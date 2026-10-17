package com.freemetar.app.ui.screens

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
    Section("Zagrożenia", Icons.Outlined.WarningAmber, frame = if (d.sigmets.any { it.second == 0.0 }) c.caution else null) {
        if (!any && d.pireps.isEmpty()) {
            Text(
                if (d.loading) "Sprawdzanie SIGMET i raportów pilotów…" else "Brak ważnych SIGMET w promieniu 50 NM i raportów pilotów w okolicy.",
                style = MaterialTheme.typography.bodyMedium, color = c.ink2,
            )
            return@Section
        }
        d.sigmets.forEach { (s, dist) -> SigmetRow(s, dist) }
        if (d.pireps.isNotEmpty()) {
            if (d.sigmets.isNotEmpty()) Spacer(Modifier.size(8.dp))
            Text("Raporty pilotów (3 h, 100 NM)", style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.padding(bottom = 4.dp))
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
                    (if (over) "Nad lotniskiem" else "${dist.fmt(0)} NM od lotniska") +
                        ", ważny do ${hhmmZ.format(Instant.ofEpochSecond(s.validTo))}Z, ${s.fir.ifBlank { s.id }}",
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
    Section("Wiatr na wysokościach", Icons.Outlined.Air) {
        if (a == null || a.levels.isEmpty()) {
            Text(if (d.loading) "Pobieranie prognozy…" else "Prognoza z Open-Meteo jest niedostępna.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
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
            "Kierunek/prędkość w węzłach i temperatura w °C. Poziom lotu wyliczony z wysokości geopotencjalnej (model Open-Meteo), ostatni wiersz to wysokość izotermy zera.",
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
