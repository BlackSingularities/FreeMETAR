package com.freemetar.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.AirportDetail
import com.freemetar.app.MainViewModel
import com.freemetar.app.data.AltUnit
import com.freemetar.app.data.Frequency
import com.freemetar.app.data.Metar
import com.freemetar.app.data.PressureUnit
import com.freemetar.app.data.Settings
import com.freemetar.app.domain.Atmo
import com.freemetar.app.domain.Category
import com.freemetar.app.domain.Geo
import com.freemetar.app.domain.MetarDecoder
import com.freemetar.app.domain.Sun
import com.freemetar.app.domain.Wind
import com.freemetar.app.domain.fmt
import com.freemetar.app.ui.components.CategoryBadge
import com.freemetar.app.ui.components.EmptyState
import com.freemetar.app.ui.components.ListHeader
import com.freemetar.app.ui.components.RunwayWindDiagram
import com.freemetar.app.ui.components.SectionCard
import com.freemetar.app.ui.components.StatTile
import com.freemetar.app.ui.components.TrendChart
import com.freemetar.app.ui.components.isWide
import com.freemetar.app.ui.components.windowColumns
import com.freemetar.app.ui.theme.Amber
import com.freemetar.app.ui.theme.CatIfr
import com.freemetar.app.ui.theme.CatVfr
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.Night
import com.freemetar.app.ui.theme.Panel
import com.freemetar.app.ui.theme.PanelHi
import com.freemetar.app.ui.theme.RawText
import com.freemetar.app.ui.theme.Sky
import com.freemetar.app.ui.theme.TextHi
import com.freemetar.app.ui.theme.TextLo
import com.freemetar.app.ui.theme.categoryColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)

private data class AirportTab(val label: String, val icon: ImageVector)

private val airportTabs = listOf(
    AirportTab("Pogoda", Icons.Outlined.Cloud),
    AirportTab("Pasy", Icons.Outlined.FlightLand),
    AirportTab("Radio", Icons.Outlined.Radio),
    AirportTab("Info", Icons.Outlined.Info),
    AirportTab("Okolica", Icons.Outlined.NearMe),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val d by vm.detail.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val detail = d ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val isFav = detail.ident in settings.favorites
    val ctx = LocalContext.current
    val cols = windowColumns()

    Column(Modifier.fillMaxSize().background(Night)) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(detail.ident, fontFamily = Mono, fontWeight = FontWeight.Bold)
                        Text(detail.airport?.name ?: detail.weather?.metar?.name ?: "", fontSize = 12.sp, color = TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    detail.weather?.metar?.flightCategory?.let { Spacer(Modifier.width(10.dp)); CategoryBadge(it) }
                }
            },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Wstecz") } },
            actions = {
                detail.weather?.metar?.let { m ->
                    IconButton({ share(ctx, detail, m) }) { Icon(Icons.Outlined.Share, "Udostępnij") }
                }
                IconButton({ vm.refreshWeather(detail.ident) }) { Icon(Icons.Outlined.Refresh, "Odśwież") }
                IconButton({ vm.toggleFavorite(detail.ident) }) {
                    Icon(if (isFav) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Ulubione", tint = if (isFav) Amber else TextLo)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
        )
        if (detail.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.size(4.dp))
        PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = Night, edgePadding = 12.dp) {
            airportTabs.forEachIndexed { i, t ->
                Tab(
                    selected = tab == i, onClick = { tab = i },
                    text = { Text(t.label) },
                    icon = { Icon(t.icon, null, modifier = Modifier.size(20.dp)) },
                    selectedContentColor = Sky, unselectedContentColor = TextLo,
                )
            }
        }
        PullToRefreshBox(isRefreshing = detail.loading, onRefresh = { vm.refreshWeather(detail.ident) }, modifier = Modifier.fillMaxSize()) {
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(if (t == 4) cols.coerceAtMost(2) else cols),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (t) {
                        0 -> weatherTab(detail, settings)
                        1 -> runwaysTab(detail)
                        2 -> radioTab(detail)
                        3 -> infoTab(detail, settings)
                        4 -> nearbyTab(detail, onOpen)
                    }
                }
            }
        }
    }
}

