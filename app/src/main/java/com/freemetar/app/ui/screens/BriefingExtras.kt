package com.freemetar.app.ui.screens

import com.freemetar.app.domain.loc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.AirportDetail
import com.freemetar.app.data.AltUnit
import com.freemetar.app.data.Settings
import com.freemetar.app.data.Sigmet
import com.freemetar.app.domain.Atmo
import com.freemetar.app.domain.fmt
import com.freemetar.app.ui.components.Rule
import com.freemetar.app.ui.components.Section
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.RawText
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val hhmmZ = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)

private fun sigmetIcon(s: Sigmet): ImageVector = when (s.hazard.uppercase()) {
    "TS", "CONVECTIVE", "CONV", "TC" -> Icons.Outlined.Thunderstorm
    "ICE" -> Icons.Outlined.AcUnit
    "TURB", "MTW" -> Icons.Outlined.Air
    else -> Icons.Outlined.WarningAmber
}

/** SIGMET nad lotniskiem i w pobliżu oraz raporty pilotów. */
@Composable
fun HazardsPart(d: AirportDetail) {
    val c = Fm.c
    val any = d.sigmets.isNotEmpty() || d.pireps.any { it.reportsHazard }
    Section(loc("Zagrożenia", "Hazards"), Icons.Outlined.WarningAmber, frame = if (d.sigmets.any { it.second == 0.0 }) c.caution else null) {
        if (!any && d.pireps.isEmpty()) {
            Text(
                if (d.loading) loc("Sprawdzanie SIGMET i raportów pilotów…", "Checking SIGMETs and pilot reports…") else loc("Brak ważnych SIGMET w promieniu 50 NM i raportów pilotów w okolicy.", "No valid SIGMETs within 50 NM and no pilot reports nearby."),
                style = MaterialTheme.typography.bodyMedium, color = c.ink2,
            )
            return@Section
        }
        d.sigmets.forEach { (s, dist) -> SigmetRow(s, dist) }
        if (d.pireps.isNotEmpty()) {
            if (d.sigmets.isNotEmpty()) Spacer(Modifier.size(8.dp))
            Text(loc("Raporty pilotów (3 h, 100 NM)", "Pilot reports (3 h, 100 NM)"), style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.padding(bottom = 4.dp))
            d.pireps.take(8).forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.RecordVoiceOver, null, tint = if (p.urgent) c.danger else c.ink2, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOfNotNull(p.flightLevel?.let { "FL%03d".format(it) }, p.aircraft, hhmmZ.format(Instant.ofEpochSecond(p.obsTime)) + "Z").joinToString("  "),
                            fontFamily = Mono, fontSize = 13.sp, color = c.ink,
                        )
                        Text(p.summary, style = MaterialTheme.typography.bodySmall,
                            color = when {
                                p.urgent || p.turbulence?.contains("SEV") == true || p.icing?.contains("SEV") == true -> c.danger
                                p.turbulence?.contains("MOD") == true || p.icing?.contains("MOD") == true -> c.caution
                                else -> c.ink2
                            })
                    }
                }
            }
        }
    }
}

