package com.freemetar.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.AirportDetail
import com.freemetar.app.MainViewModel
import com.freemetar.app.data.AltUnit
import com.freemetar.app.data.Frequency
import com.freemetar.app.data.Metar
import com.freemetar.app.data.PressureUnit
import com.freemetar.app.data.Settings
import com.freemetar.app.domain.Atis
import com.freemetar.app.domain.Atmo
import com.freemetar.app.domain.Geo
import com.freemetar.app.domain.MetarDecoder
import com.freemetar.app.domain.Sun
import com.freemetar.app.domain.TafAnalyzer
import com.freemetar.app.domain.Wind
import com.freemetar.app.domain.fmt
import com.freemetar.app.ui.components.CategoryTag
import com.freemetar.app.ui.components.EmptyState
import com.freemetar.app.ui.components.Fact
import com.freemetar.app.ui.components.FactGrid
import com.freemetar.app.ui.components.InteractiveMetar
import com.freemetar.app.ui.components.Pill
import com.freemetar.app.ui.components.Rule
import com.freemetar.app.ui.components.RunwayWindDiagram
import com.freemetar.app.ui.components.Section
import com.freemetar.app.ui.components.TrendChart
import com.freemetar.app.ui.components.isWide
import com.freemetar.app.ui.components.weatherIcon
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.RawText
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)

/** Rozdziały briefingu w kolejności czytania. */
private enum class Part(val label: String) { WEATHER("Pogoda"), HAZARDS("Zagrożenia"), TAF("Prognoza"), ALOFT("Wysokości"), RUNWAYS("Pasy"), RADIO("Radio"), AIRPORT("Lotnisko"), NEARBY("Okolica") }

@Composable
fun BriefingScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val d by vm.detail.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val detail = d ?: return
    val c = Fm.c
    val wide = isWide()

    Column(Modifier.fillMaxSize().background(c.paper)) {
        TopBar(detail, settings, vm, onBack)
        if (detail.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = c.accent, trackColor = c.paper)
        else Spacer(Modifier.height(2.dp))

        if (wide) {
            // Tablet / poziomo: pogoda po lewej, reszta briefingu po prawej
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { WeatherPart(detail, settings) }
                    item { HazardsPart(detail) }
                    item { TafPart(detail) }
                    item { AloftPart(detail, settings) }
                }
                Box(Modifier.width(1.dp).fillMaxSize().background(c.rule))
                LazyColumn(Modifier.weight(1f).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { RunwaysPart(detail) }
                    item { RadioPart(detail, vm) }
                    item { AirportPart(detail, settings) }
                    item { NearbyPart(detail, onOpen) }
                }
            }
        } else {
            val list = rememberLazyListState()
            JumpBar(list)
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = Part.WEATHER) { WeatherPart(detail, settings) }
                item(key = Part.HAZARDS) { HazardsPart(detail) }
                item(key = Part.TAF) { TafPart(detail) }
                item(key = Part.ALOFT) { AloftPart(detail, settings) }
                item(key = Part.RUNWAYS) { RunwaysPart(detail) }
                item(key = Part.RADIO) { RadioPart(detail, vm) }
                item(key = Part.AIRPORT) { AirportPart(detail, settings) }
                item(key = Part.NEARBY) { NearbyPart(detail, onOpen) }
            }
        }
    }
}

