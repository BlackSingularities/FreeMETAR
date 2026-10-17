package com.freemetar.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.freemetar.app.MainViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.domain.Atmo
import com.freemetar.app.domain.MetarDecoder
import com.freemetar.app.domain.Wind
import com.freemetar.app.domain.fmt
import com.freemetar.app.ui.components.SectionCard
import com.freemetar.app.ui.components.StatTile
import com.freemetar.app.ui.theme.Amber
import com.freemetar.app.ui.theme.CatIfr
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.RawText
import com.freemetar.app.ui.theme.Sky
import com.freemetar.app.ui.theme.TextHi
import com.freemetar.app.ui.theme.TextLo
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Tool(val label: String, val icon: ImageVector) {
    ROUTE("Trasa", Icons.Outlined.Route),
    WIND("Wiatr", Icons.Outlined.Air),
    DENSITY("Wys. gęst.", Icons.Outlined.Height),
    DESCENT("Zniżanie", Icons.AutoMirrored.Outlined.TrendingDown),
    FUEL("Paliwo", Icons.Outlined.LocalGasStation),
    UNITS("Jednostki", Icons.Outlined.SwapHoriz),
    DECODE("Dekoder", Icons.Outlined.Translate),
    CHEAT("Ściąga", Icons.AutoMirrored.Outlined.MenuBook),
}

@Composable
fun ToolsScreen(vm: MainViewModel) {
    var tool by rememberSaveable { mutableStateOf(Tool.ROUTE) }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Narzędzia", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp)) }
        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Tool.entries.size) { i ->
                    val t = Tool.entries[i]
                    FilterChip(selected = tool == t, onClick = { tool = t }, label = { Text(t.label) }, leadingIcon = { Icon(t.icon, null, modifier = Modifier.size(18.dp)) })
                }
            }
        }
        item {
            when (tool) {
                Tool.ROUTE -> RouteTool(vm)
                Tool.CHEAT -> CheatSheet()
                Tool.WIND -> WindTool()
                Tool.DENSITY -> DensityTool()
                Tool.DESCENT -> DescentTool()
                Tool.FUEL -> FuelTool()
                Tool.UNITS -> UnitsTool()
                Tool.DECODE -> DecodeTool()
            }
        }
    }
}

@Composable
internal fun NumField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp), modifier = modifier,
    )
}

internal fun String.num() = replace(',', '.').toDoubleOrNull()

