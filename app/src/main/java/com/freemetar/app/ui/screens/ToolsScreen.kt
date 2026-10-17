package com.freemetar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip

import com.freemetar.app.domain.loc

import com.freemetar.app.ui.theme.Fm
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
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.RawText
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Tool(private val labelOf: () -> String, val icon: ImageVector) {
    ROUTE({ loc("Trasa", "Route") }, Icons.Outlined.Route),
    WIND({ loc("Wiatr", "Wind") }, Icons.Outlined.Air),
    DENSITY({ loc("Wys. gęst.", "Density alt") }, Icons.Outlined.Height),
    DESCENT({ loc("Zniżanie", "Descent") }, Icons.AutoMirrored.Outlined.TrendingDown),
    FUEL({ loc("Paliwo", "Fuel") }, Icons.Outlined.LocalGasStation),
    UNITS({ loc("Jednostki", "Units") }, Icons.Outlined.SwapHoriz),
    DECODE({ loc("Dekoder", "Decoder") }, Icons.Outlined.Translate),
    CHEAT({ loc("Ściąga", "Cheat sheet") }, Icons.AutoMirrored.Outlined.MenuBook),
    ;

    val label: String get() = labelOf()
}

/**
 * Narzędzia: wszystkie widoczne naraz jako kafelki (telefon) albo lista po lewej (tablet / poziomo),
 * wybrane narzędzie pod spodem lub obok. Aktywny kafelek to blok w kolorze tekstu.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(vm: MainViewModel) {
    var tool by rememberSaveable { mutableStateOf(Tool.ROUTE) }
    val content: @Composable () -> Unit = {
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
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        if (maxWidth >= 720.dp) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Column(
                    Modifier.width(230.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(loc("Narzędzia", "Tools"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
                    Tool.entries.forEach { t -> ToolTile(t, tool == t, Modifier.fillMaxWidth(), row = true) { tool = t } }
                }
                androidx.compose.foundation.layout.Box(Modifier.width(1.dp).fillMaxHeight().background(Fm.c.rule))
                androidx.compose.foundation.layout.Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 760.dp),
                ) { content() }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text(loc("Narzędzia", "Tools"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
                item {
                    androidx.compose.foundation.layout.FlowRow(
                        maxItemsInEachRow = 4,
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Tool.entries.forEach { t -> ToolTile(t, tool == t, Modifier.weight(1f)) { tool = t } }
                    }
                }
                item { content() }
            }
        }
    }
}

@Composable
private fun ToolTile(t: Tool, on: Boolean, modifier: Modifier, row: Boolean = false, onClick: () -> Unit) {
    val c = Fm.c
    val shape = RoundedCornerShape(if (row) 50 else 14)
    val m = modifier.clip(shape).background(if (on) c.ink else c.card).border(1.dp, if (on) c.ink else c.rule, shape).clickable(onClick = onClick)
    if (row) {
        androidx.compose.foundation.layout.Row(m.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(t.icon, null, tint = if (on) c.paper else c.ink2, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            Text(t.label, color = if (on) c.paper else c.ink, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        androidx.compose.foundation.layout.Column(m.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Icon(t.icon, null, tint = if (on) c.paper else c.ink2, modifier = Modifier.size(22.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
            Text(t.label, color = if (on) c.paper else c.ink, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
    SectionCard(loc("Składowe wiatru", "Wind components"), icon = Icons.Outlined.Air) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(loc("Kierunek °", "Direction °"), dir, Modifier.weight(1f)) { dir = it }
            NumField(loc("Prędkość kt", "Speed kt"), spd, Modifier.weight(1f)) { spd = it }
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(loc("Porywy kt", "Gusts kt"), gust, Modifier.weight(1f)) { gust = it }
            NumField(loc("Pas (np. 29 lub 287°)", "Runway (e.g. 29 or 287°)"), rwy, Modifier.weight(1f)) { rwy = it }
        }
        val d = dir.num(); val s = spd.num(); val r = rwy.num()
        if (d != null && s != null && r != null) {
            val hdg = if (r <= 36) r * 10 else r
            val c = Wind.components(d, s, hdg)
            val g = gust.num()?.let { Wind.components(d, it, hdg) }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(if (c.headwind >= 0) loc("Czołowy", "Headwind") else loc("Tylny", "Tailwind"), "${abs(c.headwind).roundToInt()} kt", g?.let { loc("porywy ${abs(it.headwind).roundToInt()} kt", "gusts ${abs(it.headwind).roundToInt()} kt") }, Modifier.weight(1f), accent = if (c.headwind < 0) Fm.c.ifr else Fm.c.ink)
                StatTile(loc("Boczny", "Crosswind"), "${abs(c.crosswind).roundToInt()} kt", (g?.let { loc("porywy ${abs(it.crosswind).roundToInt()} kt · ", "gusts ${abs(it.crosswind).roundToInt()} kt · ") } ?: "") + c.crossSide, Modifier.weight(1f), accent = if (abs(c.crosswind) > 15) Fm.c.caution else Fm.c.ink)
            }
            Spacer(Modifier.size(8.dp))
            Text(loc("Korekta znosu przy TAS 100 kt: ok. ${(Math.toDegrees(Math.asin((c.crosswind / 100).coerceIn(-1.0, 1.0)))).roundToInt()}°", "Wind correction at TAS 100 kt: about ${(Math.toDegrees(Math.asin((c.crosswind / 100).coerceIn(-1.0, 1.0)))).roundToInt()}°"), color = Fm.c.ink2, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DensityTool() {
    var elev by rememberSaveable { mutableStateOf("361") }
    var qnh by rememberSaveable { mutableStateOf("1013") }
    var oat by rememberSaveable { mutableStateOf("25") }
    var ias by rememberSaveable { mutableStateOf("110") }
    SectionCard(loc("Wysokość gęstościowa i TAS", "Density altitude and TAS"), icon = Icons.Outlined.Height) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(loc("Wysokość ft", "Altitude ft"), elev, Modifier.weight(1f)) { elev = it }
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
                StatTile(loc("Ciśnieniowa", "Pressure"), "${pa.roundToInt()} ft", null, Modifier.weight(1f))
                StatTile(loc("Gęstościowa", "Density"), "${da.roundToInt()} ft", "ISA ${Atmo.isaTemp(pa).fmt(1)} °C", Modifier.weight(1f), accent = if (da - e > 2000) Fm.c.caution else Fm.c.ink)
            }
            ias.num()?.let {
                Spacer(Modifier.size(10.dp))
                StatTile(loc("TAS (przybliżona)", "TAS (approx.)"), "${Atmo.tas(it, da).roundToInt()} kt", null, Modifier.fillMaxWidth())
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
    SectionCard(loc("Planowanie zniżania (TOD)", "Descent planning (TOD)"), icon = Icons.AutoMirrored.Outlined.TrendingDown) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(loc("Z wysokości ft", "From altitude ft"), from, Modifier.weight(1f)) { from = it }
            NumField(loc("Do wysokości ft", "To altitude ft"), to, Modifier.weight(1f)) { to = it }
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("GS kt", gs, Modifier.weight(1f)) { gs = it }
            NumField(loc("Ścieżka °", "Path °"), angle, Modifier.weight(1f)) { angle = it }
        }
        val f = from.num(); val t = to.num(); val g = gs.num(); val a = angle.num()
        if (f != null && t != null && g != null && a != null && a > 0 && f > t) {
            val dist = (f - t) / (Math.tan(Math.toRadians(a)) * 6076.12)
            val vs = g * 6076.12 / 60 * Math.tan(Math.toRadians(a))
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(loc("Początek zniżania", "Top of descent"), "${dist.roundToInt()} NM", loc("przed punktem docelowym", "before the target point"), Modifier.weight(1f), accent = Fm.c.accent)
                StatTile(loc("Prędkość pionowa", "Vertical speed"), "${vs.roundToInt()} ft/min", loc("czas ${(dist / g * 60).roundToInt()} min", "time ${(dist / g * 60).roundToInt()} min"), Modifier.weight(1f))
            }
            Text(loc("Reguła kciuka: 3 × (FL/10) NM, VS ≈ GS × 5.", "Rule of thumb: 3 × (FL/10) NM, VS ≈ GS × 5."), color = Fm.c.ink2, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun FuelTool() {
    var dist by rememberSaveable { mutableStateOf("250") }
    var gs by rememberSaveable { mutableStateOf("120") }
    var burn by rememberSaveable { mutableStateOf("30") }
    var reserve by rememberSaveable { mutableStateOf("45") }
    SectionCard(loc("Paliwo i czas lotu", "Fuel and flight time"), icon = Icons.Outlined.LocalGasStation) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(loc("Dystans NM", "Distance NM"), dist, Modifier.weight(1f)) { dist = it }
            NumField("GS kt", gs, Modifier.weight(1f)) { gs = it }
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(loc("Zużycie /h", "Burn /h"), burn, Modifier.weight(1f)) { burn = it }
            NumField(loc("Rezerwa min", "Reserve min"), reserve, Modifier.weight(1f)) { reserve = it }
        }
        val d = dist.num(); val g = gs.num(); val b = burn.num(); val r = reserve.num() ?: 0.0
        if (d != null && g != null && g > 0 && b != null) {
            val h = d / g
            val trip = h * b
            val res = r / 60 * b
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(loc("Czas", "Time"), "%d:%02d".format(h.toInt(), ((h - h.toInt()) * 60).roundToInt()), null, Modifier.weight(1f))
                StatTile(loc("Na trasę", "Trip"), trip.fmt(1), null, Modifier.weight(1f))
                StatTile(loc("Z rezerwą", "With reserve"), (trip + res).fmt(1), "+${res.fmt(1)}", Modifier.weight(1f), accent = Fm.c.accent)
            }
            Text(loc("Jednostki paliwa takie jak zużycia (l, kg, lb, gal).", "Fuel units match the burn rate (l, kg, lb, gal)."), color = Fm.c.ink2, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun UnitsTool() {
    var v by rememberSaveable { mutableStateOf("1013") }
    val x = v.num()
    SectionCard(loc("Przelicznik jednostek", "Unit converter"), icon = Icons.Outlined.SwapHoriz) {
        NumField(loc("Wartość", "Value"), v, Modifier.fillMaxWidth()) { v = it }
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
                    Text(l, color = Fm.c.ink2, modifier = Modifier.weight(1f))
                    Text(r, fontFamily = Mono, color = Fm.c.ink, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DecodeTool() {
    var raw by rememberSaveable { mutableStateOf("EPWA 171030Z 24012G24KT 210V280 9999 -SHRA FEW012 BKN025CB 12/09 Q1008 TEMPO 4000 TSRA") }
    SectionCard(loc("Dekoder METAR/TAF", "METAR/TAF decoder"), icon = Icons.Outlined.Translate) {
        OutlinedTextField(raw, { raw = it.uppercase() }, Modifier.fillMaxWidth(), textStyle = RawText, shape = RoundedCornerShape(12.dp), minLines = 3)
        Spacer(Modifier.size(10.dp))
        MetarDecoder.decode(raw).filter { it.text != "—" && it.text.isNotBlank() }.forEach { d ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(d.token, fontFamily = Mono, color = Fm.c.accent, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                Column { Text(d.text, color = Fm.c.ink, fontSize = 14.sp) }
            }
        }
    }
}
