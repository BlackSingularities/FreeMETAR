package com.freemetar.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.data.Runway
import com.freemetar.app.domain.Wind
import com.freemetar.app.ui.theme.Amber
import com.freemetar.app.ui.theme.Line
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.Panel
import com.freemetar.app.ui.theme.Sky
import com.freemetar.app.ui.theme.TextHi
import com.freemetar.app.ui.theme.TextLo
import com.freemetar.app.ui.theme.categoryColor
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLo, modifier = Modifier.weight(1f))
                trailing()
            }
            Spacer(Modifier.size(10.dp))
        }
        content()
    }
}

@Composable
fun CategoryBadge(cat: String?, modifier: Modifier = Modifier) {
    val c = categoryColor(cat)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(6.dp))
        Text(cat ?: "—", color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatTile(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier, accent: Color = TextHi) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLo)
        Spacer(Modifier.size(4.dp))
        Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, color = TextLo, fontSize = 12.sp)
    }
}

@Composable
fun TileRow(vararg tiles: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { tiles.forEach { it() } }
}

/**
 * Schemat lotniska: pasy w skali, róża kierunków i strzałka wiatru.
 * Pasy rysowane względem środka lotniska, więc układ odpowiada rzeczywistości.
 */
@Composable
fun RunwayWindDiagram(runways: List<Runway>, windDir: Int?, windKt: Int?, variable: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().aspectRatio(1f)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * 0.92f

        drawCircle(Line, r, c, style = Stroke(1.5f))
        drawCircle(Line.copy(alpha = 0.5f), r * 0.66f, c, style = Stroke(1f))
        for (d in 0 until 360 step 10) {
            val len = if (d % 30 == 0) 14f else 6f
            val a = Math.toRadians(d.toDouble() - 90)
            drawLine(TextLo, Offset(c.x + (r - len) * cos(a).toFloat(), c.y + (r - len) * sin(a).toFloat()),
                Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), 1.5f)
        }
        listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (s, d) ->
            val a = Math.toRadians(d.toDouble() - 90)
            val layout = measurer.measure(s, TextStyle(color = if (s == "N") Amber else TextLo, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            val p = Offset(c.x + (r - 30) * cos(a).toFloat() - layout.size.width / 2, c.y + (r - 30) * sin(a).toFloat() - layout.size.height / 2)
            drawText(layout, topLeft = p)
        }

        // Pasy: pozycja z współrzędnych progów, jeśli są — inaczej przez środek
        val coords = runways.flatMap { listOfNotNull(it.le.lat?.let { la -> it.le.lon?.let { lo -> la to lo } }, it.he.lat?.let { la -> it.he.lon?.let { lo -> la to lo } }) }
        val cLat = coords.map { it.first }.average().takeIf { !it.isNaN() }
        val cLon = coords.map { it.second }.average().takeIf { !it.isNaN() }
        val maxLen = runways.maxOfOrNull { it.lengthFt ?: 0 }?.coerceAtLeast(1) ?: 1
        val spanNm = if (cLat != null && cLon != null) coords.maxOf { kotlin.math.hypot(it.first - cLat, (it.second - cLon) * cos(Math.toRadians(cLat))) } else 0.0
        val scale = if (spanNm > 0) (r * 0.55f) / spanNm.toFloat() else 0f

        runways.forEach { rw ->
            val hdg = Wind.headingOf(rw.le.ident, rw.le.headingTrue) ?: return@forEach
            val half = (rw.lengthFt ?: maxLen).toFloat() / maxLen * r * 0.55f
            val center = if (scale > 0 && rw.le.lat != null && rw.he.lat != null && rw.le.lon != null && rw.he.lon != null && cLat != null && cLon != null) {
                val mLat = (rw.le.lat + rw.he.lat) / 2; val mLon = (rw.le.lon + rw.he.lon) / 2
                Offset(c.x + ((mLon - cLon) * cos(Math.toRadians(cLat)) * scale).toFloat(), c.y - ((mLat - cLat) * scale).toFloat())
            } else c
            rotate(hdg.toFloat(), center) {
                val w = 16f
                drawRect(Color(0xFF334155), Offset(center.x - w / 2, center.y - half), Size(w, half * 2))
                drawLine(TextHi.copy(alpha = 0.7f), Offset(center.x, center.y - half + 8), Offset(center.x, center.y + half - 8), 1.5f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
            // Oznaczenia końców
            val a = Math.toRadians(hdg - 90)
            listOf(rw.le.ident to (half + 16), rw.he.ident to -(half + 16)).forEach { (id, dist) ->
                if (id.isBlank()) return@forEach
                val layout = measurer.measure(id, TextStyle(color = TextHi, fontSize = 11.sp, fontFamily = Mono))
                // le znajduje się po stronie przeciwnej do kierunku lądowania
                val p = Offset(center.x - (dist * cos(a)).toFloat() - layout.size.width / 2, center.y - (dist * sin(a)).toFloat() - layout.size.height / 2)
                drawText(layout, topLeft = p)
            }
        }

        // Wiatr: strzałka wskazuje, DOKĄD wieje; zaczyna się od strony, z której wieje
        if (windDir != null && (windKt ?: 0) > 0 && !variable) {
            val a = Math.toRadians(windDir.toDouble() - 90)
            val from = Offset(c.x + r * 0.95f * cos(a).toFloat(), c.y + r * 0.95f * sin(a).toFloat())
            val to = Offset(c.x + r * 0.35f * cos(a).toFloat(), c.y + r * 0.35f * sin(a).toFloat())
            drawLine(Sky, from, to, 6f)
            val head = Path().apply {
                val back = 22f; val spread = 0.45
                val ang = Math.atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
                moveTo(to.x + 8 * cos(ang).toFloat(), to.y + 8 * sin(ang).toFloat())
                lineTo(to.x - back * cos(ang - spread).toFloat(), to.y - back * sin(ang - spread).toFloat())
                lineTo(to.x - back * cos(ang + spread).toFloat(), to.y - back * sin(ang + spread).toFloat())
                close()
            }
            drawPath(head, Sky)
        }
    }
}
