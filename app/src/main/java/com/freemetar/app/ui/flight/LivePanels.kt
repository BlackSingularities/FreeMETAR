package com.freemetar.app.ui.flight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.MainViewModel
import com.freemetar.app.data.SavedRoute
import com.freemetar.app.data.link.FlightState
import com.freemetar.app.data.link.LinkClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.freemetar.app.data.link.LinkStatus
import com.freemetar.app.domain.Geo
import com.freemetar.app.ui.components.Fact
import com.freemetar.app.ui.components.FactGrid
import com.freemetar.app.ui.components.Rule
import com.freemetar.app.ui.components.Section
import com.freemetar.app.ui.components.isWide
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.roundToInt

/** Połączenie z OpenAirBook Link: znalezione komputery, parowanie kodem, ręczne IP. */
@Composable
fun ConnectionSection(vm: MainViewModel) {
    val c = Fm.c
    val status by vm.linkStatus.collectAsStateWithLifecycle()
    val servers by vm.linkServers.collectAsStateWithLifecycle()
    val err by vm.link.lastError.collectAsStateWithLifecycle()
    // Nasłuch w sieci tylko, gdy ten panel jest na ekranie
    DisposableEffect(Unit) { vm.startLinkDiscovery(); onDispose { vm.stopLinkDiscovery() } }

    Section("Symulator", Icons.Outlined.Computer, trailing = {
        if (status is LinkStatus.Connected || status is LinkStatus.Lost) IconButton({ vm.linkDisconnect() }) { Icon(Icons.Outlined.LinkOff, "Rozłącz", tint = c.ink2) }
    }) {
        val (dot, text) = when (val s = status) {
            LinkStatus.Idle -> c.ink2 to "Nie połączono"
            is LinkStatus.Connecting -> c.caution to "Łączenie z ${s.ip}…"
            is LinkStatus.NeedsCode -> c.caution to "Wpisz kod parowania z programu"
            is LinkStatus.Connected -> (if (s.source == "none") c.caution else c.green) to when (s.source) {
                "msfs" -> "Połączono z MSFS"
                "simulation" -> "Połączono — tryb symulacji"
                else -> s.message ?: "Połączono, czekam na MSFS"
            }
            is LinkStatus.Lost -> c.danger to "Utracono połączenie, ponawiam… (${s.reason})"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
            if (status is LinkStatus.Connecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.accent)
        }
        err?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.caution, modifier = Modifier.padding(top = 4.dp)) }

        if (status is LinkStatus.Idle || status is LinkStatus.NeedsCode) {
            Spacer(Modifier.size(10.dp))
            if (servers.isEmpty()) {
                Text(
                    "Szukam OpenAirBook Link w sieci… Uruchom program na komputerze z MSFS 2024; telefon musi być w tej samej sieci Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium, color = c.ink2,
                )
            }
            servers.forEach { sv ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sv.host, style = MaterialTheme.typography.titleSmall, color = c.ink)
                        Text("${sv.ip} · ${when (sv.source) { "msfs" -> "MSFS połączony"; "simulation" -> "symulacja"; else -> "czeka na MSFS" }}",
                            style = MaterialTheme.typography.bodySmall, color = c.ink2)
                    }
                    Button({ vm.linkConnect(sv.ip, sv.port) }) { Text("Połącz") }
                }
            }
            var manual by rememberSaveable { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(manual, { manual = it.trim() }, Modifier.weight(1f), label = { Text("Adres IP komputera") }, singleLine = true,
                    shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedButton({ vm.linkConnect(manual) }, enabled = manual.count { it == '.' } == 3) { Text("Połącz") }
            }
        }
    }

    (status as? LinkStatus.NeedsCode)?.let { s ->
        var code by remember(s) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.linkDisconnect() },
            title = { Text("Parowanie z ${s.ip}") },
            text = {
                Column {
                    Text("Wpisz 6-cyfrowy kod widoczny w oknie OpenAirBook Link. Telefon zapamięta parowanie.", style = MaterialTheme.typography.bodyMedium)
                    s.error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.padding(top = 10.dp), singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = Mono), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                }
            },
            confirmButton = { TextButton({ vm.linkConnect(s.ip, s.port, code) }, enabled = code.length == 6) { Text("Sparuj") } },
            dismissButton = { TextButton({ vm.linkDisconnect() }) { Text("Anuluj") } },
        )
    }
}

