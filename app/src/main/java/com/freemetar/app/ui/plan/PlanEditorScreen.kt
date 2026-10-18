package com.freemetar.app.ui.plan

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn

import com.freemetar.app.domain.loc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.MainViewModel
import com.freemetar.app.data.ProcSelection
import com.freemetar.app.data.RoutePoint
import com.freemetar.app.data.SavedRoute
import com.freemetar.app.data.nav.AirportNav
import com.freemetar.app.data.nav.ApproachRef
import com.freemetar.app.data.nav.ProcRef
import com.freemetar.app.domain.RouteEdit
import com.freemetar.app.ui.components.Rule
import com.freemetar.app.ui.components.Section
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono

/**
 * Edytor planu lotu: pas i SID, punkty trasy, STAR, podejście z przejściem, zapasowe.
 * Procedury z bazy MSFS (zapisanej w telefonie); bez danych edycja punktów nadal działa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanEditorScreen(vm: MainViewModel, onBack: () -> Unit, onShowMap: () -> Unit) {
    val c = Fm.c
    val route by vm.editing.collectAsStateWithLifecycle()
    val navMap by vm.editNav.collectAsStateWithLifecycle()
    val busy by vm.editBusy.collectAsStateWithLifecycle()
    val r = route ?: return
    val originNav = navMap[r.origin]
    val destNav = navMap[r.destination]
    val runwaysOf = { icao: String, nav: AirportNav? ->
        nav?.airport?.runways?.map { it.ident }?.takeIf { it.isNotEmpty() }
            ?: vm.airportRunwayIdents(icao)
    }

    Column(Modifier.fillMaxSize().background(c.paper)) {
        // Stały pasek górny — nie znika przy przewijaniu
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.cancelEdit(); onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, loc("Wróć bez zapisywania", "Back without saving"), tint = c.ink) }
            Column(Modifier.weight(1f)) {
                Text("${r.origin} → ${r.destination}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.ink)
                Text(loc("Edycja planu lotu", "Flight plan editor"), style = MaterialTheme.typography.bodySmall, color = c.ink2)
            }
            IconButton({ vm.refreshEditNav(listOf(r.origin, r.destination) + r.alternates, refresh = true) }, enabled = vm.nav.connected) {
                Icon(Icons.Outlined.Refresh, loc("Pobierz procedury z MSFS ponownie", "Reload procedures from MSFS"), tint = if (vm.nav.connected) c.ink else c.ink2)
            }
            Button({ vm.saveEdit(); onBack() }, modifier = Modifier.padding(end = 8.dp)) { Text(loc("Zapisz", "Save")) }
        }
        if (busy != null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.ink, trackColor = c.terrain) else Rule()

        // Na tablecie kolumna czytelnej szerokości na środku zamiast rozciągniętych pól
        LazyColumn(Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 820.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Section(loc("Ogólne", "General"), Icons.Outlined.Tune) {
                    var name by remember(r.id) { mutableStateOf(r.name) }
                    var fl by remember(r.id) { mutableStateOf(r.cruiseFl?.toString().orEmpty()) }
                    var alts by remember(r.id) { mutableStateOf(r.alternates.joinToString(" ")) }
                    OutlinedTextField(name, { name = it; vm.editUpdate { x -> RouteEdit.rename(x, it) } }, Modifier.fillMaxWidth(),
                        label = { Text(loc("Nazwa", "Name")) }, singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.size(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(fl, { v -> fl = v.filter(Char::isDigit).take(3); vm.editUpdate { x -> RouteEdit.setCruise(x, fl.toIntOrNull()) } },
                            Modifier.width(120.dp), label = { Text(loc("Poziom FL", "Level FL")) }, singleLine = true, shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(alts, { v -> alts = v.uppercase(); vm.editUpdate { x -> RouteEdit.setAlternates(x, alts.split(" ", ",")) } },
                            Modifier.width(200.dp), label = { Text(loc("Zapasowe (ICAO)", "Alternates (ICAO)")) }, singleLine = true, shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                    }
                    val dist = r.measuredDistanceNm()
                    Text(listOfNotNull(dist?.let { loc("$it NM po trasie", "$it NM along the route") }, loc("${r.fullPath().size} punktów", "${r.fullPath().size} waypoints")).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 8.dp))
                    if (originNav?.procedures == null || destNav?.procedures == null) {
                        Text(
                            if (vm.nav.connected) loc("Pobieranie procedur z MSFS… Jeśli lista zostanie pusta, baza MSFS nie ma procedur dla tego lotniska.", "Loading procedures from MSFS… If the list stays empty, the MSFS database has no procedures for this airport.")
                            else loc("Procedury (SID, STAR, podejścia) pochodzą z bazy MSFS 2024. Połącz się z OpenTakeoff Link — po pobraniu zostaną w telefonie.", "Procedures (SID, STAR, approaches) come from the MSFS 2024 database. Connect to OpenTakeoff Link — once loaded they stay on the phone."),
                            style = MaterialTheme.typography.bodySmall, color = c.caution, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onShowMap) { Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(loc("Pokaż na mapie", "Show on map")) }
                }
            }

            item {
                Section(loc("Odlot ${r.origin}", "Departure ${r.origin}"), Icons.Outlined.FlightTakeoff) {
                    RunwayPicker(runwaysOf(r.origin, originNav), r.depRunway) { rw ->
                        vm.chooseProcedure("departure", r.origin, null, rw, null, null)
                    }
                    val sids = originNav?.procedures?.departures.orEmpty().filter { r.depRunway == null || it.runways.isEmpty() || r.depRunway in it.runways }
                    ProcPicker("SID", sids, r.departure, noneLabel = loc("Bez SID (wektory)", "No SID (vectors)"),
                        onPick = { p -> vm.chooseProcedure("departure", r.origin, p?.name, r.depRunway, null, p?.name) },
                        onTransition = { t -> r.departure?.let { d -> vm.chooseProcedure("departure", r.origin, d.name, r.depRunway, t, d.label) } })
                    ProcPoints(r.departure)
                }
            }

            item {
                Section(loc("Trasa", "Route"), Icons.Outlined.Route) {
                    EnrouteEditor(r, onAdd = { ident, index -> vm.addFix(ident, index) }, onEdit = vm::editUpdate)
                }
            }

            item {
                Section(loc("Przylot ${r.destination}", "Arrival ${r.destination}"), Icons.Outlined.FlightLand) {
                    val stars = destNav?.procedures?.arrivals.orEmpty().filter { r.arrRunway == null || it.runways.isEmpty() || r.arrRunway in it.runways }
                    ProcPicker("STAR", stars, r.arrival, noneLabel = loc("Bez STAR", "No STAR"),
                        onPick = { p -> vm.chooseProcedure("arrival", r.destination, p?.name, r.arrRunway, null, p?.name) },
                        onTransition = { t -> r.arrival?.let { a -> vm.chooseProcedure("arrival", r.destination, a.name, r.arrRunway, t, a.label) } })
                    ProcPoints(r.arrival)
                    Spacer(Modifier.size(10.dp))
                    Rule()
                    Spacer(Modifier.size(10.dp))
                    ApproachPicker(r, destNav, runwaysOf(r.destination, destNav), vm)
                    ProcPoints(r.approach)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunwayPicker(runways: List<String>, selected: String?, onPick: (String?) -> Unit) {
    val c = Fm.c
    Text(loc("Pas startowy", "Departure runway"), style = MaterialTheme.typography.labelMedium, color = c.ink2)
    if (runways.isEmpty()) Text(loc("Brak danych o pasach.", "No runway data."), style = MaterialTheme.typography.bodySmall, color = c.ink2)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        runways.forEach { rw -> FilterChip(selected == rw, { onPick(if (selected == rw) null else rw) }, { Text(rw, fontFamily = Mono) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProcPicker(
    title: String, items: List<ProcRef>, current: ProcSelection?, noneLabel: String,
    onPick: (ProcRef?) -> Unit, onTransition: (String?) -> Unit,
) {
    val c = Fm.c
    Spacer(Modifier.size(10.dp))
    Text(title, style = MaterialTheme.typography.labelMedium, color = c.ink2)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(current == null, { onPick(null) }, { Text(noneLabel) })
        items.forEach { p -> FilterChip(current?.name == p.name, { onPick(p) }, { Text(p.name, fontFamily = Mono) }) }
    }
    val sel = items.firstOrNull { it.name == current?.name }
    if (sel != null && sel.transitions.isNotEmpty()) {
        Text(loc("Przejście", "Transition"), style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(current?.transition == null, { onTransition(null) }, { Text(loc("Bez przejścia", "No transition")) })
            sel.transitions.forEach { t -> FilterChip(current?.transition == t, { onTransition(t) }, { Text(t, fontFamily = Mono) }) }
        }
    }
}

private val approachTypes = listOf("ILS", "LOC", "RNAV", "GPS", "VOR", "NDB")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApproachPicker(r: SavedRoute, nav: AirportNav?, runways: List<String>, vm: MainViewModel) {
    val c = Fm.c
    val all = nav?.procedures?.approaches.orEmpty()
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    Text(loc("Podejście", "Approach"), style = MaterialTheme.typography.titleSmall, color = c.ink)
    Text(loc("Pas lądowania", "Landing runway"), style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        runways.forEach { rw ->
            FilterChip(r.arrRunway == rw, { vm.editUpdate { RouteEdit.setApproach(it, rw, if (it.approach?.runway == rw) it.approach else null) } }, { Text(rw, fontFamily = Mono) })
        }
    }
    val types = approachTypes.filter { t -> all.any { it.type == t } }
    if (types.size > 1) {
        Text(loc("Rodzaj", "Type"), style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(type == null, { type = null }, { Text(loc("Wszystkie", "All")) })
            types.forEach { t -> FilterChip(type == t, { type = t }, { Text(t) }) }
        }
    }
    val list = all.filter { (type == null || it.type == type) && (r.arrRunway == null || it.runway == r.arrRunway) }
    Text(loc("Procedura", "Procedure"), style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
    if (all.isEmpty()) Text(loc("Brak podejść w pamięci telefonu.", "No approaches stored on the phone."), style = MaterialTheme.typography.bodySmall, color = c.ink2)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(r.approach == null, { vm.chooseProcedure("approach", r.destination, null, r.arrRunway, null, null) }, { Text(loc("Z widocznością", "Visual")) })
        list.forEach { a: ApproachRef ->
            FilterChip(r.approach?.name == a.name, { vm.chooseProcedure("approach", r.destination, a.name, a.runway, null, a.label) }, { Text(a.label) })
        }
    }
    val sel = all.firstOrNull { it.name == r.approach?.name }
    if (sel != null && sel.transitions.isNotEmpty()) {
        Text(loc("Przejście", "Transition"), style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(r.approach?.transition == null, { vm.chooseProcedure("approach", r.destination, sel.name, sel.runway, null, sel.label) }, { Text(loc("Wektory", "Vectors")) })
            sel.transitions.forEach { t -> FilterChip(r.approach?.transition == t, { vm.chooseProcedure("approach", r.destination, sel.name, sel.runway, t, sel.label) }, { Text(t, fontFamily = Mono) }) }
        }
    }
    // ILS wybranego pasa — do ustawienia w NAV1 przed podejściem
    r.arrRunway?.let { rw -> nav?.ilsFor(rw)?.freqMhz?.let { f ->
        Text("ILS RWY $rw: %.2f".format(java.util.Locale.US, f) + (nav.ilsFor(rw)?.locCourseDeg?.let { loc(", kurs %03d°", ", course %03d°").format(it.toInt()) } ?: ""),
            fontFamily = Mono, fontSize = 13.sp, color = c.accent, modifier = Modifier.padding(top = 8.dp))
    } }
}

/** Lista punktów wybranej procedury z ograniczeniami wysokości. */
@Composable
private fun ProcPoints(p: ProcSelection?) {
    val c = Fm.c
    if (p == null || p.points.isEmpty()) return
    Spacer(Modifier.size(6.dp))
    Text(p.points.joinToString("  ") { pt -> pt.ident + (pt.constraint?.let { " ($it)" } ?: "") },
        fontFamily = Mono, fontSize = 12.sp, color = c.ink2, lineHeight = 18.sp)
}