@Composable
private fun TopBar(d: AirportDetail, s: Settings, vm: MainViewModel, onBack: () -> Unit) {
    val c = Fm.c
    val ctx = LocalContext.current
    val fav = d.ident in s.favorites
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Wróć", tint = c.ink) }
        Column(Modifier.weight(1f)) {
            Text(d.ident, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.ink, lineHeight = 28.sp)
            Text(
                listOfNotNull(d.airport?.name ?: d.weather?.metar?.name, d.airport?.municipality?.takeIf { it.isNotBlank() }).joinToString(", "),
                style = MaterialTheme.typography.bodySmall, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        d.weather?.metar?.let { m -> IconButton({ share(ctx, d, m) }) { Icon(Icons.Outlined.Share, "Udostępnij pogodę", tint = c.ink) } }
        IconButton({ vm.refreshWeather(d.ident) }) { Icon(Icons.Outlined.Refresh, "Odśwież", tint = c.ink) }
        IconButton({ vm.toggleFavorite(d.ident) }) {
            Icon(if (fav) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (fav) "Usuń z ulubionych" else "Dodaj do ulubionych", tint = if (fav) c.accent else c.ink)
        }
    }
}

/** Pasek skoków do rozdziałów; podświetla rozdział widoczny na górze listy. */
@Composable
private fun JumpBar(list: LazyListState) {
    val c = Fm.c
    val scope = rememberCoroutineScope()
    val current by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? Part ?: Part.WEATHER } }
    Column {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Part.entries.forEachIndexed { i, p ->
                val on = p == current
                Column(
                    Modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Tab) { scope.launch { list.animateScrollToItem(i) } }.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(p.label, style = MaterialTheme.typography.labelLarge, color = if (on) c.ink else c.ink2, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                    Box(Modifier.height(3.dp).width(28.dp).clip(RoundedCornerShape(2.dp)).background(if (on) c.accent else Color.Transparent))
                }
            }
        }
        Rule()
    }
}

private fun share(ctx: Context, d: AirportDetail, m: Metar) {
    val text = buildString {
        append("${d.ident} ${d.airport?.name.orEmpty()}\n\nMETAR ${m.raw}\n")
        d.weather?.taf?.let { append("\n${it.raw}\n") }
        append("\nOpenAirBook")
    }
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Udostępnij pogodę"))
}

// ---------------- POGODA ----------------

@Composable
private fun WeatherPart(d: AirportDetail, s: Settings) {
    val c = Fm.c
    val m = d.weather?.metar
    Column {
        if (d.error != null) {
            Row(Modifier.fillMaxWidth().background(c.ifr.copy(alpha = 0.1f)).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, null, tint = c.ifr)
                Spacer(Modifier.width(10.dp))
                Text("Nie pobrano pogody (${d.error}). Pociągnij odśwież lub sprawdź internet.", style = MaterialTheme.typography.bodySmall, color = c.ink)
            }
        }
        if (m == null) {
            if (!d.loading) EmptyState(Icons.Outlined.CloudOff, "Brak METAR dla ${d.ident}", "To lotnisko nie nadaje depesz pogodowych. Pogodę z lotnisk w pobliżu znajdziesz w rozdziale Okolica.")
            else Box(Modifier.fillMaxWidth().padding(28.dp)) { Text("Pobieranie pogody…", color = c.ink2) }
            Rule()
            return@Column
        }
        Section("Pogoda teraz", weatherIcon(m)) {
            Verdict(m)
            Spacer(Modifier.size(14.dp))
            InteractiveMetar(m.raw)
            Warnings(m)
            Spacer(Modifier.size(14.dp))
            WeatherFacts(m, d, s)
            Spacer(Modifier.size(14.dp))
            AtisPlayer(d, m)
        }
        if (m.clouds.isNotEmpty()) Section("Chmury", Icons.Outlined.Layers) { Clouds(m, s) }
        val hist = d.weather?.history.orEmpty()
        if (hist.size >= 3) Section("Ostatnie 12 godzin", Icons.AutoMirrored.Outlined.ShowChart) { Trends(hist.reversed()) }
        if (hist.size > 1) Section("Wcześniejsze depesze", Icons.Outlined.History) { History(hist.drop(1)) }
    }
}

