package com.freemetar.app.ui.screens

import androidx.compose.foundation.layout.wrapContentWidth

import com.freemetar.app.domain.loc

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
import com.freemetar.app.ui.flight.CockpitView
import com.freemetar.app.domain.AircraftProfile
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
fun FlightScreen(vm: MainViewModel, onOpen: (String) -> Unit, onShowMap: () -> Unit, onEditPlan: (String) -> Unit, onOpenFlight: (String) -> Unit, onOpenLogbook: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
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

    val live by vm.flightState.collectAsStateWithLifecycle()
    // Po połączeniu z symulatorem domyślnie kokpit (PFD + panele) na cały ekran; plan i historia pod przełącznikiem.
    var cockpit by rememberSaveable { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        val state = live
        if (state != null && cockpit) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                CockpitView(vm.link, state, active, AircraftProfile.fromName(settings.aircraftProfile), { p -> vm.setAircraftProfile(p) },
                    settings.controlMode, { vm.setControlMode(it) }, settings.floatingControl, { vm.setFloatingControl(it) },
                    vm.nearbyNav.collectAsStateWithLifecycle().value, floatingTrim = settings.floatingStyle == "TRIM",
                    onShowPlan = { cockpit = false })
            }
        } else Column(
            Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 820.dp)
                .statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            if (state != null) ModeSwitch(cockpit) { cockpit = it }
            else Text(loc("Lot", "Flight"), style = MaterialTheme.typography.headlineLarge, color = c.ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))

            ConnectionSection(vm)

            if (active != null) ActivePlan(active, briefing, onOpen, onShowMap, { onEditPlan(active.id) }) { vm.refreshRouteBriefing() }

            Section(loc("Nowy plan lotu", "New flight plan"), Icons.Outlined.CloudDownload) {
                // SimBrief: login zapisany na stałe w Ustawieniach — tu tylko pobranie ostatniego planu
                if (settings.simbriefUser.isNotBlank()) {
                    Button({ vm.importSimbrief(settings.simbriefUser) }, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (importing) loc("Pobieranie…", "Loading…") else loc("Pobierz ostatni plan z SimBrief", "Download latest SimBrief plan"))
                    }
                    Text(loc("Konto ${settings.simbriefUser}. Zmienisz je w Ustawieniach.", "Account ${settings.simbriefUser}. Change it in Settings."),
                        style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 4.dp))
                } else {
                    OutlinedButton(onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(loc("Ustaw konto SimBrief w Ustawieniach", "Set your SimBrief account in Settings")) }
                }

                Spacer(Modifier.size(10.dp))
                OutlinedButton({ vm.startMapPlanning(); onShowMap() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(loc("Zaplanuj na mapie", "Plan on the map"))
                }

                Spacer(Modifier.size(14.dp))
                Rule()
                Spacer(Modifier.size(12.dp))
                var text by rememberSaveable { mutableStateOf("") }
                var fl by rememberSaveable { mutableStateOf("") }
                OutlinedTextField(
                    text, { text = it.uppercase() }, Modifier.fillMaxWidth(),
                    label = { Text(loc("Albo wpisz trasę: lotniska i punkty, np. EPWA SOXER EPGD", "Or type a route: airports and waypoints, e.g. EPWA SOXER EPGD")) },
                    textStyle = RawText, minLines = 2, shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                Spacer(Modifier.size(8.dp))
                FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        fl, { fl = it.filter { ch -> ch.isDigit() }.take(3) }, Modifier.width(120.dp),
                        label = { Text(loc("Poziom FL", "Level FL")) }, singleLine = true, shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedButton({ vm.addRouteFromText(text, fl.toIntOrNull()); text = "" }, enabled = text.split(" ").count { it.isNotBlank() } >= 2) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(loc("Zapisz trasę", "Save route"))
                    }
                }
                Text(loc("Drogi lotnicze i procedury są pomijane; punkty rozpoznawane z bazy lotnisk i radiolatarni.", "Airways and procedures are skipped; waypoints are matched against the airport and navaid database."),
                    style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 4.dp))
            }

            Section(loc("Zapisane trasy", "Saved routes"), Icons.Outlined.Folder, trailing = {
                IconButton({ importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Icon(Icons.Outlined.FileUpload, loc("Importuj z pliku", "Import from file"), tint = c.ink2) }
                IconButton({ exporter.launch(loc("openairbook-trasy.json", "openairbook-routes.json")) }, enabled = routes.isNotEmpty()) { Icon(Icons.Outlined.FileDownload, loc("Eksportuj do pliku", "Export to file"), tint = c.ink2) }
            }) {
                if (routes.isEmpty()) {
                    Text(loc("Nie masz zapisanych tras. Pobierz plan z SimBrief, wpisz trasę albo zaimportuj plik .json.", "No saved routes yet. Load a plan from SimBrief, type a route or import a .json file."),
                        style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                }
                var toDelete by remember { mutableStateOf<SavedRoute?>(null) }
                routes.forEach { r -> RouteRow(r, r.id == active?.id, onClick = { vm.activateRoute(r.id) }, onEdit = { onEditPlan(r.id) }, onDelete = { toDelete = r }) }
                toDelete?.let { r ->
                    AlertDialog(
                        onDismissRequest = { toDelete = null },
                        title = { Text(loc("Usunąć trasę ${r.name}?", "Delete route ${r.name}?")) },
                        text = { Text(loc("Trasa zniknie z telefonu. Jeśli chcesz ją zachować, najpierw wyeksportuj trasy do pliku.", "The route will be removed from the phone. To keep it, export your routes to a file first.")) },
                        confirmButton = { TextButton({ vm.deleteRoute(r.id); toDelete = null }) { Text(loc("Usuń", "Delete"), color = c.danger) } },
                        dismissButton = { TextButton({ toDelete = null }) { Text(loc("Anuluj", "Cancel")) } },
                    )
                }
            }

            com.freemetar.app.ui.flight.LogbookEntry(vm, onOpenLogbook)
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(12.dp).widthIn(max = 560.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivePlan(r: SavedRoute, b: RouteBriefing?, onOpen: (String) -> Unit, onShowMap: () -> Unit, onEdit: () -> Unit, onRefresh: () -> Unit) {
    val c = Fm.c
    Section(r.name, Icons.Outlined.Route, trailing = {
        IconButton(onEdit) { Icon(Icons.Outlined.Edit, loc("Edytuj plan: procedury i punkty", "Edit plan: procedures and waypoints"), tint = c.ink) }
        IconButton(onShowMap) { Icon(Icons.Outlined.Map, loc("Pokaż trasę na mapie", "Show route on map"), tint = c.ink) }
        IconButton(onRefresh) { Icon(Icons.Outlined.Refresh, loc("Odśwież pogodę na trasie", "Refresh en-route weather"), tint = c.ink2) }
    }) {
        val dist = r.distanceNm ?: r.measuredDistanceNm()
        FactGrid(
            listOfNotNull(
                Fact(loc("Dystans", "Distance"), dist?.let { "$it NM" } ?: "—"),
                Fact(loc("Poziom", "Level"), r.cruiseFl?.let { "FL%03d".format(it) } ?: "—", accent = c.magenta),
                Fact(loc("Czas lotu", "Flight time"), r.eteMin?.let { "%d:%02d".format(it / 60, it % 60) } ?: "—"),
                Fact(loc("Samolot", "Aircraft"), r.aircraft ?: "—"),
                r.fuel?.let { f -> Fact(loc("Paliwo na trasę", "Trip fuel"), f.trip?.let { "$it ${f.unit}" } ?: "—", sub = f.ramp?.let { loc("na starcie $it ${f.unit}", "ramp $it ${f.unit}") }) },
                r.fuel?.let { f -> Fact("Rezerwa", f.reserve?.let { "$it ${f.unit}" } ?: "—", sub = f.alternate?.let { loc("do zapasowego $it ${f.unit}", "to alternate $it ${f.unit}") }) },
            ),
        )
        val procs = listOfNotNull(
            r.depRunway?.let { "RWY $it" }, r.departure?.let { "SID ${it.display}" },
            r.arrival?.let { "STAR ${it.display}" }, r.approach?.let { it.display } ?: r.arrRunway?.let { loc("lądowanie RWY $it", "landing RWY $it") },
        )
        if (procs.isNotEmpty()) Text(procs.joinToString(" · "), fontFamily = Mono, fontSize = 12.sp, color = c.ink2, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.size(12.dp))
        val roles = listOf(r.origin to "Start", r.destination to loc("Cel", "Destination")) + r.alternates.map { it to loc("Zapasowe", "Alternate") }
        roles.forEach { (id, role) ->
            val m = b?.metars?.get(id)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(id) }.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(id, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.ink, modifier = Modifier.width(64.dp))
                    Text(role, style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.weight(1f))
                    CategoryTag(m?.flightCategory)
                }
                Text(m?.raw?.substringAfter(" ") ?: if (b?.loading == true) loc("Pobieranie METAR…", "Loading METAR…") else loc("Brak METAR", "No METAR"),
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
                    Text(loc("SIGMET na trasie: ${b.sigmets.size}", "SIGMETs on route: ${b.sigmets.size}"), style = MaterialTheme.typography.titleSmall, color = c.caution)
                }
                b.sigmets.forEach { s -> Text("${s.hazardLabel}, ${s.levels} (${s.fir.ifBlank { s.id }})", style = MaterialTheme.typography.bodySmall, color = c.ink, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        if (b != null && b.cruiseWinds.isNotEmpty() && r.cruiseFl != null) {
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Air, null, tint = c.ink2, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(loc("Wiatr na FL%03d", "Wind at FL%03d").format(r.cruiseFl), style = MaterialTheme.typography.labelMedium, color = c.ink2)
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
            TrendChart(loc("Profil wysokości (ft)", "Altitude profile (ft)"), alts, "", c.magenta, Modifier.fillMaxWidth())
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
                    if (r.source == "simbrief") "SimBrief" else loc("własna", "custom"),
                    (r.distanceNm ?: r.measuredDistanceNm())?.let { "$it NM" },
                    r.cruiseFl?.let { "FL%03d".format(it) },
                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(r.createdAt)),
                ).joinToString(", "),
                style = MaterialTheme.typography.bodySmall, color = c.ink2,
            )
        }
        IconButton(onEdit) { Icon(Icons.Outlined.Edit, loc("Edytuj trasę", "Edit route"), tint = c.ink2) }
        IconButton(onDelete) { Icon(Icons.Outlined.Delete, loc("Usuń trasę", "Delete route"), tint = c.ink2) }
    }
}

/** Przełącznik Kokpit / Plan i loty — aktywny segment to blok w kolorze tekstu (biały na AMOLED). */
@Composable
private fun ModeSwitch(cockpit: Boolean, onChange: (Boolean) -> Unit) {
    val c = Fm.c
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 4.dp).clip(RoundedCornerShape(50)).border(1.dp, c.rule, RoundedCornerShape(50)).padding(2.dp),
    ) {
        listOf(true to loc("Kokpit", "Cockpit"), false to loc("Plan i loty", "Plan & flights")).forEach { (v, label) ->
            val on = v == cockpit
            Text(
                label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (on) c.paper else c.ink2,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.ink else c.paper)
                    .clickable { onChange(v) }.padding(horizontal = 14.dp, vertical = 5.dp),
            )
        }
    }
}