@Composable
private fun EnrouteEditor(r: SavedRoute, onAdd: (String, Int) -> Unit, onEdit: ((SavedRoute) -> SavedRoute) -> Unit) {
    val c = Fm.c
    val e = r.enroute
    if (e.isEmpty()) Text(loc("Trasa bez punktów pośrednich — lot bezpośredni.", "No en-route waypoints — direct flight."), style = MaterialTheme.typography.bodySmall, color = c.ink2)
    e.forEachIndexed { i, p -> PointRow(i, p, last = i == e.lastIndex, onEdit = onEdit) }
    Spacer(Modifier.size(10.dp))
    var ident by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            ident, { ident = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6) }, Modifier.weight(1f),
            label = { Text(loc("Dodaj punkt (np. SOXER, WAR)", "Add waypoint (e.g. SOXER, WAR)")) }, singleLine = true, shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (ident.isNotEmpty()) { onAdd(ident, e.size); ident = "" } }),
        )
        Button({ onAdd(ident, e.size); ident = "" }, enabled = ident.length >= 2) { Text(loc("Dodaj", "Add")) }
    }
}

@Composable
private fun PointRow(index: Int, p: RoutePoint, last: Boolean, onEdit: ((SavedRoute) -> SavedRoute) -> Unit) {
    val c = Fm.c
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${index + 1}", fontFamily = Mono, fontSize = 11.sp, color = c.ink2, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(p.ident, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (p.hasPosition) c.ink else c.caution)
            Text(
                listOfNotNull(p.airway?.let { loc("przez $it", "via $it") }, p.type.takeIf { it != "fix" }, if (!p.hasPosition) loc("brak pozycji", "no position") else null).joinToString(", "),
                style = MaterialTheme.typography.bodySmall, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton({ onEdit { RouteEdit.move(it, index, index - 1) } }, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, loc("Przesuń wyżej", "Move up"), tint = c.ink2) }
        IconButton({ onEdit { RouteEdit.move(it, index, index + 1) } }, enabled = !last) { Icon(Icons.Outlined.ArrowDownward, loc("Przesuń niżej", "Move down"), tint = c.ink2) }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, loc("Więcej", "More"), tint = c.ink2) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Direct to ${p.ident}") }, onClick = { onEdit { RouteEdit.directTo(it, index) }; menu = false })
                DropdownMenuItem(text = { Text(loc("Usuń", "Delete"), color = c.danger) }, leadingIcon = { Icon(Icons.Outlined.Close, null, tint = c.danger) },
                    onClick = { onEdit { RouteEdit.remove(it, index) }; menu = false })
            }
        }
    }
}