private fun share(ctx: android.content.Context, d: AirportDetail, m: Metar) {
    val text = buildString {
        append("${d.ident} ${d.airport?.name.orEmpty()}\n\n")
        append("METAR: ${m.raw}\n")
        d.weather?.taf?.let { append("\nTAF: ${it.raw}\n") }
        append("\n— FreeMETAR")
    }
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Udostępnij pogodę"))
}

private val Full = StaggeredGridItemSpan.FullLine

// ---------------- POGODA ----------------

private fun LazyStaggeredGridScope.weatherTab(d: AirportDetail, s: Settings) {
    val m = d.weather?.metar
    if (d.error != null) item(span = Full) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, null, tint = CatIfr)
                Spacer(Modifier.width(10.dp))
                Text("Nie udało się pobrać pogody: ${d.error}", color = TextHi, fontSize = 13.sp)
            }
        }
    }
    if (m == null) {
        item(span = Full) {
            if (d.loading) SectionCard { Text("Pobieranie METAR…", color = TextLo) }
            else EmptyState(Icons.Outlined.CloudOff, "Brak METAR dla ${d.ident}", "To lotnisko może nie nadawać depesz. Sprawdź pogodę na pobliskich lotniskach w zakładce Okolica.")
        }
    } else {
        item(span = Full) { WeatherHero(m, d) }
        if (s.showRawFirst) item(span = Full) { RawCard("METAR", m.raw, Icons.AutoMirrored.Outlined.Article) }
        item(span = Full) { Tiles(m, d, s) }
        warnings(m).takeIf { it.isNotEmpty() }?.let { w -> item(span = Full) { Warnings(w) } }
        if (m.clouds.isNotEmpty()) item { Clouds(m, s) }
        item { Decoded(m.raw) }
        if (!s.showRawFirst) item { RawCard("METAR", m.raw, Icons.AutoMirrored.Outlined.Article) }
    }
    d.weather?.taf?.let { taf -> item { TafCard(taf.raw) } }
    val hist = d.weather?.history.orEmpty()
    if (hist.size >= 3) item(span = Full) { Trends(hist.reversed(), s) }
    if (hist.size > 1) item {
        SectionCard("Historia (12 h)", icon = Icons.Outlined.History) {
            hist.drop(1).forEach { h ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(categoryColor(h.flightCategory)))
                    Spacer(Modifier.width(8.dp))
                    Text(hhmm.format(Instant.ofEpochSecond(h.obsTimeEpoch)) + "Z", fontFamily = Mono, color = TextHi, fontSize = 13.sp, modifier = Modifier.width(52.dp))
                    Text(h.raw.substringAfter("Z "), style = RawText.copy(fontSize = 12.sp), color = TextHi.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun WeatherHero(m: Metar, d: AirportDetail) {
    val cat = categoryColor(m.flightCategory)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(cat.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(weatherIcon(m), null, tint = cat, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(categoryText(m.flightCategory), color = cat, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, tint = TextLo, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${hhmm.format(Instant.ofEpochSecond(m.obsTimeEpoch))}Z · ${ageMinutes(m.obsTimeEpoch)} temu", color = TextLo, fontSize = 13.sp)
                }
            }
        }
        val best = Wind.analyze(d.runways, m.windDir, m.windKt, m.gustKt).firstOrNull()
        if (best != null) {
            Spacer(Modifier.size(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sky.copy(alpha = 0.12f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.FlightLand, null, tint = Sky, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PREFEROWANY PAS", style = MaterialTheme.typography.labelSmall, color = Sky)
                    Text("RWY ${best.runwayEnd}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextHi)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Czołowy ${best.steady.headwind.roundToInt()} kt", color = TextHi, fontSize = 13.sp)
                    Text("Boczny ${abs(best.steady.crosswind).roundToInt()} kt ${best.steady.crossSide}", color = TextHi, fontSize = 13.sp)
                }
            }
        } else if (m.windVariable || (m.windKt ?: 0) < 3) {
            Spacer(Modifier.size(8.dp))
            Text("Wiatr słaby/zmienny — wybór pasu wg ATIS i ruchu.", color = TextLo, fontSize = 13.sp)
        }
    }
}

/** Ikona najbardziej znaczącego zjawiska. */
private fun weatherIcon(m: Metar): ImageVector {
    val wx = m.wx.orEmpty()
    return when {
        "TS" in wx || m.clouds.any { it.type == "CB" } -> Icons.Outlined.Thunderstorm
        "SN" in wx || "FZ" in wx || "PL" in wx || "GR" in wx -> Icons.Outlined.AcUnit
        "RA" in wx || "DZ" in wx -> Icons.Outlined.WaterDrop
        "FG" in wx || "BR" in wx || "HZ" in wx -> Icons.Outlined.Visibility
        m.clouds.any { it.cover in setOf("BKN", "OVC") } -> Icons.Outlined.Cloud
        else -> Icons.Outlined.WbSunny
    }
}

private fun categoryText(c: String?) = when (c) {
    "VFR" -> "VFR"; "MVFR" -> "Marginal VFR"; "IFR" -> "IFR"; "LIFR" -> "Low IFR"; else -> "Brak kategorii"
}

private data class TileData(val label: String, val value: String, val sub: String?, val icon: ImageVector, val accent: Color = TextHi)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tiles(m: Metar, d: AirportDetail, s: Settings) {
    val elevFt = d.airport?.elevationFt?.toDouble() ?: m.elevationM?.let { Atmo.mToFt(it) }
    val ceiling = ceilingFt(m)
    fun alt(ft: Double) = if (s.altUnit == AltUnit.FT) "${ft.roundToInt()} ft" else "${Atmo.ftToM(ft).roundToInt()} m"
    fun press(h: Double) = if (s.pressureUnit == PressureUnit.HPA) "${h.roundToInt()} hPa" else "${Atmo.hpaToInHg(h).fmt(2)} inHg"
    fun pressOther(h: Double) = if (s.pressureUnit == PressureUnit.HPA) "${Atmo.hpaToInHg(h).fmt(2)} inHg" else "${h.roundToInt()} hPa"

    val tiles = buildList {
        add(TileData("Wiatr", windShort(m) + if ((m.windKt ?: 0) > 0) " kt" else "",
            m.windKt?.let { "${Atmo.ktToKmh(it.toDouble()).roundToInt()} km/h" + (m.windDir?.let { dd -> " · z " + Geo.compass(dd.toDouble()) } ?: "") },
            Icons.Outlined.Air, if ((m.gustKt ?: m.windKt ?: 0) >= 25) Amber else TextHi))
        add(TileData("Widzialność", visText(m.visibility), null, Icons.Outlined.Visibility))
        add(TileData("Temp / rosa", "${m.tempC?.roundToInt() ?: "—"}° / ${m.dewpointC?.roundToInt() ?: "—"}°", m.tempC?.let { "${Atmo.cToF(it).roundToInt()} °F" }, Icons.Outlined.Thermostat))
        m.altimHpa?.let { add(TileData("QNH", press(it), pressOther(it), Icons.Outlined.Compress)) }
        add(TileData("Pułap", ceiling?.let { alt(it.toDouble()) } ?: "Brak", if (ceiling != null) "najniższe BKN/OVC" else "brak pułapu", Icons.Outlined.Cloud,
            if (ceiling != null && ceiling < 1000) CatIfr else TextHi))
        if (m.tempC != null && m.dewpointC != null) {
            add(TileData("Wilgotność", "${Atmo.relativeHumidity(m.tempC, m.dewpointC).roundToInt()}%", "oblodz. gaźnika: " + Atmo.carbIceRisk(m.tempC, m.dewpointC), Icons.Outlined.WaterDrop))
        }
        if (elevFt != null && m.altimHpa != null && m.tempC != null) {
            val da = Atmo.densityAltitude(elevFt, m.altimHpa, m.tempC)
            add(TileData("Wys. gęstościowa", alt(da), "ciśnieniowa " + alt(Atmo.pressureAltitude(elevFt, m.altimHpa)), Icons.Outlined.Height, if (da - elevFt > 2000) Amber else TextHi))
            add(TileData("QFE", press(Atmo.qfe(m.altimHpa, elevFt)), "elewacja " + alt(elevFt), Icons.Outlined.Landscape))
        }
    }
    val perRow = if (isWide()) 4 else 2
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = perRow) {
        tiles.forEach { t -> StatTile(t.label, t.value, t.sub, Modifier.weight(1f), accent = t.accent, icon = t.icon) }
        // Dopełnienie ostatniego rzędu, by kafelki miały równą szerokość
        repeat((perRow - tiles.size % perRow) % perRow) { Spacer(Modifier.weight(1f)) }
    }
}

private fun ceilingFt(m: Metar) =
    m.clouds.filter { it.cover in setOf("BKN", "OVC", "VV", "OVX") }.mapNotNull { it.baseFt }.minOrNull()

/** Ostrzeżenia wyciągnięte z obserwacji. */
private fun warnings(m: Metar): List<Pair<ImageVector, String>> = buildList {
    if (m.clouds.any { it.type == "CB" } || m.wx.orEmpty().contains("TS")) add(Icons.Outlined.Thunderstorm to "Burza lub cumulonimbus w rejonie lotniska")
    if (m.tempC != null && m.dewpointC != null && Atmo.isFreezingWithMoisture(m.tempC, m.dewpointC)) add(Icons.Outlined.AcUnit to "Warunki sprzyjające oblodzeniu: temperatura bliska 0 °C przy dużej wilgotności")
    if ((m.gustKt ?: 0) >= 25) add(Icons.Outlined.Air to "Silne porywy wiatru: ${m.gustKt} kt")
    if (m.wx.orEmpty().contains("FZ")) add(Icons.Outlined.AcUnit to "Opad marznący")
    if (m.tempC != null && m.dewpointC != null && m.tempC - m.dewpointC <= 1 && m.tempC > 0) add(Icons.Outlined.Visibility to "Mały rozrzut temperatury i punktu rosy — możliwa mgła")
}

@Composable
private fun Warnings(w: List<Pair<ImageVector, String>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Amber.copy(alpha = 0.12f)).padding(14.dp)) {
        w.forEach { (icon, text) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, color = TextHi, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Clouds(m: Metar, s: Settings) {
    SectionCard("Chmury", icon = Icons.Outlined.Layers) {
        m.clouds.sortedByDescending { it.baseFt ?: 0 }.forEach { c ->
            val frac = when (c.cover) { "FEW" -> 0.25f; "SCT" -> 0.5f; "BKN" -> 0.8f; else -> 1f }
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c.cover, fontFamily = Mono, color = Sky, modifier = Modifier.width(44.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(PanelHi)) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color(0xFF93C5FD)))
                }
                Spacer(Modifier.width(10.dp))
                Text(c.baseFt?.let { if (s.altUnit == AltUnit.FT) "$it ft" else "${Atmo.ftToM(it.toDouble()).roundToInt()} m" } ?: "—", color = TextHi, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.width(72.dp))
                c.type?.let { Text(it, color = if (it == "CB") CatIfr else Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun Trends(hist: List<Metar>, s: Settings) {
    val cols = if (isWide()) 3 else 1
    SectionCard("Trend z 12 godzin", icon = Icons.AutoMirrored.Outlined.ShowChart) {
        val charts: List<@Composable (Modifier) -> Unit> = listOf(
            { mod -> TrendChart("Temperatura", hist.mapNotNull { it.tempC }, "°", Color(0xFFFCA5A5), Icons.Outlined.Thermostat, mod) },
            { mod -> TrendChart("Wiatr", hist.mapNotNull { it.windKt?.toDouble() }, " kt", Sky, Icons.Outlined.Air, mod) },
            { mod ->
                TrendChart("QNH hPa", hist.mapNotNull { it.altimHpa }, "", Color(0xFFC4B5FD), Icons.Outlined.Compress, mod)
            },
        )
        charts.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { it(Modifier.weight(1f)) }
            }
        }
    }
}

private fun visText(v: String?): String = when {
    v == null -> "—"
    v.endsWith("+") -> "≥ ${v.dropLast(1)} SM"
    v.toDoubleOrNull() != null -> "$v SM (${Atmo.smToKm(v.toDouble()).fmt(1)} km)"
    else -> v
}

@Composable
private fun Decoded(raw: String) {
    SectionCard("Rozkodowany METAR", icon = Icons.Outlined.Translate) {
        MetarDecoder.decode(raw).filter { it.text != "—" && it.text.isNotBlank() }.forEach { item ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Text(
                    item.token,
                    fontFamily = Mono, fontSize = 12.sp,
                    color = categoryTint(item.category),
                    modifier = Modifier.width(96.dp).clip(RoundedCornerShape(6.dp)).background(PanelHi).padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                Text(item.text, color = TextHi, fontSize = 14.sp)
            }
        }
    }
}

private fun categoryTint(c: Category): Color = when (c) {
    Category.WIND -> Sky
    Category.VISIBILITY, Category.RVR -> CatVfr
    Category.WEATHER -> Amber
    Category.CLOUDS -> Color(0xFF93C5FD)
    Category.TEMP -> Color(0xFFFCA5A5)
    Category.PRESSURE -> Color(0xFFC4B5FD)
    Category.TREND -> Amber
    else -> TextLo
}

@Composable
private fun CopyIcon(text: String) {
    val clip = LocalClipboardManager.current
    Icon(Icons.Outlined.ContentCopy, "Kopiuj", tint = TextLo, modifier = Modifier.size(18.dp).clickable { clip.setText(AnnotatedString(text)) })
}

@Composable
private fun RawCard(title: String, raw: String, icon: ImageVector) {
    SectionCard(title, icon = icon, trailing = { CopyIcon(raw) }) {
        Text(raw, style = RawText, color = TextHi)
    }
}

@Composable
private fun TafCard(raw: String) {
    SectionCard("TAF — prognoza", icon = Icons.Outlined.Schedule, trailing = { CopyIcon(raw) }) {
        MetarDecoder.splitTaf(raw).forEachIndexed { i, line ->
            val trend = MetarDecoder.decode(line).filter { it.category == Category.TREND || it.category == Category.TIME }.joinToString(" ") { it.text }
            Row(Modifier.padding(vertical = 4.dp)) {
                if (i > 0) Box(Modifier.padding(top = 6.dp, end = 8.dp).size(6.dp).clip(CircleShape).background(Amber))
                Column {
                    Text(line, style = RawText.copy(fontSize = 13.sp), color = if (i == 0) TextHi else TextHi.copy(alpha = 0.85f))
                    if (trend.isNotBlank()) Text(trend, color = Amber, fontSize = 12.sp)
                }
            }
        }
    }
}

// ---------------- PASY ----------------

private fun LazyStaggeredGridScope.runwaysTab(d: AirportDetail) {
    val m = d.weather?.metar
    if (d.runways.isEmpty()) {
        item(span = Full) { EmptyState(Icons.Outlined.FlightLand, "Brak danych o pasach", "OurAirports nie ma informacji o pasach tego lotniska.") }
        return
    }
    item {
        SectionCard("Schemat i wiatr", icon = Icons.Outlined.Explore) {
            RunwayWindDiagram(d.runways, m?.windDir, m?.windKt, m?.windVariable == true)
            if (m != null) Text("Wiatr: ${windShort(m)} kt (kierunek rzeczywisty)", color = TextLo, fontSize = 12.sp)
        }
    }
    val analysis = Wind.analyze(d.runways, m?.windDir, m?.windKt, m?.gustKt)
    if (analysis.isNotEmpty()) item {
        SectionCard("Składowe wiatru", icon = Icons.Outlined.Air) {
            Row(Modifier.padding(bottom = 6.dp)) {
                listOf("Pas" to 60.dp, "Kurs" to 56.dp).forEach { (t, w) -> Text(t, color = TextLo, fontSize = 12.sp, modifier = Modifier.width(w)) }
                Text("Czołowy", color = TextLo, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Boczny", color = TextLo, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
            analysis.forEachIndexed { i, rw ->
                val head = rw.steady.headwind
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (i == 0) Sky.copy(alpha = 0.12f) else Color.Transparent).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(rw.runwayEnd, fontFamily = Mono, fontWeight = FontWeight.Bold, color = if (i == 0) Sky else TextHi, modifier = Modifier.width(60.dp).padding(start = 4.dp))
                    Text("%03d°".format(rw.heading.roundToInt()), fontFamily = Mono, color = TextLo, fontSize = 13.sp, modifier = Modifier.width(56.dp))
                    Text(
                        (if (head >= 0) "↓ ${head.roundToInt()}" else "↑ ${(-head).roundToInt()} tylny") + (rw.gust?.let { " (${it.headwind.roundToInt()})" } ?: ""),
                        color = if (head < 0) CatIfr else CatVfr, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                    val x = abs(rw.steady.crosswind)
                    Text(
                        "${if (rw.steady.crosswind >= 0) "→" else "←"} ${x.roundToInt()}" + (rw.gust?.let { " (${abs(it.crosswind).roundToInt()})" } ?: ""),
                        color = when { x >= 20 -> CatIfr; x >= 12 -> Amber; else -> TextHi }, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                }
            }
            Text("W nawiasach wartości w porywach. → wiatr z prawej, ← z lewej.", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    items(d.runways) { rw ->
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FlightLand, null, tint = Sky, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("RWY ${rw.name}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextHi, modifier = Modifier.weight(1f))
                if (rw.lighted) {
                    Icon(Icons.Outlined.Lightbulb, null, tint = Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("OŚWIETLONY", style = MaterialTheme.typography.labelSmall, color = Amber)
                }
            }
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Długość", rw.lengthFt?.let { "$it ft" } ?: "—", rw.lengthFt?.let { "${Atmo.ftToM(it.toDouble()).roundToInt()} m" }, Modifier.weight(1f), icon = Icons.Outlined.Straighten)
                StatTile("Szer.", rw.widthFt?.let { "$it ft" } ?: "—", rw.widthFt?.let { "${Atmo.ftToM(it.toDouble()).roundToInt()} m" }, Modifier.weight(1f), icon = Icons.Outlined.SpaceBar)
            }
            Spacer(Modifier.size(10.dp))
            StatTile("Nawierzchnia", rw.surfaceLabel, null, Modifier.fillMaxWidth(), icon = Icons.Outlined.Layers)
            listOf(rw.le, rw.he).filter { it.ident.isNotBlank() }.forEach { e ->
                val extra = listOfNotNull(
                    e.headingTrue?.let { "kurs ${it.roundToInt()}°T" },
                    e.elevationFt?.let { "próg $it ft" },
                    e.displacedFt?.takeIf { it > 0 }?.let { "przesunięty próg $it ft" },
                ).joinToString(" · ")
                if (extra.isNotBlank()) Text("${e.ident}: $extra", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

// ---------------- RADIO ----------------

private fun freqIcon(f: Frequency): ImageVector = when (f.type.uppercase()) {
    "ATIS" -> Icons.AutoMirrored.Outlined.Article
    "GND", "DEL", "CLD" -> Icons.Outlined.Landscape
    "TWR" -> Icons.Outlined.Visibility
    "APP", "DEP", "CTR", "CNTR" -> Icons.Outlined.Explore
    else -> Icons.Outlined.Radio
}

private fun LazyStaggeredGridScope.radioTab(d: AirportDetail) {
    if (d.frequencies.isEmpty()) {
        item(span = Full) { EmptyState(Icons.Outlined.Radio, "Brak częstotliwości w bazie", "W sieciach VATSIM/IVAO sprawdź aktywnych kontrolerów na ich mapach.") }
    } else item {
        SectionCard("Częstotliwości lotniska", icon = Icons.Outlined.Radio) {
            d.frequencies.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(PanelHi), contentAlignment = Alignment.Center) {
                        Icon(freqIcon(f), null, tint = Sky, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.label, color = TextHi, fontWeight = FontWeight.SemiBold)
                        if (f.description.isNotBlank()) Text(f.description, color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("%.3f".format(f.mhz), fontFamily = Mono, fontSize = 20.sp, color = Sky, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (d.navaids.isNotEmpty()) item {
        SectionCard("Pomoce nawigacyjne · 40 NM", icon = Icons.Outlined.Explore) {
            d.navaids.forEach { (n, dist) ->
                val brg = d.airport?.let { Geo.bearing(it.lat, it.lon, n.lat, n.lon) }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.ident, fontFamily = Mono, fontWeight = FontWeight.Bold, color = TextHi, modifier = Modifier.width(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${n.type} · ${n.name}", color = TextHi, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${dist.fmt(1)} NM" + (brg?.let { " · ${"%03d".format(it.roundToInt())}°" } ?: ""), color = TextLo, fontSize = 12.sp)
                    }
                    Text(n.frequencyLabel, fontFamily = Mono, color = Sky)
                }
            }
        }
    }
}

// ---------------- INFO ----------------

private fun LazyStaggeredGridScope.infoTab(d: AirportDetail, s: Settings) {
    val a = d.airport
    if (a == null) {
        item(span = Full) { EmptyState(Icons.Outlined.Info, "Brak ${d.ident} w bazie OurAirports") }
        return
    }
    item {
        SectionCard("Lotnisko", icon = Icons.Outlined.Info) {
            InfoRow("Nazwa", a.name)
            InfoRow("Typ", a.typeLabel + if (a.scheduled) " · ruch rozkładowy" else "")
            InfoRow("ICAO / IATA", a.ident + if (a.iata.isNotBlank()) " / ${a.iata}" else "")
            InfoRow("Miejscowość", listOf(a.municipality, a.region, a.country).filter { it.isNotBlank() }.joinToString(", "))
            InfoRow("Współrzędne", Geo.formatLatLon(a.lat, a.lon))
            InfoRow("Elewacja", a.elevationFt?.let { if (s.altUnit == AltUnit.FT) "$it ft" else "${Atmo.ftToM(it.toDouble()).roundToInt()} m" } ?: "—")
        }
    }
    item {
        val today = LocalDate.now(ZoneOffset.UTC)
        val sun = Sun.times(today, a.lat, a.lon)
        fun t(z: java.time.ZonedDateTime?) = z?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "—"
        SectionCard("Słońce dziś (UTC)", icon = Icons.Outlined.WbSunny) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Świt", t(sun.civilDawn), "cywilny", Modifier.weight(1f), icon = Icons.Outlined.WbTwilight)
                StatTile("Wschód", t(sun.sunrise), null, Modifier.weight(1f), accent = Amber, icon = Icons.Outlined.WbSunny)
            }
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Zachód", t(sun.sunset), null, Modifier.weight(1f), accent = Amber, icon = Icons.Outlined.WbSunny)
                StatTile("Zmierzch", t(sun.civilDusk), "cywilny", Modifier.weight(1f), icon = Icons.Outlined.WbTwilight)
            }
            Text("Lot nocny VFR liczy się od końca zmierzchu cywilnego.", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
    item(span = Full) { Links(a.ident, a.lat, a.lon, a.wikipedia, a.homeLink) }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = TextLo, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(value, color = TextHi, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Links(ident: String, lat: Double, lon: Double, wiki: String, home: String) {
    val ctx = LocalContext.current
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    val links = buildList {
        add("SkyVector" to "https://skyvector.com/airport/$ident")
        add("OurAirports" to "https://ourairports.com/airports/$ident/")
        add("Windy" to "https://www.windy.com/$lat/$lon?$lat,$lon,11")
        add("AirNav Radar" to "https://www.airnavradar.com/data/airports/$ident")
        add("VATSIM Radar" to "https://vatsim-radar.com/airport/$ident")
        add("Mapa OSM" to "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=14/$lat/$lon")
        if (wiki.isNotBlank()) add("Wikipedia" to wiki)
        if (home.isNotBlank()) add("Strona lotniska" to home)
    }
    SectionCard("Zewnętrzne zasoby", icon = Icons.Outlined.Link) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEach { (t, u) ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(PanelHi).clickable { open(u) }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Link, null, tint = Sky, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t, color = Sky, fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------------- OKOLICA ----------------

private fun LazyStaggeredGridScope.nearbyTab(d: AirportDetail, onOpen: (String) -> Unit) {
    if (d.nearby.isEmpty()) {
        item(span = Full) { EmptyState(Icons.Outlined.NearMe, "Brak lotnisk w promieniu 60 NM") }
        return
    }
    item(span = Full) { ListHeader("Lotniska w promieniu 60 NM", Icons.Outlined.NearMe) }
    items(d.nearby, key = { it.first.ident }) { (a, dist) ->
        val m = d.nearbyMetars[a.ident]
        val brg = d.airport?.let { Geo.bearing(it.lat, it.lon, a.lat, a.lon) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable { onOpen(a.ident) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.ident, fontFamily = Mono, fontWeight = FontWeight.Bold, color = TextHi)
                    if (m != null) { Spacer(Modifier.width(8.dp)); CategoryBadge(m.flightCategory) }
                }
                Text(a.name, color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m != null) Text("${windShort(m)} · ${m.tempC?.roundToInt() ?: "—"}° · Q${m.altimHpa?.roundToInt() ?: "—"}", fontFamily = Mono, color = TextHi, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${dist.fmt(0)} NM", fontFamily = Mono, color = Sky)
                brg?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Strzałka w kierunku lotniska; ikona NearMe domyślnie wskazuje NE (45°)
                        Icon(Icons.Outlined.NearMe, null, tint = TextLo, modifier = Modifier.size(13.dp).rotate(it.toFloat() - 45f))
                        Spacer(Modifier.width(4.dp))
                        Text("%03d° %s".format(it.roundToInt(), Geo.compass(it)), fontFamily = Mono, color = TextLo, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