@Composable
private fun Verdict(m: Metar) {
    val c = Fm.c
    val col = c.category(m.flightCategory)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(col), contentAlignment = Alignment.Center) {
            Text(m.flightCategory ?: "—", color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = if ((m.flightCategory?.length ?: 1) > 3) 12.sp else 14.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(categoryText(m.flightCategory), style = MaterialTheme.typography.headlineMedium, color = col)
            val stale = isStale(m.obsTimeEpoch)
            Text(
                "Obserwacja ${hhmm.format(Instant.ofEpochSecond(m.obsTimeEpoch))}Z, ${ageMinutes(m.obsTimeEpoch)} temu" + if (stale) ". Depesza może być nieaktualna." else "",
                style = MaterialTheme.typography.bodySmall, color = if (stale) c.caution else c.ink2,
            )
        }
    }
}

private fun categoryText(c: String?) = when (c) {
    "VFR" -> "Warunki VFR"
    "MVFR" -> "Marginalne VFR"
    "IFR" -> "Warunki IFR"
    "LIFR" -> "Niskie IFR"
    else -> "Kategoria nieznana"
}

/** Ostrzeżenia wyciągnięte z obserwacji. */
private fun warnings(m: Metar): List<String> = buildList {
    if (m.clouds.any { it.type == "CB" } || m.wx.orEmpty().contains("TS")) add("Burza lub cumulonimbus w rejonie lotniska.")
    if (m.wx.orEmpty().contains("FZ")) add("Opad marznący — ryzyko oblodzenia płatowca.")
    else if (m.tempC != null && m.dewpointC != null && Atmo.isFreezingWithMoisture(m.tempC, m.dewpointC)) add("Temperatura bliska 0 °C przy dużej wilgotności — możliwe oblodzenie.")
    if ((m.gustKt ?: 0) >= 25) add("Silne porywy wiatru do ${m.gustKt} kt.")
    if (m.tempC != null && m.dewpointC != null && m.tempC - m.dewpointC <= 1 && m.tempC > 0 && !m.wx.orEmpty().contains("FG")) add("Temperatura równa punktowi rosy — możliwa mgła.")
}

@Composable
private fun Warnings(m: Metar) {
    val w = warnings(m)
    if (w.isEmpty()) return
    val c = Fm.c
    Spacer(Modifier.size(12.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).border(1.5.dp, c.ifr, RoundedCornerShape(6.dp)).padding(12.dp)) {
        w.forEach { text ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WarningAmber, null, tint = c.ifr, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink)
            }
        }
    }
}

private fun ceilingFt(m: Metar) = m.clouds.filter { it.cover in setOf("BKN", "OVC", "VV", "OVX") }.mapNotNull { it.baseFt }.minOrNull()

private fun visText(v: String?): String = when {
    v == null -> "—"
    v.endsWith("+") -> "10 km +"
    v.toDoubleOrNull() != null -> Atmo.smToKm(v.toDouble()).let { if (it >= 10) "10 km +" else "${it.fmt(1)} km" }
    else -> v
}

