package com.freemetar.app.ui.flight

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.MainViewModel
import com.freemetar.app.data.link.FlightLog
import com.freemetar.app.domain.LandingReport
import com.freemetar.app.ui.components.Fact
import com.freemetar.app.ui.components.FactGrid
import com.freemetar.app.ui.components.Rule
import com.freemetar.app.ui.components.Section
import com.freemetar.app.ui.components.TrendChart
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/** Kolor oceny: zieleń / biel / bursztyn / czerwień — jedyne miejsce, gdzie ocena ma kolor. */
@Composable
fun scoreColor(score: Int): Color {
    val c = Fm.c
    return when {
        score >= 90 -> c.green
        score >= 75 -> c.ink
        score >= 55 -> c.caution
        else -> c.danger
    }
}

@Composable
fun ScoreBadge(l: LandingReport?, size: Int = 44) {
    val c = Fm.c
    val col = l?.let { scoreColor(it.score) } ?: c.ink2
    Box(Modifier.size(size.dp).clip(CircleShape).border(2.dp, col, CircleShape), contentAlignment = Alignment.Center) {
        Text(l?.score?.toString() ?: "—", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = (size / 3).sp, color = col)
    }
}

/** Historia lotów z podsumowaniem; dotknięcie otwiera szczegóły. */
@Composable
fun FlightHistorySection(vm: MainViewModel, onOpenFlight: (String) -> Unit) {
    val c = Fm.c
    val logs by vm.flightLogs.collectAsStateWithLifecycle()
    if (logs.isEmpty()) return
    Section("Historia lotów", Icons.Outlined.History) {
        val minutes = logs.sumOf { it.durationMin }
        val scored = logs.mapNotNull { it.landing?.score }
        FactGrid(
            listOf(
                Fact("Loty", logs.size.toString()),
                Fact("Czas lotu", "%d:%02d".format(minutes / 60, minutes % 60)),
                Fact("Średnia ocena", if (scored.isEmpty()) "—" else scored.average().roundToInt().toString(),
                    sub = scored.maxOrNull()?.let { "najlepsza $it" }),
                Fact("Dystans", "${logs.sumOf { it.distanceNm }} NM"),
            ),
        )
        Spacer(Modifier.size(8.dp))
        logs.take(50).forEach { f ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpenFlight(f.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ScoreBadge(f.landing, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${f.from ?: "?"} → ${f.to ?: "?"}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                    Text(
                        listOfNotNull(
                            DateFormat.getDateInstance(DateFormat.SHORT).format(Date(f.start)),
                            "%d:%02d".format(f.durationMin / 60, f.durationMin % 60),
                            f.landing?.let { "${it.vsFpm} ft/min" },
                            f.aircraft,
                        ).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = c.ink2, maxLines = 1,
                    )
                }
            }
            Rule()
        }
    }
}

