package com.freemetar.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.MainViewModel
import com.freemetar.app.RouteBriefing
import com.freemetar.app.ui.flight.ConnectionSection
import com.freemetar.app.ui.flight.FlightHistorySection
import com.freemetar.app.ui.flight.LiveSection
import com.freemetar.app.data.SavedRoute
import com.freemetar.app.ui.components.CategoryTag
import com.freemetar.app.ui.components.Fact
import com.freemetar.app.ui.components.FactGrid
import com.freemetar.app.ui.components.Rule
import com.freemetar.app.ui.components.Section
import com.freemetar.app.ui.components.TrendChart
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.RawText
import java.text.DateFormat
import java.util.Date

/** Sekcja Lot: aktywny plan z pogodą na trasie, import (SimBrief, tekst, plik), zapisane trasy, symulator. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlightScreen(vm: MainViewModel, onOpen: (String) -> Unit, onShowMap: () -> Unit, onEditPlan: (String) -> Unit, onOpenFlight: (String) -> Unit) {
    val c = Fm.c
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val briefing by vm.routeBriefing.collectAsStateWithLifecycle()
    val message by vm.flightMessage.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val active = routes.firstOrNull { it.id == settings.activeRouteId }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.consumeFlightMessage() }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.exportRoutes(it) } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importRoutesFile(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("Lot", style = MaterialTheme.typography.headlineLarge, color = c.ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))

            val live by vm.flightState.collectAsStateWithLifecycle()
            live?.let { LiveSection(it, active, vm.link, com.freemetar.app.domain.AircraftProfile.fromName(settings.aircraftProfile)) { p -> vm.setAircraftProfile(p) } }
            ConnectionSection(vm)

            if (active != null) ActivePlan(active, briefing, onOpen, onShowMap, { onEditPlan(active.id) }) { vm.refreshRouteBriefing() }

            Section("Nowy plan lotu", Icons.Outlined.CloudDownload) {
                var user by rememberSaveable(settings.simbriefUser) { mutableStateOf(settings.simbriefUser) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        user, { user = it }, Modifier.weight(1f),
                        label = { Text("Użytkownik SimBrief lub Pilot ID") }, singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                    Button({ vm.importSimbrief(user) }, enabled = !importing) { Text(if (importing) "Pobieranie…" else "Pobierz") }
                }
                Text("Pobiera ostatni plan wygenerowany na simbrief.com.", style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.size(14.dp))
                Rule()
                Spacer(Modifier.size(12.dp))
                var text by rememberSaveable { mutableStateOf("") }
                var fl by rememberSaveable { mutableStateOf("") }
                OutlinedTextField(
                    text, { text = it.uppercase() }, Modifier.fillMaxWidth(),
                    label = { Text("Trasa, np. EPWA SOXER N871 BIMPA EPGD") },
                    textStyle = RawText, minLines = 2, shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                Spacer(Modifier.size(8.dp))
                FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        fl, { fl = it.filter { ch -> ch.isDigit() }.take(3) }, Modifier.width(120.dp),
                        label = { Text("Poziom FL") }, singleLine = true, shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedButton({ vm.addRouteFromText(text, fl.toIntOrNull()); text = "" }, enabled = text.split(" ").count { it.isNotBlank() } >= 2) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Zapisz trasę")
                    }
                }
                Text("Drogi lotnicze i procedury są pomijane; punkty rozpoznawane z bazy lotnisk i radiolatarni.",
                    style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 4.dp))
            }

            Section("Zapisane trasy", Icons.Outlined.Folder, trailing = {
                IconButton({ importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Icon(Icons.Outlined.FileUpload, "Importuj z pliku", tint = c.ink2) }
                IconButton({ exporter.launch("openairbook-trasy.json") }, enabled = routes.isNotEmpty()) { Icon(Icons.Outlined.FileDownload, "Eksportuj do pliku", tint = c.ink2) }
            }) {
                if (routes.isEmpty()) {
                    Text("Nie masz zapisanych tras. Pobierz plan z SimBrief, wpisz trasę albo zaimportuj plik .json.",
                        style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                }
                var toDelete by remember { mutableStateOf<SavedRoute?>(null) }
                routes.forEach { r -> RouteRow(r, r.id == active?.id, onClick = { vm.activateRoute(r.id) }, onEdit = { onEditPlan(r.id) }, onDelete = { toDelete = r }) }
                toDelete?.let { r ->
                    AlertDialog(
                        onDismissRequest = { toDelete = null },
                        title = { Text("Usunąć trasę ${r.name}?") },
                        text = { Text("Trasa zniknie z telefonu. Jeśli chcesz ją zachować, najpierw wyeksportuj trasy do pliku.") },
                        confirmButton = { TextButton({ vm.deleteRoute(r.id); toDelete = null }) { Text("Usuń", color = c.danger) } },
                        dismissButton = { TextButton({ toDelete = null }) { Text("Anuluj") } },
                    )
                }
            }

            FlightHistorySection(vm, onOpenFlight)
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(12.dp).widthIn(max = 560.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivePlan(r: SavedRoute, b: RouteBriefing?, onOpen: (String) -> Unit, onShowMap: () -> Unit, onEdit: () -> Unit, onRefresh: () -> Unit) {
    val c = Fm.c
    Section(r.name, Icons.Outlined.Route, trailing = {
        IconButton(onEdit) { Icon(Icons.Outlined.Edit, "Edytuj plan: procedury i punkty", tint = c.ink) }
        IconButton(onShowMap) { Icon(Icons.Outlined.Map, "Pokaż trasę na mapie", tint = c.ink) }
        IconButton(onRefresh) { Icon(Icons.Outlined.Refresh, "Odśwież pogodę na trasie", tint = c.ink2) }
    }) {
        val dist = r.distanceNm ?: r.measuredDistanceNm()
        FactGrid(
            listOfNotNull(
                Fact("Dystans", dist?.let { "$it NM" } ?: "—"),
                Fact("Poziom", r.cruiseFl?.let { "FL%03d".format(it) } ?: "—", accent = c.magenta),
                Fact("Czas lotu", r.eteMin?.let { "%d:%02d".format(it / 60, it % 60) } ?: "—"),
                Fact("Samolot", r.aircraft ?: "—"),
                r.fuel?.let { f -> Fact("Paliwo na trasę", f.trip?.let { "$it ${f.unit}" } ?: "—", sub = f.ramp?.let { "na starcie $it ${f.unit}" }) },
                r.fuel?.let { f -> Fact("Rezerwa", f.reserve?.let { "$it ${f.unit}" } ?: "—", sub = f.alternate?.let { "do zapasowego $it ${f.unit}" }) },
            ),
        )
        val procs = listOfNotNull(
            r.depRunway?.let { "RWY $it" }, r.departure?.let { "SID ${it.display}" },
            r.arrival?.let { "STAR ${it.display}" }, r.approach?.let { it.display } ?: r.arrRunway?.let { "lądowanie RWY $it" },
        )
        if (procs.isNotEmpty()) Text(procs.joinToString(" · "), fontFamily = Mono, fontSize = 12.sp, color = c.ink2, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.size(12.dp))
        val roles = listOf(r.origin to "Start", r.destination to "Cel") + r.alternates.map { it to "Zapasowe" }
        roles.forEach { (id, role) ->
            val m = b?.metars?.get(id)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(id) }.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(id, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.ink, modifier = Modifier.width(64.dp))
                    Text(role, style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.weight(1f))
                    CategoryTag(m?.flightCategory)
                }
                Text(m?.raw?.substringAfter(" ") ?: if (b?.loading == true) "Pobieranie METAR…" else "Brak METAR",
                    fontFamily = Mono, fontSize = 12.sp, color = if (m != null) c.accent else c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Rule()
        }
        if (b != null && b.sigmets.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.5.dp, c.caution, RoundedCornerShape(8.dp)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = c.caution, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SIGMET na trasie: ${b.sigmets.size}", style = MaterialTheme.typography.titleSmall, color = c.caution)
                }
                b.sigmets.forEach { s -> Text("${s.hazardLabel}, ${s.levels} (${s.fir.ifBlank { s.id }})", style = MaterialTheme.typography.bodySmall, color = c.ink, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        if (b != null && b.cruiseWinds.isNotEmpty() && r.cruiseFl != null) {
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Air, null, tint = c.ink2, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Wiatr na FL%03d".format(r.cruiseFl), style = MaterialTheme.typography.labelMedium, color = c.ink2)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                b.cruiseWinds.forEach { (id, w) ->
                    Column {
                        Text(id, style = MaterialTheme.typography.labelSmall, color = c.ink2)
                        Text(w?.let { "%03d/%02d %+d°".format(it.dirDeg ?: 0, it.speedKt ?: 0, it.tempC ?: 0) } ?: "—", fontFamily = Mono, fontSize = 14.sp, color = c.ink)
                    }
                }
            }
        }
        val alts = r.fullPath().mapNotNull { it.altitudeFt?.toDouble() }
        if (alts.size >= 3) {
            Spacer(Modifier.size(12.dp))
            TrendChart("Profil wysokości (ft)", alts, "", c.magenta, Modifier.fillMaxWidth())
        }
        if (r.routeText.isNotBlank()) {
            Spacer(Modifier.size(10.dp))
            Text(r.routeText, style = RawText.copy(fontSize = 12.sp, lineHeight = 18.sp), color = c.ink2)
        }
    }
}

@Composable
private fun RouteRow(r: SavedRoute, active: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = Fm.c
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (active) c.magenta else c.terrain))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, color = if (active) c.magenta else c.ink)
            Text(
                listOfNotNull(
                    if (r.source == "simbrief") "SimBrief" else "własna",
                    (r.distanceNm ?: r.measuredDistanceNm())?.let { "$it NM" },
                    r.cruiseFl?.let { "FL%03d".format(it) },
                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(r.createdAt)),
                ).joinToString(", "),
                style = MaterialTheme.typography.bodySmall, color = c.ink2,
            )
        }
        IconButton(onEdit) { Icon(Icons.Outlined.Edit, "Edytuj trasę", tint = c.ink2) }
        IconButton(onDelete) { Icon(Icons.Outlined.Delete, "Usuń trasę", tint = c.ink2) }
    }
}