@Composable
private fun WeatherFacts(m: Metar, d: AirportDetail, s: Settings) {
    val c = Fm.c
    val elevFt = d.airport?.elevationFt?.toDouble() ?: m.elevationM?.let { Atmo.mToFt(it) }
    fun alt(ft: Double) = if (s.altUnit == AltUnit.FT) "${ft.roundToInt()} ft" else "${Atmo.ftToM(ft).roundToInt()} m"
    fun press(h: Double) = if (s.pressureUnit == PressureUnit.HPA) "${h.roundToInt()} hPa" else "${Atmo.hpaToInHg(h).fmt(2)} inHg"
    fun pressOther(h: Double) = if (s.pressureUnit == PressureUnit.HPA) "${Atmo.hpaToInHg(h).fmt(2)} inHg" else "${h.roundToInt()} hPa"
    val best = Wind.analyze(d.runways, m.windDir, m.windKt, m.gustKt).firstOrNull()
    val ceiling = ceilingFt(m)

    val facts = buildList {
        add(Fact("Pas wg wiatru", best?.let { "RWY ${it.runwayEnd}" } ?: "dowolny", accent = c.accent,
            sub = best?.let { "czołowy ${it.steady.headwind.roundToInt()}, boczny ${abs(it.steady.crosswind).roundToInt()} kt" } ?: "wiatr słaby lub zmienny"))
        add(Fact("Wiatr", windShort(m) + if ((m.windKt ?: 0) > 0) " kt" else "",
            accent = if ((m.gustKt ?: m.windKt ?: 0) >= 25) c.caution else null,
            sub = m.windKt?.takeIf { it > 0 }?.let { "${Atmo.ktToKmh(it.toDouble()).roundToInt()} km/h" + (m.windDir?.let { dd -> ", z " + Geo.compass(dd.toDouble()) } ?: "") }))
        add(Fact("Widzialność", visText(m.visibility)))
        add(Fact("Pułap", ceiling?.let { alt(it.toDouble()) } ?: "brak", accent = if (ceiling != null && ceiling < 1000) c.ifr else null,
            sub = if (ceiling == null) "bez warstw BKN/OVC" else null))
        m.altimHpa?.let { add(Fact("QNH", press(it), sub = pressOther(it))) }
        add(Fact("Temperatura / rosa", "${m.tempC?.roundToInt() ?: "—"}° / ${m.dewpointC?.roundToInt() ?: "—"}°",
            sub = if (m.tempC != null && m.dewpointC != null) "wilgotność ${Atmo.relativeHumidity(m.tempC, m.dewpointC).roundToInt()}%" else null))
        if (elevFt != null && m.altimHpa != null && m.tempC != null) {
            val da = Atmo.densityAltitude(elevFt, m.altimHpa, m.tempC)
            add(Fact("Wys. gęstościowa", alt(da), accent = if (da - elevFt > 2000) c.caution else null, sub = "ciśnieniowa ${alt(Atmo.pressureAltitude(elevFt, m.altimHpa))}"))
            add(Fact("QFE", press(Atmo.qfe(m.altimHpa, elevFt)), sub = "elewacja ${alt(elevFt)}"))
        }
        if (m.tempC != null && m.dewpointC != null) add(Fact("Oblodzenie gaźnika", Atmo.carbIceRisk(m.tempC, m.dewpointC).lowercase()))
    }
    FactGrid(facts, columns = 2)
}

/** Odczyt komunikatu w stylu ATIS przez syntezator mowy telefonu (po angielsku). */
@Composable
private fun AtisPlayer(d: AirportDetail, m: Metar) {
    val c = Fm.c
    val ctx = LocalContext.current
    val name = remember(d.airport?.name) { d.airport?.name?.removeSuffix(" Airport")?.removeSuffix(" International") ?: d.ident }
    val text = remember(m.raw, d.runways) { Atis.text(name, m, d.runways) }
    var speaking by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(ctx.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { speaking = true }
            override fun onDone(id: String?) { speaking = false }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { speaking = false }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.card).border(1.dp, c.rule, RoundedCornerShape(6.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent).clickable(enabled = ready, role = Role.Button) {
                if (speaking) {
                    tts.stop(); speaking = false
                } else {
                    tts.language = Locale.US
                    tts.setSpeechRate(0.92f)
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "atis")
                }
            }, contentAlignment = Alignment.Center) {
                Icon(if (speaking) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp, if (speaking) "Zatrzymaj" else "Odsłuchaj ATIS", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("ATIS, informacja ${Atis.letter(m)}", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(if (ready) "Komunikat złożony z METAR, czytany po angielsku" else "Syntezator mowy niedostępny", style = MaterialTheme.typography.bodySmall, color = c.ink2)
            }
            TextButton({ showText = !showText }) { Text(if (showText) "Ukryj" else "Tekst") }
        }
        if (showText) {
            Spacer(Modifier.size(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = c.ink)
        }
    }
}