/** Szczegóły lotu: ocena lądowania, tor, wykresy, eksport. */
@Composable
fun FlightDetailScreen(vm: MainViewModel, id: String, onBack: () -> Unit) {
    val c = Fm.c
    val logs by vm.flightLogs.collectAsStateWithLifecycle()
    val f = logs.firstOrNull { it.id == id } ?: return
    val ctx = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        uri?.let { u -> vm.exportFlightGpx(ctx, f, u) }
    }
    Column(Modifier.fillMaxSize().background(c.paper)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Wróć", tint = c.ink) }
            Column(Modifier.weight(1f)) {
                Text("${f.from ?: "?"} → ${f.to ?: "?"}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.ink)
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.start)) + (f.aircraft?.let { ", $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = c.ink2, maxLines = 1)
            }
            IconButton({ exporter.launch("lot-${f.from ?: "x"}-${f.to ?: "y"}.gpx") }) { Icon(Icons.Outlined.FileDownload, "Eksportuj tor do GPX", tint = c.ink) }
            IconButton({ vm.deleteFlightLog(f.id); onBack() }) { Icon(Icons.Outlined.Delete, "Usuń lot", tint = c.danger) }
        }
        Rule()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            f.landing?.let { LandingCard(it) } ?: Section("Lądowanie", Icons.Outlined.FlightLand) {
                Text("Brak danych o przyziemieniu (lot zakończony w powietrzu albo nagrany starszą wersją).", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            }
            Section("Lot", Icons.Outlined.Timeline) {
                FactGrid(listOf(
                    Fact("Czas", "%d:%02d".format(f.durationMin / 60, f.durationMin % 60)),
                    Fact("Dystans", "${f.distanceNm} NM"),
                    Fact("Najwyżej", "FL%03d".format(f.maxAltFt / 100)),
                    Fact("Średnia GS", f.track.map { it.gsKt }.filter { it > 50 }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.let { "$it kt" } ?: "—"),
                ))
                Spacer(Modifier.size(12.dp))
                TrackPlot(f)
            }
            Section("Profil lotu", Icons.AutoMirrored.Outlined.ShowChart) {
                TrendChart("Wysokość ft", f.track.map { it.altFt.toDouble() }, "", c.magenta, Modifier.fillMaxWidth())
                Spacer(Modifier.size(14.dp))
                TrendChart("Prędkość względem ziemi kt", f.track.map { it.gsKt.toDouble() }, "", c.accent, Modifier.fillMaxWidth())
                val vs = f.track.mapNotNull { it.vsFpm?.toDouble() }
                if (vs.size >= 2) { Spacer(Modifier.size(14.dp)); TrendChart("Prędkość pionowa ft/min", vs, "", c.green, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun LandingCard(l: LandingReport) {
    val c = Fm.c
    val col = scoreColor(l.score)
    Section("Lądowanie" + (l.runway?.let { " — $it" } ?: ""), Icons.Outlined.FlightLand, frame = if (l.score < 55) c.danger else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreBadge(l, 72)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(l.grade, style = MaterialTheme.typography.headlineMedium, color = col)
                Text("${l.vsFpm} ft/min przy przyziemieniu", fontFamily = Mono, fontSize = 14.sp, color = c.ink)
            }
        }
        Spacer(Modifier.size(12.dp))
        FactGrid(listOfNotNull(
            Fact("Prędkość pionowa", "${l.vsFpm} ft/min"),
            Fact("Przeciążenie", l.gForce?.let { "%.2f g".format(it) } ?: "—"),
            Fact("Przechylenie", l.bankDeg?.let { "%.1f°".format(abs(it)) } ?: "—"),
            Fact("Pochylenie", l.pitchDeg?.let { "%.1f°".format(it) } ?: "—"),
            Fact("Za progiem", l.alongFt?.let { "$it ft" } ?: "—"),
            Fact("Od osi", l.crossFt?.let { "${abs(it)} ft " + if (it >= 0) "P" else "L" } ?: "—"),
            Fact("Prędkość IAS", l.iasKt?.let { "$it kt" } ?: "—"),
            Fact("Odbicia", l.bounces.toString(), accent = if (l.bounces > 0) c.caution else null),
        ))
        Spacer(Modifier.size(10.dp))
        l.notes.forEach { n -> Text("• $n", style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

/** Tor lotu w rzucie z góry (bez mapy — działa offline), start zielony, koniec czerwony. */
@Composable
private fun TrackPlot(f: FlightLog) {
    val c = Fm.c
    val pts = f.track
    if (pts.size < 2) return
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(10.dp)).background(c.terrain)) {
        val minLat = pts.minOf { it.lat }; val maxLat = pts.maxOf { it.lat }
        val minLon = pts.minOf { it.lon }; val maxLon = pts.maxOf { it.lon }
        val k = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = ((maxLon - minLon) * k).coerceAtLeast(1e-4); val spanY = (maxLat - minLat).coerceAtLeast(1e-4)
        val pad = 24f
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY).toFloat()
        val ox = (size.width - spanX.toFloat() * scale) / 2; val oy = (size.height - spanY.toFloat() * scale) / 2
        fun p(lat: Double, lon: Double) = Offset(ox + ((lon - minLon) * k).toFloat() * scale, size.height - oy - ((lat - minLat).toFloat() * scale))
        val path = Path().apply { pts.forEachIndexed { i, t -> val o = p(t.lat, t.lon); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
        drawPath(path, c.magenta, style = Stroke(3f, cap = StrokeCap.Round))
        drawCircle(c.green, 7f, p(pts.first().lat, pts.first().lon))
        drawCircle(c.danger, 7f, p(pts.last().lat, pts.last().lon))
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(f.from ?: "start", fontFamily = Mono, fontSize = 12.sp, color = c.green)
        Text(f.to ?: "koniec", fontFamily = Mono, fontSize = 12.sp, color = c.danger, textAlign = TextAlign.End)
    }
}