private enum class LiveView(val label: String, val instrument: Boolean) {
    PFD("PFD", true), ND("Mapa", true), AP("Autopilot", false), RADIO("Radio", false),
    CONTROLS("Systemy", false), YOKE("Wolant", false), ENGINES("Silniki", false),
}

/** Widoki na żywo: PFD, mapa nawigacyjna, silniki i paliwo, postęp lotu. Na tablecie PFD i mapa obok siebie. */
@Composable
fun LiveSection(s: FlightState, route: SavedRoute?, link: LinkClient) {
    val c = Fm.c
    var view by rememberSaveable { mutableStateOf(LiveView.PFD) }
    var panel by rememberSaveable { mutableStateOf(LiveView.AP) }
    var range by rememberSaveable { mutableStateOf(40) }
    val wide = isWide()
    Section(s.aircraft.title ?: "Lot na żywo", Icons.Outlined.Speed) {
        if (s.aircraft.paused == true) Text("Symulator wstrzymany", style = MaterialTheme.typography.labelMedium, color = c.caution, modifier = Modifier.padding(bottom = 6.dp))
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1.1f).clip(RoundedCornerShape(8.dp))) { Pfd(s) }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))) { NavDisplay(s, route?.points.orEmpty(), range) }
            }
            RangeRow(range) { range = it }
            Spacer(Modifier.size(12.dp))
            Progress(s, route)
            Spacer(Modifier.size(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveView.entries.filterNot { it.instrument }.forEach { v -> FilterChip(panel == v, { panel = v }, { Text(v.label) }) }
            }
            Spacer(Modifier.size(10.dp))
            PanelFor(panel, s, link)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveView.entries.forEach { v -> FilterChip(view == v, { view = v }, { Text(v.label) }) }
            }
            when (view) {
                LiveView.PFD -> Box(Modifier.clip(RoundedCornerShape(8.dp))) { Pfd(s) }
                LiveView.ND -> {
                    Box(Modifier.clip(RoundedCornerShape(8.dp))) { NavDisplay(s, route?.points.orEmpty(), range) }
                    RangeRow(range) { range = it }
                }
                else -> PanelFor(view, s, link)
            }
            // Postęp lotu pod przyrządami; przy panelach sterowania nie zabiera miejsca
            if (view.instrument) { Spacer(Modifier.size(12.dp)); Progress(s, route) }
        }
    }
}

@Composable
private fun PanelFor(v: LiveView, s: FlightState, link: LinkClient) {
    when (v) {
        LiveView.AP -> AutopilotPanel(link, s)
        LiveView.RADIO -> RadioPanel(link, s)
        LiveView.CONTROLS -> SystemsPanel(link, s)
        LiveView.YOKE -> YokePanel(link, s)
        LiveView.ENGINES -> Systems(s)
        else -> {}
    }
}

@Composable
private fun RangeRow(range: Int, onRange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        listOf(10, 20, 40, 80, 160).forEach { r -> FilterChip(range == r, { onRange(r) }, { Text("$r NM") }) }
    }
}

/** Postęp: do celu, czas, ETA, początek zniżania (3°), paliwo i jego starczalność. */
@Composable
private fun Progress(s: FlightState, route: SavedRoute?) {
    val c = Fm.c
    val lat = s.position.lat; val lon = s.position.lon
    val dest = route?.points?.lastOrNull { it.hasPosition }
    val gs = s.speed.gsKt ?: 0.0
    val toDest = if (lat != null && lon != null && dest != null) Geo.distanceNm(lat, lon, dest.lat!!, dest.lon!!) else null
    val eteMin = if (toDest != null && gs > 50) toDest / gs * 60 else null
    val eta = eteMin?.let {
        Instant.now().plusSeconds((it * 60).toLong()).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HH:mm")) + "Z"
    }
    val alt = s.position.altMslFt ?: 0.0
    val todNm = ((alt - (dest?.altitudeFt ?: 0)) / 1000.0 * 3).coerceAtLeast(0.0)
    val fuel = s.fuel.totalKg
    val ff = s.engines.mapNotNull { it.fuelFlowKgH }.sum()
    val endurance = if (fuel != null && ff > 1) fuel / ff * 60 else null
    FactGrid(
        listOfNotNull(
            Fact("Do ${dest?.ident ?: "celu"}", toDest?.let { "${it.roundToInt()} NM" } ?: "—", accent = c.magenta, sub = if (dest == null) "wybierz trasę" else null),
            Fact("Przylot", eta ?: "—", sub = eteMin?.let { "za %d:%02d".format(it.toInt() / 60, it.toInt() % 60) }),
            Fact("Początek zniżania", if (toDest == null || s.aircraft.onGround == true) "—" else if (toDest <= todNm) "teraz" else "za ${(toDest - todNm).roundToInt()} NM",
                accent = if (toDest != null && toDest - todNm < 10 && s.aircraft.onGround != true) c.caution else null, sub = "${todNm.roundToInt()} NM przed celem"),
            Fact("V/S", "${(s.speed.vsFpm ?: 0.0).roundToInt()} ft/min", sub = "GS ${gs.roundToInt()} kt"),
            fuel?.let { Fact("Paliwo", "${it.roundToInt()} kg", sub = endurance?.let { e -> "starczy na %d:%02d".format(e.toInt() / 60, e.toInt() % 60) }) },
            Fact("Wiatr", s.environment.windDirDeg?.let { d -> "%03d/%d".format(d.roundToInt(), (s.environment.windSpdKt ?: 0.0).roundToInt()) } ?: "—",
                sub = s.environment.oatC?.let { "OAT ${it.roundToInt()} °C" }),
        ),
    )
}