@Composable
private fun Clouds(m: Metar, s: Settings) {
    val c = Fm.c
    // Warstwy od najwyższej, jak na przekroju atmosfery
    m.clouds.sortedByDescending { it.baseFt ?: 0 }.forEach { cl ->
        val frac = when (cl.cover) { "FEW" -> 0.25f; "SCT" -> 0.5f; "BKN" -> 0.8f; else -> 1f }
        val word = when (cl.cover) { "FEW" -> "nieliczne"; "SCT" -> "rozproszone"; "BKN" -> "poprzerywane"; "OVC" -> "całkowite"; "VV" -> "niebo niewidoczne"; else -> cl.cover }
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(cl.baseFt?.let { if (s.altUnit == AltUnit.FT) "$it ft" else "${Atmo.ftToM(it.toDouble()).roundToInt()} m" } ?: "—",
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink, modifier = Modifier.width(78.dp))
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.terrain)) {
                Box(Modifier.fillMaxWidth(frac).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (cl.type == "CB") c.ifr else c.blue.copy(alpha = 0.7f)))
            }
            Spacer(Modifier.width(10.dp))
            Text(word + when (cl.type) { "CB" -> ", CB"; "TCU" -> ", TCU"; else -> "" }, style = MaterialTheme.typography.bodySmall,
                color = if (cl.type == "CB") c.ifr else c.ink2, modifier = Modifier.width(118.dp))
        }
    }
}

