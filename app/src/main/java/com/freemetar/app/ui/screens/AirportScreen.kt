package com.freemetar.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.freemetar.app.ui.components.RunwayWindDiagram
import com.freemetar.app.ui.components.SectionCard
import com.freemetar.app.ui.components.StatTile
import com.freemetar.app.ui.theme.Amber
import com.freemetar.app.ui.theme.CatIfr
import com.freemetar.app.ui.theme.CatVfr
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.Night
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val d by vm.detail.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val detail = d ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val isFav = detail.ident in settings.favorites

    Column(Modifier.fillMaxSize().background(Night)) {
        TopAppBar(
            title = {
                Column {
                    Text(detail.ident, fontFamily = Mono, fontWeight = FontWeight.Bold)
                    Text(detail.airport?.name ?: detail.weather?.metar?.name ?: "", fontSize = 12.sp, color = TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Wstecz") } },
            actions = {
                IconButton({ vm.refreshWeather(detail.ident) }) { Icon(Icons.Outlined.Refresh, "Odśwież") }
                IconButton({ vm.toggleFavorite(detail.ident) }) {
                    Icon(if (isFav) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Ulubione", tint = if (isFav) Amber else TextLo)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
        )
        if (detail.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.size(4.dp))
        PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = Night, edgePadding = 16.dp) {
            listOf("Pogoda", "Pasy", "Radio", "Info", "Okolica").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> weatherTab(detail, settings)
                1 -> runwaysTab(detail)
                2 -> radioTab(detail)
                3 -> infoTab(detail, settings)
                4 -> nearbyTab(detail, onOpen)
            }
        }
    }
}

// ---------------- POGODA ----------------

private fun androidx.compose.foundation.lazy.LazyListScope.weatherTab(d: AirportDetail, s: Settings) {
    val m = d.weather?.metar
    if (d.error != null) item { SectionCard { Text("Błąd: ${d.error}", color = CatIfr) } }
    if (m == null) {
        item { SectionCard { Text(if (d.loading) "Pobieranie METAR…" else "Brak METAR dla ${d.ident}. Lotnisko może nie nadawać depesz — sprawdź zakładkę Okolica.", color = TextLo) } }
    } else {
        item { WeatherHero(m, d, s) }
        if (s.showRawFirst) item { RawCard("METAR", m.raw) }
        item { Tiles(m, d, s) }
        item { Decoded(m.raw) }
        if (!s.showRawFirst) item { RawCard("METAR", m.raw) }
    }
    d.weather?.taf?.let { taf -> item { TafCard(taf.raw) } }
    val hist = d.weather?.history.orEmpty().drop(1)
    if (hist.isNotEmpty()) item {
        SectionCard("Historia (12 h)") {
            hist.forEach { h ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    Text(hhmm.format(Instant.ofEpochSecond(h.obsTimeEpoch)) + "Z", fontFamily = Mono, color = categoryColor(h.flightCategory), fontSize = 13.sp, modifier = Modifier.width(56.dp))
                    Text(h.raw.substringAfter("Z "), style = RawText.copy(fontSize = 12.sp), color = TextHi.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun WeatherHero(m: Metar, d: AirportDetail, s: Settings) {
    val cat = categoryColor(m.flightCategory)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(categoryText(m.flightCategory), color = cat, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Obserwacja ${hhmm.format(Instant.ofEpochSecond(m.obsTimeEpoch))}Z · ${ageMinutes(m.obsTimeEpoch)} temu", color = TextLo, fontSize = 13.sp)
            }
            CategoryBadge(m.flightCategory)
        }
        val best = Wind.analyze(d.runways, m.windDir, m.windKt, m.gustKt).firstOrNull()
        if (best != null) {
            Spacer(Modifier.size(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sky.copy(alpha = 0.12f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            Text("Wiatr słaby/zmienny — wybór pasu wg ATIS/ruchu.", color = TextLo, fontSize = 13.sp)
        }
    }
}

private fun categoryText(c: String?) = when (c) {
    "VFR" -> "VFR"; "MVFR" -> "Marginal VFR"; "IFR" -> "IFR"; "LIFR" -> "Low IFR"; else -> "Brak kategorii"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tiles(m: Metar, d: AirportDetail, s: Settings) {
    val elevFt = d.airport?.elevationFt?.toDouble() ?: m.elevationM?.let { Atmo.mToFt(it) }
    val ceiling = m.clouds.filter { it.cover in setOf("BKN", "OVC", "VV", "OVX") }.minOfOrNull { it.baseFt ?: Int.MAX_VALUE }?.takeIf { it != Int.MAX_VALUE }
    fun alt(ft: Double) = if (s.altUnit == AltUnit.FT) "${ft.roundToInt()} ft" else "${Atmo.ftToM(ft).roundToInt()} m"
    fun press(h: Double) = if (s.pressureUnit == PressureUnit.HPA) "${h.roundToInt()} hPa" else "${Atmo.hpaToInHg(h).fmt(2)} inHg"
    fun pressAlt(h: Double) = if (s.pressureUnit == PressureUnit.HPA) "${Atmo.hpaToInHg(h).fmt(2)} inHg" else "${h.roundToInt()} hPa"

    val tiles = buildList<Triple<String, String, String?>> {
        add(Triple("Wiatr", windShort(m) + if (m.windKt != null && m.windKt > 0) " kt" else "", m.windKt?.let { "${Atmo.ktToKmh(it.toDouble()).roundToInt()} km/h" + (m.windDir?.let { d -> " · z " + Geo.compass(d.toDouble()) } ?: "") }))
        add(Triple("Widzialność", visText(m.visibility), null))
        add(Triple("Temp / rosa", "${m.tempC?.roundToInt() ?: "—"}° / ${m.dewpointC?.roundToInt() ?: "—"}°", m.tempC?.let { "${Atmo.cToF(it).roundToInt()} °F" }))
        m.altimHpa?.let { add(Triple("QNH", press(it), pressAlt(it))) }
        add(Triple("Podstawa", ceiling?.let { alt(it.toDouble()) } ?: "Brak", if (ceiling != null) "pułap BKN/OVC" else "brak pułapu"))
        if (m.tempC != null && m.dewpointC != null) {
            add(Triple("Wilgotność", "${Atmo.relativeHumidity(m.tempC, m.dewpointC).roundToInt()}%", "oblodz. gaźnika: " + Atmo.carbIceRisk(m.tempC, m.dewpointC)))
        }
        if (elevFt != null && m.altimHpa != null && m.tempC != null) {
            val da = Atmo.densityAltitude(elevFt, m.altimHpa, m.tempC)
            add(Triple("Wys. gęstościowa", alt(da), "ciśnieniowa " + alt(Atmo.pressureAltitude(elevFt, m.altimHpa))))
            add(Triple("QFE", press(Atmo.qfe(m.altimHpa, elevFt)), "elewacja " + alt(elevFt)))
        }
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
        tiles.forEach { (l, v, sub) -> StatTile(l, v, sub, Modifier.weight(1f)) }
    }
    if (m.tempC != null && m.dewpointC != null && Atmo.isFreezingWithMoisture(m.tempC, m.dewpointC)) {
        Spacer(Modifier.size(10.dp))
        SectionCard { Text("⚠ Warunki sprzyjające oblodzeniu: temperatura bliska 0 °C przy dużej wilgotności.", color = Amber, fontSize = 13.sp) }
    }
    if (m.clouds.isNotEmpty()) {
        Spacer(Modifier.size(10.dp))
        SectionCard("Chmury") {
            m.clouds.forEach { c ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(c.cover, fontFamily = Mono, color = Sky, modifier = Modifier.width(48.dp))
                    Text(c.baseFt?.let { alt(it.toDouble()) + " AGL" } ?: "—", color = TextHi, modifier = Modifier.weight(1f))
                    c.type?.let { Text(it, color = if (it == "CB") CatIfr else Amber, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun visText(v: String?): String = when {
    v == null -> "—"
    v.endsWith("+") -> "≥ ${v.dropLast(1)} SM"
    v.toDoubleOrNull() != null -> "${v} SM (${Atmo.smToKm(v.toDouble()).fmt(1)} km)"
    else -> v
}

@Composable
private fun Decoded(raw: String) {
    SectionCard("Rozkodowany METAR") {
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
private fun RawCard(title: String, raw: String) {
    val clip = LocalClipboardManager.current
    SectionCard(title, trailing = {
        Icon(Icons.Outlined.ContentCopy, "Kopiuj", tint = TextLo, modifier = Modifier.size(18.dp).clickable { clip.setText(AnnotatedString(raw)) })
    }) {
        Text(raw, style = RawText, color = TextHi)
    }
}

@Composable
private fun TafCard(raw: String) {
    val clip = LocalClipboardManager.current
    SectionCard("TAF — prognoza", trailing = {
        Icon(Icons.Outlined.ContentCopy, "Kopiuj", tint = TextLo, modifier = Modifier.size(18.dp).clickable { clip.setText(AnnotatedString(raw)) })
    }) {
        MetarDecoder.splitTaf(raw).forEachIndexed { i, line ->
            val trend = MetarDecoder.decode(line).filter { it.category == Category.TREND || it.category == Category.TIME }.joinToString(" ") { it.text }
            Column(Modifier.padding(vertical = 4.dp).padding(start = if (i == 0) 0.dp else 12.dp)) {
                Text(line, style = RawText.copy(fontSize = 13.sp), color = if (i == 0) TextHi else TextHi.copy(alpha = 0.85f))
                if (trend.isNotBlank()) Text(trend, color = Amber, fontSize = 12.sp)
            }
        }
    }
}

// ---------------- PASY ----------------

private fun androidx.compose.foundation.lazy.LazyListScope.runwaysTab(d: AirportDetail) {
    val m = d.weather?.metar
    if (d.runways.isEmpty()) {
        item { SectionCard { Text("Brak danych o pasach w bazie OurAirports.", color = TextLo) } }
        return
    }
    item {
        SectionCard("Schemat i wiatr") {
            RunwayWindDiagram(d.runways, m?.windDir, m?.windKt, m?.windVariable == true)
            if (m != null) Text("Wiatr: ${windShort(m)} kt (kierunek rzeczywisty)", color = TextLo, fontSize = 12.sp)
        }
    }
    val analysis = Wind.analyze(d.runways, m?.windDir, m?.windKt, m?.gustKt)
    if (analysis.isNotEmpty()) item {
        SectionCard("Składowe wiatru") {
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
                Text("RWY ${rw.name}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextHi, modifier = Modifier.weight(1f))
                if (rw.lighted) Text("OŚWIETLONY", style = MaterialTheme.typography.labelSmall, color = Amber)
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Długość", rw.lengthFt?.let { "$it ft" } ?: "—", rw.lengthFt?.let { "${Atmo.ftToM(it.toDouble()).roundToInt()} m" }, Modifier.weight(1f))
                StatTile("Szerokość", rw.widthFt?.let { "$it ft" } ?: "—", rw.widthFt?.let { "${Atmo.ftToM(it.toDouble()).roundToInt()} m" }, Modifier.weight(1f))
                StatTile("Nawierzchnia", rw.surfaceLabel, null, Modifier.weight(1f))
            }
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

private fun androidx.compose.foundation.lazy.LazyListScope.radioTab(d: AirportDetail) {
    if (d.frequencies.isEmpty()) {
        item { SectionCard { Text("Brak częstotliwości w bazie. W sieciach VATSIM/IVAO sprawdź aktywnych kontrolerów w ich mapach.", color = TextLo) } }
    } else item {
        SectionCard("Częstotliwości lotniska") {
            d.frequencies.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
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
        SectionCard("Pomoce nawigacyjne w promieniu 40 NM") {
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

private fun androidx.compose.foundation.lazy.LazyListScope.infoTab(d: AirportDetail, s: Settings) {
    val a = d.airport
    if (a == null) {
        item { SectionCard { Text("Brak lotniska ${d.ident} w bazie OurAirports.", color = TextLo) } }
        return
    }
    item {
        SectionCard("Lotnisko") {
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
        SectionCard("Słońce dziś (UTC)") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Świt cyw.", t(sun.civilDawn), null, Modifier.weight(1f))
                StatTile("Wschód", t(sun.sunrise), null, Modifier.weight(1f), accent = Amber)
                StatTile("Zachód", t(sun.sunset), null, Modifier.weight(1f), accent = Amber)
                StatTile("Zmierzch", t(sun.civilDusk), null, Modifier.weight(1f))
            }
            Text("Lot nocny VFR liczy się od końca zmierzchu cywilnego.", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    item { Links(a.ident, a.lat, a.lon, a.wikipedia, a.homeLink) }
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
    fun open(url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    val links = buildList {
        add("SkyVector" to "https://skyvector.com/airport/$ident")
        add("OurAirports" to "https://ourairports.com/airports/$ident/")
        add("Windy" to "https://www.windy.com/$lat/$lon?$lat,$lon,11")
        add("AirNav Radar" to "https://www.airnavradar.com/data/airports/$ident")
        add("VATSIM Radar" to "https://vatsim-radar.com/airport/$ident")
        add("Mapy (OSM)" to "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=14/$lat/$lon")
        if (wiki.isNotBlank()) add("Wikipedia" to wiki)
        if (home.isNotBlank()) add("Strona lotniska" to home)
    }
    SectionCard("Zewnętrzne zasoby") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEach { (t, u) ->
                Text(t, color = Sky, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(PanelHi).clickable { open(u) }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
    }
}

// ---------------- OKOLICA ----------------

private fun androidx.compose.foundation.lazy.LazyListScope.nearbyTab(d: AirportDetail, onOpen: (String) -> Unit) {
    if (d.nearby.isEmpty()) {
        item { SectionCard { Text("Brak lotnisk w promieniu 60 NM.", color = TextLo) } }
        return
    }
    item { Text("LOTNISKA W PROMIENIU 60 NM", style = MaterialTheme.typography.labelSmall, color = TextLo) }
    items(d.nearby, key = { it.first.ident }) { (a, dist) ->
        val m = d.nearbyMetars[a.ident]
        val brg = d.airport?.let { Geo.bearing(it.lat, it.lon, a.lat, a.lon) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(com.freemetar.app.ui.theme.Panel).clickable { onOpen(a.ident) }.padding(14.dp),
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
                brg?.let { Text("%03d° %s".format(it.roundToInt(), Geo.compass(it)), fontFamily = Mono, color = TextLo, fontSize = 12.sp) }
            }
        }
    }
}