@Composable
private fun Systems(s: FlightState) {
    val c = Fm.c
    s.engines.forEachIndexed { i, e ->
        val turbine = (e.n1Pct ?: 0.0) > 0.1
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SIL ${i + 1}", fontFamily = Mono, fontSize = 12.sp, color = c.ink2, modifier = Modifier.width(52.dp))
            val pct = if (turbine) (e.n1Pct ?: 0.0) / 100 else ((e.rpm ?: 0.0) / 2700).coerceIn(0.0, 1.0)
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.terrain)) {
                Box(Modifier.fillMaxWidth(pct.toFloat().coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.green))
            }
            Spacer(Modifier.width(10.dp))
            Text(if (turbine) "N1 ${"%.1f".format(e.n1Pct)}" else "${(e.rpm ?: 0.0).roundToInt()} RPM", fontFamily = Mono, fontSize = 13.sp, color = c.ink, modifier = Modifier.width(96.dp))
        }
        Text(
            listOfNotNull(e.egtC?.let { "EGT ${it.roundToInt()} °C" }, e.fuelFlowKgH?.let { "FF ${it.roundToInt()} kg/h" }, e.n2Pct?.takeIf { turbine }?.let { "N2 ${"%.1f".format(it)}" }).joinToString("   "),
            fontFamily = Mono, fontSize = 12.sp, color = c.ink2, modifier = Modifier.padding(start = 52.dp),
        )
    }
    if (s.fuel.tanks.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Outlined.LocalGasStation, null, tint = c.ink2, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                s.fuel.tanks.entries.joinToString("   ") { (k, v) ->
                    val name = when (k) { "leftKg" -> "L"; "centerKg" -> "C"; "rightKg" -> "P"; else -> k.removeSuffix("Kg") }
                    "$name ${v.roundToInt()}"
                } + " kg",
                fontFamily = Mono, fontSize = 13.sp, color = c.ink,
            )
        }
    }
    val ctl = s.controls
    Text(
        listOfNotNull(
            ctl.gearDown?.let { if (it) "Podwozie wypuszczone" else "Podwozie schowane" },
            ctl.flapsIndex?.let { "klapy $it/${(ctl.flapsPositions ?: 1) - 1}" },
            ctl.parkingBrake?.takeIf { it }?.let { "hamulec postojowy" },
            s.radios.transponder?.code?.let { "squawk $it" },
        ).joinToString(", "),
        style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 8.dp),
    )
}

/** Historia lotów nagranych automatycznie z symulatora. */
@Composable
fun FlightHistorySection(vm: MainViewModel) {
    val c = Fm.c
    val logs by vm.flightLogs.collectAsStateWithLifecycle()
    if (logs.isEmpty()) return
    Section("Historia lotów", Icons.Outlined.History) {
        logs.take(30).forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${f.from ?: "?"} → ${f.to ?: "?"}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                    Text(
                        listOfNotNull(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(f.start)),
                            "%d:%02d".format(f.durationMin / 60, f.durationMin % 60),
                            "${f.distanceNm} NM", "FL%03d".format(f.maxAltFt / 100), f.aircraft,
                        ).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = c.ink2,
                    )
                }
                IconButton({ vm.deleteFlightLog(f.id) }) { Icon(Icons.Outlined.Delete, "Usuń lot z historii", tint = c.ink2) }
            }
            Rule()
        }
    }
}