@Composable
private fun Trends(hist: List<Metar>) {
    val c = Fm.c
    val charts = listOf<@Composable (Modifier) -> Unit>(
        { mod -> TrendChart("Temperatura °C", hist.mapNotNull { it.tempC }, "°", c.ifr, mod) },
        { mod -> TrendChart("Wiatr kt", hist.mapNotNull { it.windKt?.toDouble() }, " kt", c.blue, mod) },
        { mod -> TrendChart("QNH hPa", hist.mapNotNull { it.altimHpa }, "", c.accent, mod) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { charts.forEach { it(Modifier.fillMaxWidth()) } }
}

@Composable
private fun History(hist: List<Metar>) {
    val c = Fm.c
    hist.forEach { h ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(60.dp)) {
                Text(hhmm.format(Instant.ofEpochSecond(h.obsTimeEpoch)) + "Z", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
                CategoryTag(h.flightCategory, Modifier.padding(top = 3.dp))
            }
            Text(h.raw.substringAfter("Z "), style = RawText.copy(fontSize = 12.sp, lineHeight = 18.sp), color = c.ink2)
        }
    }
}

// ---------------- TAF ----------------

@Composable
private fun TafPart(d: AirportDetail) {
    val c = Fm.c
    val taf = d.weather?.taf
    Section("Prognoza TAF", Icons.Outlined.Schedule, trailing = { taf?.let { CopyButton(it.raw) } }) {
        if (taf == null) {
            Text(if (d.loading) "Pobieranie prognozy…" else "To lotnisko nie wydaje prognozy TAF.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            return@Section
        }
        val periods = remember(taf.raw) { TafAnalyzer.periods(taf.raw) }
        periods.forEachIndexed { i, p ->
            if (i > 0) Rule(Modifier.padding(vertical = 2.dp))
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryTag(p.category)
                    Spacer(Modifier.width(10.dp))
                    Text(p.title, style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                }
                val details = listOfNotNull(
                    p.windText?.let { "wiatr $it" },
                    p.visM?.let { if (it >= 9999) "widzialność 10 km +" else if (it >= 5000) "widzialność ${it / 1000} km" else "widzialność $it m" },
                    p.ceilingFt?.let { "pułap $it ft" },
                    p.weather,
                )
                if (details.isNotEmpty()) Text(details.joinToString(", ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.padding(top = 4.dp))
                Text(p.raw, style = RawText.copy(fontSize = 12.sp, lineHeight = 18.sp), color = c.ink2, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun CopyButton(text: String) {
    val clip = LocalClipboardManager.current
    IconButton({ clip.setText(AnnotatedString(text)) }) { Icon(Icons.Outlined.ContentCopy, "Kopiuj", tint = Fm.c.ink2, modifier = Modifier.size(20.dp)) }
}

// ---------------- PASY ----------------

@Composable
private fun RunwaysPart(d: AirportDetail) {
    val c = Fm.c
    val m = d.weather?.metar
    Section("Pasy i wiatr", Icons.Outlined.FlightLand) {
        if (d.runways.isEmpty()) {
            Text("Baza OurAirports nie ma danych o pasach tego lotniska.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            return@Section
        }
        val analysis = Wind.analyze(d.runways, m?.windDir, m?.windKt, m?.gustKt)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RunwayWindDiagram(d.runways, m?.windDir, m?.windKt, m?.windVariable == true, Modifier.widthIn(max = 170.dp).weight(1f))
            if (analysis.isNotEmpty()) {
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row { Text("Pas", style = MaterialTheme.typography.labelSmall, color = c.ink2, modifier = Modifier.weight(1f)); Text("czoł.  bocz.", style = MaterialTheme.typography.labelSmall, color = c.ink2) }
                    analysis.forEachIndexed { i, rw ->
                        val head = rw.steady.headwind.roundToInt()
                        val cross = rw.steady.crosswind
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(rw.runwayEnd, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (i == 0) c.accent else c.ink, modifier = Modifier.weight(1f))
                            Text(if (head >= 0) "↓%2d".format(head) else "↑%2d".format(-head), fontFamily = Mono, fontSize = 14.sp, color = if (head < 0) c.ifr else c.ink)
                            Spacer(Modifier.width(8.dp))
                            Text((if (cross >= 0) "→" else "←") + "%2d".format(abs(cross).roundToInt()), fontFamily = Mono, fontSize = 14.sp,
                                color = when { abs(cross) >= 20 -> c.ifr; abs(cross) >= 12 -> c.caution; else -> c.ink })
                        }
                        Rule()
                    }
                }
            }
        }
        if (analysis.isNotEmpty()) Text("↓ czołowy, ↑ tylny, → z prawej, ← z lewej (kt). Kierunki rzeczywiste.", style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.size(12.dp))
        d.runways.forEach { rw ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rw.name, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink, modifier = Modifier.width(90.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(
                            rw.lengthFt?.let { "${Atmo.ftToM(it.toDouble()).roundToInt()} m" + (rw.widthFt?.let { w -> " × ${Atmo.ftToM(w.toDouble()).roundToInt()} m" } ?: "") },
                            rw.surfaceLabel.lowercase(),
                        ).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium, color = c.ink,
                    )
                    Text(
                        listOfNotNull(rw.lengthFt?.let { "$it ft" }, if (rw.lighted) "oświetlony" else null,
                            listOf(rw.le, rw.he).firstNotNullOfOrNull { e -> e.displacedFt?.takeIf { it > 0 }?.let { "przesunięty próg ${e.ident}: $it ft" } }).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = c.ink2,
                    )
                    listOf(rw.le.ident, rw.he.ident).mapNotNull { id -> d.nav?.ilsFor(id)?.takeIf { it.freqMhz != null }?.let { id to it } }.forEach { (id, ils) ->
                        Text("ILS $id: ${ils.ident ?: ""} %.2f".format(Locale.US, ils.freqMhz) + (ils.locCourseDeg?.let { ", kurs %03d°".format(it.roundToInt()) } ?: ""),
                            fontFamily = Mono, fontSize = 12.sp, color = c.blue)
                    }
                }
            }
            Rule()
        }
    }
}

// ---------------- RADIO ----------------

private fun freqIcon(f: Frequency): ImageVector = when (f.type.uppercase()) {
    "ATIS" -> Icons.AutoMirrored.Outlined.VolumeUp
    "APP", "DEP", "CTR", "CNTR" -> Icons.Outlined.Explore
    else -> Icons.Outlined.Radio
}

@Composable
private fun RadioPart(d: AirportDetail, vm: MainViewModel) {
    val c = Fm.c
    val clip = LocalClipboardManager.current
    val link by vm.linkStatus.collectAsStateWithLifecycle()
    val connected = link is com.freemetar.app.data.link.LinkStatus.Connected
    var menuFor by remember { mutableStateOf<Frequency?>(null) }
    Section("Radio", Icons.Outlined.Radio) {
        if (d.frequencies.isEmpty()) {
            Text("Brak częstotliwości w bazie. W VATSIM i IVAO sprawdź aktywnych kontrolerów na mapie sieci.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
        }
        d.frequencies.forEach { f ->
            Box {
            Row(
                Modifier.fillMaxWidth().clickable {
                    // Z symulatorem: ustaw w radiu; bez niego: skopiuj
                    if (connected) menuFor = f else clip.setText(AnnotatedString("%.3f".format(Locale.US, f.mhz)))
                }.padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(freqIcon(f), null, tint = c.ink2, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.label, style = MaterialTheme.typography.titleSmall, color = c.ink)
                    if (f.description.isNotBlank()) Text(f.description, style = MaterialTheme.typography.bodySmall, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("%.3f".format(Locale.US, f.mhz), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.blue)
            }
            DropdownMenu(expanded = menuFor == f, onDismissRequest = { menuFor = null }) {
                val hz = com.freemetar.app.domain.ControlMath.comHz(f.mhz)
                listOf(
                    "COM1 zapasowa" to "COM_STBY_RADIO_SET_HZ", "COM1 aktywna" to "COM_RADIO_SET_HZ",
                    "COM2 zapasowa" to "COM2_STBY_RADIO_SET_HZ", "COM2 aktywna" to "COM2_RADIO_SET_HZ",
                ).forEach { (label, event) ->
                    DropdownMenuItem(text = { Text("Ustaw jako $label") }, enabled = hz != null, onClick = {
                        hz?.let { vm.link.command(event, it.toDouble()) }
                        menuFor = null
                    })
                }
                DropdownMenuItem(text = { Text("Kopiuj") }, onClick = { clip.setText(AnnotatedString("%.3f".format(Locale.US, f.mhz))); menuFor = null })
            }
            }
            Rule()
        }
        if (d.frequencies.isNotEmpty()) Text(
            if (connected) "Dotknij częstotliwości, aby ustawić ją w radiu samolotu." else "Dotknij częstotliwości, aby ją skopiować.",
            style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 8.dp),
        )
        MsfsRadioBlock(
            d, connected,
            onRefresh = { vm.loadNav(d.ident, refresh = true) },
            onSetNav = { mhz, active ->
                com.freemetar.app.domain.ControlMath.navHz(mhz)?.let { hz -> vm.link.command(if (active) "NAV1_RADIO_SET_HZ" else "NAV1_STBY_SET_HZ", hz.toDouble()) }
            },
            onCopy = { mhz -> clip.setText(AnnotatedString("%.2f".format(Locale.US, mhz))) },
        )
        if (d.navaids.isNotEmpty()) {
            Text("Radiolatarnie do 40 NM", style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
            d.navaids.forEach { (n, dist) ->
                val brg = d.airport?.let { Geo.bearing(it.lat, it.lon, n.lat, n.lon) }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.ident, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink, modifier = Modifier.width(54.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${n.type} ${n.name}", style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${dist.fmt(1)} NM" + (brg?.let { ", kierunek %03d°".format(it.roundToInt()) } ?: ""), style = MaterialTheme.typography.bodySmall, color = c.ink2)
                    }
                    Text(n.frequencyLabel, fontFamily = Mono, fontSize = 14.sp, color = c.blue)
                }
                Rule()
            }
        }
    }
}

// ---------------- LOTNISKO ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AirportPart(d: AirportDetail, s: Settings) {
    val c = Fm.c
    val a = d.airport
    val ctx = LocalContext.current
    Section("Lotnisko", Icons.Outlined.Info) {
        if (a == null) {
            Text("Brak ${d.ident} w bazie OurAirports.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            return@Section
        }
        Text(a.name, style = MaterialTheme.typography.titleMedium, color = c.ink)
        Text(listOf(a.municipality, a.region, a.country).filter { it.isNotBlank() }.joinToString(", ") + ". " + a.typeLabel + if (a.scheduled) ", ruch rozkładowy." else ".",
            style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(bottom = 10.dp))
        val sun = Sun.times(LocalDate.now(ZoneOffset.UTC), a.lat, a.lon)
        fun t(z: ZonedDateTime?) = z?.format(DateTimeFormatter.ofPattern("HH:mm"))?.plus("Z") ?: "—"
        FactGrid(
            listOf(
                Fact("ICAO / IATA", a.ident + if (a.iata.isNotBlank()) " / ${a.iata}" else ""),
                Fact("Elewacja", a.elevationFt?.let { if (s.altUnit == AltUnit.FT) "$it ft" else "${Atmo.ftToM(it.toDouble()).roundToInt()} m" } ?: "—"),
                Fact("Szerokość", Geo.formatLatLon(a.lat, a.lon).substringBefore(" ")),
                Fact("Długość", Geo.formatLatLon(a.lat, a.lon).substringAfter(" ")),
                Fact("Wschód słońca", t(sun.sunrise)),
                Fact("Zachód słońca", t(sun.sunset)),
                Fact("Świt cywilny", t(sun.civilDawn)),
                Fact("Koniec zmierzchu", t(sun.civilDusk), sub = "od tej chwili lot nocny"),
            ),
        )
        Spacer(Modifier.size(14.dp))
        fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        val links = buildList {
            add("SkyVector" to "https://skyvector.com/airport/${a.ident}")
            add("Windy" to "https://www.windy.com/${a.lat}/${a.lon}?${a.lat},${a.lon},11")
            add("VATSIM Radar" to "https://vatsim-radar.com/airport/${a.ident}")
            add("OurAirports" to "https://ourairports.com/airports/${a.ident}/")
            add("Mapa" to "https://www.openstreetmap.org/?mlat=${a.lat}&mlon=${a.lon}#map=14/${a.lat}/${a.lon}")
            if (a.wikipedia.isNotBlank()) add("Wikipedia" to a.wikipedia)
            if (a.homeLink.isNotBlank()) add("Strona lotniska" to a.homeLink)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEach { (label, url) -> Pill(label, icon = Icons.AutoMirrored.Outlined.OpenInNew) { open(url) } }
        }
    }
}

// ---------------- OKOLICA ----------------

@Composable
private fun NearbyPart(d: AirportDetail, onOpen: (String) -> Unit) {
    val c = Fm.c
    Section("Okolica do 60 NM", Icons.Outlined.NearMe) {
        if (d.nearby.isEmpty()) {
            Text("Brak innych lotnisk w promieniu 60 NM.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            return@Section
        }
        d.nearby.forEach { (a, dist) ->
            val m = d.nearbyMetars[a.ident]
            val brg = d.airport?.let { Geo.bearing(it.lat, it.lon, a.lat, a.lon) }
            Row(Modifier.fillMaxWidth().clickable { onOpen(a.ident) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(64.dp)) {
                    Text(a.ident, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
                    CategoryTag(m?.flightCategory, Modifier.padding(top = 3.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m?.let { "${windShort(it)} kt, ${it.tempC?.roundToInt() ?: "—"}°, QNH ${it.altimHpa?.roundToInt() ?: "—"}" } ?: "bez METAR",
                        fontFamily = if (m != null) Mono else null, fontSize = 12.sp, color = c.ink2)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${dist.fmt(0)} NM", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink)
                    if (brg != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        // Ikona NearMe wskazuje domyślnie NE (45°)
                        Icon(Icons.Outlined.NearMe, null, tint = c.accent, modifier = Modifier.size(13.dp).rotate(brg.toFloat() - 45f))
                        Spacer(Modifier.width(3.dp))
                        Text("%03d°".format(brg.roundToInt()), fontFamily = Mono, fontSize = 12.sp, color = c.ink2)
                    }
                }
            }
            Rule()
        }
    }
}
