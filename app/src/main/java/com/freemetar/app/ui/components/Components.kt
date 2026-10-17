package com.freemetar.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import com.freemetar.app.ui.theme.PanelHi
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

/** Szerokość okna → liczba kolumn; telefon 1, składany/tablet 2–3. */
@Composable
fun windowColumns(): Int {
    val w = LocalConfiguration.current.screenWidthDp
    return when { w >= 1000 -> 3; w >= 600 -> 2; else -> 1 }
}

@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(16.dp)
            .animateContentSize(),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = Sky, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
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
fun StatTile(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier, accent: Color = TextHi, icon: ImageVector? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = TextLo, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLo, maxLines = 1)
        }
        Spacer(Modifier.size(4.dp))
        Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, color = TextLo, fontSize = 12.sp)
    }
}

/** Pusty stan z dużą ikoną — zamiast samego szarego tekstu. */
@Composable
fun EmptyState(icon: ImageVector, title: String, sub: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(PanelHi), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Sky, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(title, color = TextHi, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (sub != null) Text(sub, color = TextLo, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
    }
}

/** Nagłówek sekcji listy z ikoną. */
@Composable
fun ListHeader(text: String, icon: ImageVector, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TextLo, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLo, modifier = Modifier.weight(1f))
        trailing()
    }
}

/**
 * Prosty wykres liniowy trendu (np. temperatura z 12 h). Punkty w kolejności czasu.
 * Rysuje linię, wypełnienie i wartości min/maks.
 */
@Composable
fun TrendChart(label: String, values: List<Double>, unit: String, color: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(PanelHi).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)) }
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLo, modifier = Modifier.weight(1f))
            values.lastOrNull()?.let { Text("${it.toInt()}$unit", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        Spacer(Modifier.size(8.dp))
        if (values.size < 2) {
            Text("Za mało danych", color = TextLo, fontSize = 12.sp)
            return@Column
        }
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val min = values.min(); val max = values.max()
            val span = (max - min).takeIf { it > 0.0 } ?: 1.0
            val pad = 14f
            fun pt(i: Int, v: Double) = Offset(
                i * size.width / (values.size - 1),
                pad + (size.height - 2 * pad) * (1 - ((v - min) / span)).toFloat(),
            )
            val line = Path(); val fill = Path()
            values.forEachIndexed { i, v ->
                val p = pt(i, v)
                if (i == 0) { line.moveTo(p.x, p.y); fill.moveTo(p.x, size.height); fill.lineTo(p.x, p.y) } else { line.lineTo(p.x, p.y); fill.lineTo(p.x, p.y) }
            }
            fill.lineTo(size.width, size.height); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, color, style = Stroke(3f, cap = StrokeCap.Round))
            val last = pt(values.size - 1, values.last())
            drawCircle(color, 5f, last)
            val st = TextStyle(color = TextLo, fontSize = 10.sp)
            drawText(measurer.measure("max ${max.toInt()}", st), topLeft = Offset(0f, -2f))
            drawText(measurer.measure("min ${min.toInt()}", st), topLeft = Offset(0f, size.height - 14f))
        }
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