@Composable
private fun SigmetRow(s: Sigmet, dist: Double) {
    val c = Fm.c
    var open by remember { mutableStateOf(false) }
    val over = dist == 0.0
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(sigmetIcon(s), null, tint = if (over) c.caution else c.ink2, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${s.hazardLabel}, ${s.levels}", style = MaterialTheme.typography.titleSmall, color = if (over) c.caution else c.ink)
                Text(
                    (if (over) loc("Nad lotniskiem", "Over the airport") else loc("${dist.fmt(0)} NM od lotniska", "${dist.fmt(0)} NM from the airport")) +
                        loc(", ważny do ${hhmmZ.format(Instant.ofEpochSecond(s.validTo))}Z, ${s.fir.ifBlank { s.id }}", ", valid until ${hhmmZ.format(Instant.ofEpochSecond(s.validTo))}Z, ${s.fir.ifBlank { s.id }}"),
                    style = MaterialTheme.typography.bodySmall, color = c.ink2,
                )
            }
        }
        if (open && s.raw.isNotBlank()) {
            Text(s.raw, style = RawText.copy(fontSize = 11.sp, lineHeight = 16.sp), color = c.ink2, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Tabela poziom lotu × godzina z wiatrem i temperaturą oraz izoterma zera. */
@Composable
fun AloftPart(d: AirportDetail, s: Settings) {
    val c = Fm.c
    val a = d.aloft
    Section(loc("Wiatr na wysokościach", "Winds aloft"), Icons.Outlined.Air) {
        if (a == null || a.levels.isEmpty()) {
            Text(if (d.loading) loc("Pobieranie prognozy…", "Loading forecast…") else loc("Prognoza z Open-Meteo jest niedostępna.", "The Open-Meteo forecast is unavailable."), style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            return@Section
        }
        // Co 3 godziny, żeby tabela mieściła się na telefonie
        val cols = a.hoursUtc.indices.filter { it % 3 == 0 }.take(4)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column {
                Cell("", header = true, width = 58)
                a.levels.reversed().forEach { l -> Cell("FL%03d".format(l.flightLevel), header = true, width = 58) }
                Cell("0 °C", header = true, width = 58)
            }
            cols.forEach { i ->
                Column {
                    Cell(a.hoursUtc[i] + "Z", header = true, width = 92)
                    a.levels.reversed().forEach { l ->
                        val cell = l.cells.getOrNull(i)
                        val txt = if (cell?.dirDeg == null || cell.speedKt == null) "—" else "%03d/%02d %+d".format(cell!!.dirDeg, cell.speedKt, cell.tempC ?: 0)
                        Cell(txt, width = 92, strong = (cell?.speedKt ?: 0) >= 50)
                    }
                    val fz = a.freezingLevelFt.getOrNull(i)
                    Cell(fz?.let { if (s.altUnit == AltUnit.FT) "$it ft" else "${Atmo.ftToM(it.toDouble()).roundToInt()} m" } ?: "—", width = 92)
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Rule()
        Text(
            loc("Kierunek/prędkość w węzłach i temperatura w °C. Poziom lotu wyliczony z wysokości geopotencjalnej (model Open-Meteo), ostatni wiersz to wysokość izotermy zera.", "Direction/speed in knots and temperature in °C. Flight level derived from geopotential height (Open-Meteo model); the last row is the freezing level."),
            style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Cell(text: String, header: Boolean = false, width: Int, strong: Boolean = false) {
    val c = Fm.c
    Box(Modifier.width(width.dp).padding(vertical = 5.dp)) {
        Text(
            text,
            fontFamily = Mono,
            fontSize = if (header) 12.sp else 13.sp,
            fontWeight = if (header || strong) FontWeight.Bold else FontWeight.Normal,
            color = when {
                header -> c.ink2
                strong -> c.accent
                else -> c.ink
            },
        )
    }
}

/**
 * ILS i częstotliwości z bazy MSFS (przez OpenAirBook Link, zapisane w telefonie).
 * Przy połączeniu dotknięcie ILS ustawia go w NAV1; bez połączenia kopiuje częstotliwość.
 */
@Composable
fun MsfsRadioBlock(d: AirportDetail, connected: Boolean, onRefresh: () -> Unit, onSetNav: (Double, Boolean) -> Unit, onCopy: (Double) -> Unit) {
    val c = Fm.c
    val nav = d.nav
    Spacer(Modifier.size(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(loc("ILS i lokalizery", "ILS and localizers"), style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
        if (connected) androidx.compose.material3.TextButton(onRefresh, enabled = !d.navLoading) {
            Text(if (d.navLoading) loc("Pobieranie…", "Loading…") else loc("Pobierz z MSFS", "Load from MSFS"))
        }
    }
    val ils = nav?.airport?.runways.orEmpty().filter { it.ils?.freqMhz != null }
    when {
        nav?.airport == null -> Text(
            if (connected) loc("Pobieranie danych lotniska z bazy MSFS…", "Loading airport data from the MSFS database…") else loc("Częstotliwości ILS pochodzą z bazy MSFS 2024. Połącz się z OpenAirBook Link — dane zostaną zapisane w telefonie i będą dostępne bez symulatora.", "ILS frequencies come from the MSFS 2024 database. Connect to OpenAirBook Link — the data is saved on the phone and available without the simulator."),
            style = MaterialTheme.typography.bodySmall, color = c.ink2,
        )
        ils.isEmpty() -> Text(loc("Baza MSFS nie ma ILS dla tego lotniska (lotniska dodatkowe bywają niekompletne).", "The MSFS database has no ILS for this airport (add-on airports can be incomplete)."), style = MaterialTheme.typography.bodySmall, color = c.ink2)
    }
    var menuFor by remember { mutableStateOf<Double?>(null) }
    ils.forEach { rw ->
        val i = rw.ils!!
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { if (connected) menuFor = i.freqMhz else onCopy(i.freqMhz!!) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RWY ${rw.ident}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink, modifier = Modifier.width(78.dp))
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(i.ident, i.locCourseDeg?.let { loc("kurs %03d°", "course %03d°").format(it.roundToInt()) }).joinToString(" · ").ifBlank { "ILS" },
                        style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    i.gsAngleDeg?.let { Text(loc("ścieżka %.1f°", "glide slope %.1f°").format(it), style = MaterialTheme.typography.bodySmall, color = c.ink2) }
                }
                Text("%.2f".format(java.util.Locale.US, i.freqMhz), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = c.blue)
            }
            androidx.compose.material3.DropdownMenu(expanded = menuFor == i.freqMhz, onDismissRequest = { menuFor = null }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text(loc("Ustaw w NAV1 jako zapasową", "Set as NAV1 standby")) }, onClick = { onSetNav(i.freqMhz!!, false); menuFor = null })
                androidx.compose.material3.DropdownMenuItem(text = { Text(loc("Ustaw w NAV1 jako aktywną", "Set as NAV1 active")) }, onClick = { onSetNav(i.freqMhz!!, true); menuFor = null })
                androidx.compose.material3.DropdownMenuItem(text = { Text(loc("Kopiuj", "Copy")) }, onClick = { onCopy(i.freqMhz!!); menuFor = null })
            }
        }
        Rule()
    }
    // Częstotliwości, których brakuje w OurAirports, a są w MSFS
    val known = d.frequencies.map { it.mhz }
    val extra = nav?.airport?.frequencies.orEmpty().filter { f -> known.none { kotlin.math.abs(it - f.mhz) < 0.003 } }
    if (extra.isNotEmpty()) {
        Text(loc("Dodatkowe częstotliwości z MSFS", "Additional frequencies from MSFS"), style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
        extra.forEach { f ->
            Row(Modifier.fillMaxWidth().clickable { onCopy(f.mhz) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.type, fontFamily = Mono, fontSize = 12.sp, color = c.ink2, modifier = Modifier.width(64.dp))
                Text(f.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
                Text("%.3f".format(java.util.Locale.US, f.mhz), fontFamily = Mono, fontSize = 16.sp, color = c.blue)
            }
        }
    }
    nav?.let {
        Text(loc("Baza MSFS, pobrano ${java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.fetchedAt))}", "MSFS database, loaded ${java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.fetchedAt))}"),
            style = MaterialTheme.typography.labelSmall, color = c.ink2, modifier = Modifier.padding(top = 8.dp))
    }
}