@Composable
private fun WindTool() {
    var dir by rememberSaveable { mutableStateOf("240") }
    var spd by rememberSaveable { mutableStateOf("15") }
    var gust by rememberSaveable { mutableStateOf("") }
    var rwy by rememberSaveable { mutableStateOf("29") }
    SectionCard("Składowe wiatru", icon = Icons.Outlined.Air) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Kierunek °", dir, Modifier.weight(1f)) { dir = it }
            NumField("Prędkość kt", spd, Modifier.weight(1f)) { spd = it }
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Porywy kt", gust, Modifier.weight(1f)) { gust = it }
            NumField("Pas (np. 29 lub 287°)", rwy, Modifier.weight(1f)) { rwy = it }
        }
        val d = dir.num(); val s = spd.num(); val r = rwy.num()
        if (d != null && s != null && r != null) {
            val hdg = if (r <= 36) r * 10 else r
            val c = Wind.components(d, s, hdg)
            val g = gust.num()?.let { Wind.components(d, it, hdg) }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(if (c.headwind >= 0) "Czołowy" else "Tylny", "${abs(c.headwind).roundToInt()} kt", g?.let { "porywy ${abs(it.headwind).roundToInt()} kt" }, Modifier.weight(1f), accent = if (c.headwind < 0) CatIfr else TextHi)
                StatTile("Boczny", "${abs(c.crosswind).roundToInt()} kt", (g?.let { "porywy ${abs(it.crosswind).roundToInt()} kt · " } ?: "") + c.crossSide, Modifier.weight(1f), accent = if (abs(c.crosswind) > 15) Amber else TextHi)
            }
            Spacer(Modifier.size(8.dp))
            Text("Korekta znosu przy TAS 100 kt: ok. ${(Math.toDegrees(Math.asin((c.crosswind / 100).coerceIn(-1.0, 1.0)))).roundToInt()}°", color = TextLo, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DensityTool() {
    var elev by rememberSaveable { mutableStateOf("361") }
    var qnh by rememberSaveable { mutableStateOf("1013") }
    var oat by rememberSaveable { mutableStateOf("25") }
    var ias by rememberSaveable { mutableStateOf("110") }
    SectionCard("Wysokość gęstościowa i TAS", icon = Icons.Outlined.Height) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Wysokość ft", elev, Modifier.weight(1f)) { elev = it }
            NumField("QNH hPa", qnh, Modifier.weight(1f)) { qnh = it }
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("OAT °C", oat, Modifier.weight(1f)) { oat = it }
            NumField("IAS kt", ias, Modifier.weight(1f)) { ias = it }
        }
        val e = elev.num(); val q = qnh.num(); val t = oat.num()
        if (e != null && q != null && t != null) {
            val pa = Atmo.pressureAltitude(e, q)
            val da = Atmo.densityAltitude(e, q, t)
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Ciśnieniowa", "${pa.roundToInt()} ft", null, Modifier.weight(1f))
                StatTile("Gęstościowa", "${da.roundToInt()} ft", "ISA ${Atmo.isaTemp(pa).fmt(1)} °C", Modifier.weight(1f), accent = if (da - e > 2000) Amber else TextHi)
            }
            ias.num()?.let {
                Spacer(Modifier.size(10.dp))
                StatTile("TAS (przybliżona)", "${Atmo.tas(it, da).roundToInt()} kt", null, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DescentTool() {
    var from by rememberSaveable { mutableStateOf("35000") }
    var to by rememberSaveable { mutableStateOf("3000") }
    var gs by rememberSaveable { mutableStateOf("420") }
    var angle by rememberSaveable { mutableStateOf("3") }
    SectionCard("Planowanie zniżania (TOD)", icon = Icons.AutoMirrored.Outlined.TrendingDown) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Z wysokości ft", from, Modifier.weight(1f)) { from = it }
            NumField("Do wysokości ft", to, Modifier.weight(1f)) { to = it }
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("GS kt", gs, Modifier.weight(1f)) { gs = it }
            NumField("Ścieżka °", angle, Modifier.weight(1f)) { angle = it }
        }
        val f = from.num(); val t = to.num(); val g = gs.num(); val a = angle.num()
        if (f != null && t != null && g != null && a != null && a > 0 && f > t) {
            val dist = (f - t) / (Math.tan(Math.toRadians(a)) * 6076.12)
            val vs = g * 6076.12 / 60 * Math.tan(Math.toRadians(a))
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Początek zniżania", "${dist.roundToInt()} NM", "przed punktem docelowym", Modifier.weight(1f), accent = Sky)
                StatTile("Prędkość pionowa", "${vs.roundToInt()} ft/min", "czas ${(dist / g * 60).roundToInt()} min", Modifier.weight(1f))
            }
            Text("Reguła kciuka: 3 × (FL/10) NM, VS ≈ GS × 5.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun FuelTool() {
    var dist by rememberSaveable { mutableStateOf("250") }
    var gs by rememberSaveable { mutableStateOf("120") }
    var burn by rememberSaveable { mutableStateOf("30") }
    var reserve by rememberSaveable { mutableStateOf("45") }
    SectionCard("Paliwo i czas lotu", icon = Icons.Outlined.LocalGasStation) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Dystans NM", dist, Modifier.weight(1f)) { dist = it }
            NumField("GS kt", gs, Modifier.weight(1f)) { gs = it }
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Zużycie /h", burn, Modifier.weight(1f)) { burn = it }
            NumField("Rezerwa min", reserve, Modifier.weight(1f)) { reserve = it }
        }
        val d = dist.num(); val g = gs.num(); val b = burn.num(); val r = reserve.num() ?: 0.0
        if (d != null && g != null && g > 0 && b != null) {
            val h = d / g
            val trip = h * b
            val res = r / 60 * b
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Czas", "%d:%02d".format(h.toInt(), ((h - h.toInt()) * 60).roundToInt()), null, Modifier.weight(1f))
                StatTile("Na trasę", trip.fmt(1), null, Modifier.weight(1f))
                StatTile("Z rezerwą", (trip + res).fmt(1), "+${res.fmt(1)}", Modifier.weight(1f), accent = Sky)
            }
            Text("Jednostki paliwa takie jak zużycia (l, kg, lb, gal).", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun UnitsTool() {
    var v by rememberSaveable { mutableStateOf("1013") }
    val x = v.num()
    SectionCard("Przelicznik jednostek", icon = Icons.Outlined.SwapHoriz) {
        NumField("Wartość", v, Modifier.fillMaxWidth()) { v = it }
        Spacer(Modifier.size(10.dp))
        if (x != null) {
            val rows = listOf(
                "hPa → inHg" to Atmo.hpaToInHg(x).fmt(2),
                "inHg → hPa" to Atmo.inHgToHpa(x).fmt(1),
                "ft → m" to Atmo.ftToM(x).fmt(1),
                "m → ft" to Atmo.mToFt(x).fmt(0),
                "kt → km/h" to Atmo.ktToKmh(x).fmt(1),
                "kt → m/s" to Atmo.ktToMs(x).fmt(1),
                "NM → km" to Atmo.nmToKm(x).fmt(2),
                "SM → km" to Atmo.smToKm(x).fmt(2),
                "°C → °F" to Atmo.cToF(x).fmt(1),
                "°F → °C" to Atmo.fToC(x).fmt(1),
            )
            rows.forEach { (l, r) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(l, color = TextLo, modifier = Modifier.weight(1f))
                    Text(r, fontFamily = Mono, color = TextHi, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DecodeTool() {
    var raw by rememberSaveable { mutableStateOf("EPWA 171030Z 24012G24KT 210V280 9999 -SHRA FEW012 BKN025CB 12/09 Q1008 TEMPO 4000 TSRA") }
    SectionCard("Dekoder METAR/TAF", icon = Icons.Outlined.Translate) {
        OutlinedTextField(raw, { raw = it.uppercase() }, Modifier.fillMaxWidth(), textStyle = RawText, shape = RoundedCornerShape(12.dp), minLines = 3)
        Spacer(Modifier.size(10.dp))
        MetarDecoder.decode(raw).filter { it.text != "—" && it.text.isNotBlank() }.forEach { d ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(d.token, fontFamily = Mono, color = Sky, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                Column { Text(d.text, color = TextHi, fontSize = 14.sp) }
            }
        }
    }
}
