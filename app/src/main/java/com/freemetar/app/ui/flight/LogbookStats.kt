package com.freemetar.app.ui.flight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.data.link.FlightLog
import com.freemetar.app.domain.loc
import com.freemetar.app.ui.theme.Fm
import kotlin.math.roundToInt

/**
 * Rozszerzone statystyki dla dziennika lotów.
 */
@Composable
fun LogbookStatistics(logs: List<FlightLog>) {
    val c = Fm.c
    if (logs.isEmpty()) return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Główne statystyki
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                icon = Icons.Outlined.FlightTakeoff,
                value = logs.size.toString(),
                label = loc("Lotów", "Flights"),
                modifier = Modifier.weight(1f)
            )

            val minutes = logs.sumOf { it.durationMin }
            StatCard(
                icon = Icons.Outlined.Schedule,
                value = "%d:%02d".format(minutes / 60, minutes % 60),
                label = loc("Czas", "Time"),
                modifier = Modifier.weight(1f)
            )

            StatCard(
                icon = Icons.Outlined.Public,
                value = "${logs.sumOf { it.distanceNm }}",
                label = "NM",
                modifier = Modifier.weight(1f)
            )
        }

        // Lądowania
        val scored = logs.mapNotNull { it.landing?.score }
        if (scored.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Outlined.Star,
                    value = scored.average().roundToInt().toString(),
                    label = loc("Średnia ocena", "Avg score"),
                    subtitle = loc("najlepsza", "best") + " ${scored.maxOrNull()}",
                    modifier = Modifier.weight(1f)
                )

                StatCard(
                    icon = Icons.Outlined.TrendingUp,
                    value = scored.size.toString(),
                    label = loc("Z oceną", "Scored"),
                    subtitle = "${(scored.size * 100 / logs.size)}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Wykres postępów w czasie (ostatnie 10 lotów z oceną)
        val recent = logs.mapNotNull { if (it.landing?.score != null) it to it.landing.score else null }.take(10).reversed()
        if (recent.size >= 3) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    loc("Postępy (ostatnie 10 lotów)", "Progress (last 10 flights)"),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink
                )
                Spacer(Modifier.height(12.dp))
                ProgressChart(recent.map { it.second }, modifier = Modifier.fillMaxWidth().height(120.dp))
            }
        }

        // Top lotniska
        val airports = logs.flatMap { listOfNotNull(it.from, it.to) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(5)

        if (airports.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    loc("Najczęstsze lotniska", "Most visited airports"),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink
                )
                Spacer(Modifier.height(8.dp))
                airports.forEach { (icao, count) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(icao, fontWeight = FontWeight.Bold, color = c.ink)
                        Text(
                            loc("$count lotów", "$count flights"),
                            fontSize = 13.sp,
                            color = c.ink2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val c = Fm.c
    Column(
        modifier
            .background(c.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = c.ink
        )
        Text(label, fontSize = 12.sp, color = c.ink2)
        if (subtitle != null) {
            Text(subtitle, fontSize = 11.sp, color = c.ink2)
        }
    }
}

@Composable
private fun ProgressChart(scores: List<Int>, modifier: Modifier = Modifier) {
    val c = Fm.c
    Canvas(modifier) {
        if (scores.size < 2) return@Canvas

        val w = size.width
        val h = size.height
        val maxScore = 100f
        val minScore = scores.minOrNull()?.toFloat()?.coerceAtMost(50f) ?: 0f
        val range = maxScore - minScore

        val stepX = w / (scores.size - 1)

        // Linia wykresu
        val path = Path()
        scores.forEachIndexed { i, score ->
            val x = i * stepX
            val y = h - ((score - minScore) / range * h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = c.accent,
            style = Stroke(width = 3.dp.toPx())
        )

        // Punkty
        scores.forEachIndexed { i, score ->
            val x = i * stepX
            val y = h - ((score - minScore) / range * h)
            drawCircle(
                color = c.accent,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }

        // Linia bazowa (80 punktów)
        val baseline80 = h - ((80f - minScore) / range * h)
        drawLine(
            color = c.ink2.copy(alpha = 0.3f),
            start = Offset(0f, baseline80),
            end = Offset(w, baseline80),
            strokeWidth = 1.dp.toPx()
        )
    }
}
