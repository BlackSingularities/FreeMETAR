package com.freemetar.app.ui.flight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.data.link.FlightState
import com.freemetar.app.data.link.LinkClient
import com.freemetar.app.data.link.Radio
import com.freemetar.app.domain.AircraftProfile
import com.freemetar.app.domain.ControlMath
import com.freemetar.app.domain.loc
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------- Wybór panelu wg profilu ----------------
// A320 ma własne widoki w stylu kabiny Airbusa; pozostałe profile — panele ogólne dopasowane do profilu.

@Composable
fun AutopilotFor(link: LinkClient, s: FlightState, p: AircraftProfile, compact: Boolean = false) =
    if (p == AircraftProfile.A320) AirbusFcu(link, s, compact) else AutopilotPanelV2(link, s, p, compact)

@Composable
fun RadioFor(link: LinkClient, s: FlightState, p: AircraftProfile) =
    if (p == AircraftProfile.A320) { AirbusRmp(link, s); Spacer(Modifier.height(10.dp)); AirbusAtc(link, s) } else RadioPanel(link, s, p)

@Composable
fun SystemsFor(link: LinkClient, s: FlightState, p: AircraftProfile) =
    if (p == AircraftProfile.A320) AirbusSystems(link, s) else SystemsPanel(link, s, p)

@Composable
fun EnginesFor(link: LinkClient, s: FlightState, p: AircraftProfile) =
    if (p == AircraftProfile.A320) AirbusEngines(link, s) else EnginePanel(link, s, p)

// ---------------- Wygląd paneli Airbusa ----------------

/** Szaroniebieska płyta paneli A320, białe opisy, czarne okienka z pomarańczowymi (FCU) albo białymi (RMP) cyframi. */
private val AB_PLATE = Color(0xFF5C768E)
private val AB_PLATE_EDGE = Color(0xFF3B4F61)
private val AB_LEGEND = Color(0xFFF4F7FA)
private val AB_WINDOW = Color(0xFF0A0B0C)
private val AB_ORANGE = Color(0xFFFF9F2E)
private val AB_LAMP = Color(0xFF3FE07A)
private val AB_KEY = Color(0xFF2B3138)
private val AB_CYAN = Color(0xFF4FD1E8)
private val AB_AMBER = Color(0xFFFFB020)
private val AB_RED = Color(0xFFFF3B47)

@Composable
private fun airbusSkin() = panelSkin(AircraftProfile.A320).copy(
    plate = AB_PLATE, plateEdge = AB_PLATE_EDGE, key = AB_KEY, legend = AB_LEGEND, window = AB_WINDOW, windowText = AB_ORANGE,
    windowLabel = AB_LEGEND, lampOn = AB_LAMP, knobOuter = Color(0xFFB9C0C6), knobInner = Color(0xFFDCE1E5), knobMark = Color(0xFF22272C),
)

/** Płyta panelu z nazwą (FCU, RMP, ATC…) w lewym górnym rogu. */
@Composable
private fun AirbusPlate(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AB_PLATE).border(1.dp, AB_PLATE_EDGE, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AB_LEGEND, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

/** Czarne okienko z cyframi jak na wyświetlaczach siedmiosegmentowych; dotknięcie — wpisanie wartości. */
@Composable
private fun SegWindow(value: String, color: Color, modifier: Modifier = Modifier, size: Int = 24, desc: String, onClick: (() -> Unit)? = null) {
    Box(
        modifier.clip(RoundedCornerShape(5.dp)).background(AB_WINDOW).border(1.dp, Color(0xFF22282E), RoundedCornerShape(5.dp))
            .semantics { contentDescription = desc }
            .let { if (onClick != null) it.clickable(onClickLabel = loc("Wpisz wartość", "Type a value"), onClick = onClick) else it }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = size.sp, color = color, maxLines = 1, softWrap = false) }
}

/** Mały klawisz PUSH/PULL pod pokrętłem FCU (wciśnięcie = tryb zarządzany, wyciągnięcie = wybrany). */
@Composable
private fun PushPull(label: String, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(Color(0x33000000)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(15.dp))
            .semantics { contentDescription = desc }
            .clickable(role = Role.Button) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = AB_LEGEND) }
}

// ---------------- FCU ----------------

/**
 * Panel FCU A320: okienka SPD, HDG, ALT i V/S z pokrętłami. Obrót pokrętła zmienia wartość; PUSH i PULL
 * działają jak w Airbusie tam, gdzie MSFS ma odpowiednik (HDG: PULL — kurs wybrany, PUSH — NAV;
 * ALT: PULL — OP CLB/DES, PUSH — utrzymanie; V/S: PULL — V/S, PUSH — wyrównanie lotu). Pod spodem LOC, AP1, A/THR, APPR i FD.
 */
@Composable
fun AirbusFcu(link: LinkClient, s: FlightState, compact: Boolean = false) {
    val ap = s.autopilot
    val m = ap.modes
    var enter by remember { mutableStateOf<String?>(null) }
    fun cmd(n: String, v: Number? = null) { link.command(n, v?.toDouble()) }
    fun live() = link.state.value?.autopilot
    val skin = airbusSkin()
    val knob = if (compact) 64.dp else 80.dp

    AirbusPlate("FCU") {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = if (maxWidth >= 520.dp) 4 else 2
            val sections: List<@Composable (Modifier) -> Unit> = listOf(
                { mod ->
                    FcuSection("SPD", ap.spdKt?.let { "${it.roundToInt()}" } ?: "---", knob, mod,
                        onStep = { d -> cmd("AP_SPD_VAR_SET", ControlMath.speed(live()?.spdKt, d)) },
                        onBig = { d -> cmd("AP_SPD_VAR_SET", ControlMath.speed(live()?.spdKt, d * 10)) },
                        onWindow = { enter = "SPD" }, onPush = { enter = "SPD" },
                        pull = loc("Prędkość wybrana", "Selected speed") to { cmd("AP_PANEL_SPEED_HOLD") }, push = null)
                },
                { mod ->
                    FcuSection("HDG", ap.hdgDeg?.let { "%03d".format(it.roundToInt() % 360) } ?: "---", knob, mod,
                        onStep = { d -> cmd("HEADING_BUG_SET", ControlMath.heading(live()?.hdgDeg, d)) },
                        onBig = { d -> cmd("HEADING_BUG_SET", ControlMath.heading(live()?.hdgDeg, d * 10)) },
                        onWindow = { enter = "HDG" }, onPush = { cmd("AP_NAV1_HOLD") },
                        pull = loc("Kurs wybrany", "Selected heading") to { cmd("AP_HDG_HOLD") },
                        push = loc("Nawigacja (NAV)", "Navigation (NAV)") to { cmd("AP_NAV1_HOLD") })
                },
                { mod ->
                    FcuSection("ALT", ap.altFt?.let { "%05d".format(it.roundToInt()) } ?: "-----", knob, mod,
                        onStep = { d -> cmd("AP_ALT_VAR_SET_ENGLISH", ControlMath.altitude(live()?.altFt, d * 100)) },
                        onBig = { d -> cmd("AP_ALT_VAR_SET_ENGLISH", ControlMath.altitude(live()?.altFt, d * 1000)) },
                        onWindow = { enter = "ALT" }, onPush = { cmd("AP_ALT_HOLD") },
                        pull = loc("Wznoszenie / zniżanie (OP CLB/DES)", "Open climb / descent") to { cmd("FLIGHT_LEVEL_CHANGE") },
                        push = loc("Utrzymanie wysokości", "Altitude hold") to { cmd("AP_ALT_HOLD") })
                },
                { mod ->
                    FcuSection("V/S", ap.vsFpm?.let { "%+05d".format(it.roundToInt()) } ?: "-----", knob, mod,
                        onStep = { d -> cmd("AP_VS_VAR_SET_ENGLISH", ControlMath.verticalSpeed(live()?.vsFpm, d * 100)) },
                        onBig = { d -> cmd("AP_VS_VAR_SET_ENGLISH", ControlMath.verticalSpeed(live()?.vsFpm, d * 500)) },
                        onWindow = { enter = "VS" }, onPush = { cmd("AP_VS_VAR_SET_ENGLISH", 0); if (!m.vs) cmd("AP_VS_HOLD") },
                        pull = loc("Prędkość pionowa", "Vertical speed") to { cmd("AP_VS_HOLD") },
                        push = loc("Wyrównanie lotu (V/S 0)", "Level off (V/S 0)") to { cmd("AP_VS_VAR_SET_ENGLISH", 0); if (!m.vs) cmd("AP_VS_HOLD") })
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sections.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { it(Modifier.weight(1f)) } }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Klawisze FCU z zielonymi lampkami nad napisem; FD jest na panelu EFIS obok
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AfdsKey("LOC", m.loc, Modifier.weight(1f), skin) { cmd("AP_LOC_HOLD") }
            AfdsKey("AP1", ap.master, Modifier.weight(1f), skin) { cmd("AP_MASTER") }
            AfdsKey("A/THR", ap.athr, Modifier.weight(1f), skin) { cmd("AUTO_THROTTLE_ARM") }
            AfdsKey("APPR", m.apr, Modifier.weight(1f), skin) { cmd("AP_APR_HOLD") }
            AfdsKey("FD", ap.fd, Modifier.weight(1f), skin) { cmd("TOGGLE_FLIGHT_DIRECTOR") }
        }
    }
    if (!compact) Text(loc("Kręć pokrętłem: pierścień — duży krok, środek — mały. Dotknięcie środka = PUSH. Dotknij okienka, aby wpisać wartość.",
        "Turn a knob: outer ring for big steps, centre for small ones. Tapping the centre = PUSH. Tap a window to type a value."),
        style = MaterialTheme.typography.labelSmall, color = Fm.c.ink2, modifier = Modifier.padding(top = 8.dp))

    when (enter) {
        "HDG" -> EnterDialog(loc("Kurs (HDG)", "Heading (HDG)"), loc("np. 270", "e.g. 270"), KeyboardType.Number, { it.toIntOrNull() in 0..359 }, { cmd("HEADING_BUG_SET", it.toInt()) }) { enter = null }
        "ALT" -> EnterDialog(loc("Wysokość (ALT, ft)", "Altitude (ALT, ft)"), loc("np. 12000", "e.g. 12000"), KeyboardType.Number, { it.toIntOrNull() in 0..50_000 }, { cmd("AP_ALT_VAR_SET_ENGLISH", it.toInt()) }) { enter = null }
        "SPD" -> EnterDialog(loc("Prędkość (SPD, kt)", "Speed (SPD, kt)"), loc("np. 250", "e.g. 250"), KeyboardType.Number, { it.toIntOrNull() in 0..500 }, { cmd("AP_SPD_VAR_SET", it.toInt()) }) { enter = null }
        "VS" -> EnterDialog(loc("Prędkość pionowa (V/S, ft/min)", "Vertical speed (V/S, ft/min)"), loc("np. -1500", "e.g. -1500"), KeyboardType.Number, { it.toIntOrNull() in -8000..8000 }, { cmd("AP_VS_VAR_SET_ENGLISH", it.toInt()) }) { enter = null }
    }
}

@Composable
private fun FcuSection(
    label: String, value: String, knob: Dp, modifier: Modifier,
    onStep: (Int) -> Unit, onBig: (Int) -> Unit, onWindow: () -> Unit, onPush: () -> Unit,
    pull: Pair<String, () -> Unit>?, push: Pair<String, () -> Unit>?,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AB_LEGEND)
        SegWindow(value, AB_ORANGE, Modifier.fillMaxWidth().padding(top = 3.dp), desc = "$label $value", onClick = onWindow)
        Spacer(Modifier.height(8.dp))
        DualKnob(knob, loc("Pokrętło $label: $value", "$label knob: $value"), skin = airbusSkin(), onStep = onStep, onBigStep = onBig, onPush = onPush)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (push != null) PushPull("PUSH", "$label PUSH: ${push.first}", Modifier.weight(1f), push.second)
            if (pull != null) PushPull("PULL", "$label PULL: ${pull.first}", Modifier.weight(1f), pull.second)
        }
    }
}

// ---------------- RMP i ATC ----------------

private enum class RmpSel(val label: String, val com: Boolean) { VHF1("VHF1", true), VHF2("VHF2", true), NAV1("NAV1", false), NAV2("NAV2", false) }

/**
 * Panel radiowy RMP: okienka ACTIVE i STBY/CRS, zielony przycisk zamiany i klawisze wyboru radia (VHF1, VHF2,
 * a pod napisem STBY NAV — NAV1 i NAV2). Pokrętło zmienia częstotliwość zapasową: pierścień o 1 MHz, środek o 25 kHz
 * (VHF) albo 50 kHz (NAV). Dotknięcie okienka STBY — wpisanie częstotliwości.
 */
@Composable
fun AirbusRmp(link: LinkClient, s: FlightState) {
    var selName by rememberSaveable { mutableStateOf(RmpSel.VHF1.name) }
    val sel = RmpSel.valueOf(selName)
    var enter by remember { mutableStateOf(false) }
    fun cmd(n: String, v: Number? = null) { link.command(n, v?.toDouble()) }
    val r = s.radios
    val radio: Radio? = when (sel) { RmpSel.VHF1 -> r.com1; RmpSel.VHF2 -> r.com2; RmpSel.NAV1 -> r.nav1; RmpSel.NAV2 -> r.nav2 }
    val fmt = if (sel.com) "%.3f" else "%.2f"
    fun liveStby(): Double? = link.state.value?.radios?.let { when (sel) { RmpSel.VHF1 -> it.com1; RmpSel.VHF2 -> it.com2; RmpSel.NAV1 -> it.nav1; RmpSel.NAV2 -> it.nav2 } }?.standbyMhz
    // Nowa częstotliwość zapasowa: krok w kHz, zawinięcie w zakresie pasma (VHF 118–136,975, NAV 108–117,95)
    fun setStby(stepKhz: Int) {
        val cur = liveStby() ?: return
        val (lo, hi) = if (sel.com) 118_000 to 136_975 else 108_000 to 117_950
        var khz = (cur * 1000).roundToInt() + stepKhz
        if (khz > hi) khz = lo + (khz - hi - 1)
        if (khz < lo) khz = hi - (lo - khz - 1)
        val hz = khz.toLong() * 1000
        when (sel) {
            RmpSel.VHF1 -> cmd("COM_STBY_RADIO_SET_HZ", hz)
            RmpSel.VHF2 -> cmd("COM2_STBY_RADIO_SET_HZ", hz)
            RmpSel.NAV1 -> cmd("NAV1_STBY_SET_HZ", hz)
            RmpSel.NAV2 -> cmd("NAV2_STBY_SET_HZ", hz)
        }
    }
    val swap = when (sel) { RmpSel.VHF1 -> "COM_STBY_RADIO_SWAP"; RmpSel.VHF2 -> "COM2_RADIO_SWAP"; RmpSel.NAV1 -> "NAV1_RADIO_SWAP"; RmpSel.NAV2 -> "NAV2_RADIO_SWAP" }
    val active = radio?.activeMhz?.let { fmt.format(java.util.Locale.US, it) } ?: "---.---"
    val stby = radio?.standbyMhz?.let { fmt.format(java.util.Locale.US, it) } ?: "---.---"

    AirbusPlate("RMP") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ACTIVE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AB_LEGEND)
                SegWindow(active, Color.White, Modifier.fillMaxWidth().padding(top = 3.dp), size = 22, desc = loc("${sel.label} aktywna $active", "${sel.label} active $active"))
            }
            Box(
                Modifier.size(width = 52.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(AB_KEY)
                    .semantics { contentDescription = loc("Zamień aktywną i zapasową ${sel.label}", "Swap active and standby ${sel.label}") }
                    .clickable(role = Role.Button) { cmd(swap) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.SwapHoriz, null, tint = AB_LAMP, modifier = Modifier.size(30.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("STBY/CRS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AB_LEGEND)
                SegWindow(stby, Color.White, Modifier.fillMaxWidth().padding(top = 3.dp), size = 22, desc = loc("${sel.label} zapasowa $stby", "${sel.label} standby $stby")) { enter = true }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RmpKey("VHF1", sel == RmpSel.VHF1, Modifier.weight(1f)) { selName = RmpSel.VHF1.name }
                    RmpKey("VHF2", sel == RmpSel.VHF2, Modifier.weight(1f)) { selName = RmpSel.VHF2.name }
                }
                // Napis STBY NAV z klamrą nad klawiszami nawigacji, jak na panelu
                Text("— STBY NAV —", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = AB_LEGEND, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RmpKey("NAV1", sel == RmpSel.NAV1, Modifier.weight(1f)) { selName = RmpSel.NAV1.name }
                    RmpKey("NAV2", sel == RmpSel.NAV2, Modifier.weight(1f)) { selName = RmpSel.NAV2.name }
                }
            }
            DualKnob(84.dp, loc("Pokrętło częstotliwości ${sel.label}", "${sel.label} frequency knob"), skin = airbusSkin(),
                onStep = { setStby(it * if (sel.com) 25 else 50) }, onBigStep = { setStby(it * 1000) }, onPush = { enter = true })
        }
    }
    if (enter) {
        val name = sel.label
        if (sel.com) EnterDialog(loc("$name — częstotliwość zapasowa (MHz)", "$name — standby frequency (MHz)"), "np. 121.500", KeyboardType.Decimal,
            { ControlMath.parseMhz(it)?.let(ControlMath::comHz) != null },
            { t -> ControlMath.parseMhz(t)?.let(ControlMath::comHz)?.let { hz -> cmd(if (sel == RmpSel.VHF1) "COM_STBY_RADIO_SET_HZ" else "COM2_STBY_RADIO_SET_HZ", hz) } }) { enter = false }
        else EnterDialog(loc("$name — częstotliwość zapasowa (MHz)", "$name — standby frequency (MHz)"), "np. 110.30", KeyboardType.Decimal,
            { ControlMath.parseMhz(it)?.let(ControlMath::navHz) != null },
            { t -> ControlMath.parseMhz(t)?.let(ControlMath::navHz)?.let { hz -> cmd(if (sel == RmpSel.NAV1) "NAV1_STBY_SET_HZ" else "NAV2_STBY_SET_HZ", hz) } }) { enter = false }
    }
}

/** Klawisz wyboru radia RMP: zielony trójkąt nad klawiszem, gdy radio jest wybrane. */
@Composable
private fun RmpKey(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("▼", fontSize = 9.sp, color = if (on) AB_LAMP else Color(0x33FFFFFF))
        Box(
            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(5.dp)).background(AB_KEY).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(5.dp))
                .semantics { contentDescription = label + if (on) loc(", wybrane", ", selected") else ""; role = Role.RadioButton }
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
            contentAlignment = Alignment.Center,
        ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AB_LEGEND) }
    }
}

/**
 * Panel ATC (transponder) A320: okienko kodu, klawiatura 0–7 z CLR — czwarta cyfra wysyła kod,
 * przełącznik trybu STBY / AUTO / ON i klawisz IDENT.
 */
@Composable
fun AirbusAtc(link: LinkClient, s: FlightState) {
    val x = s.radios.transponder
    var typed by rememberSaveable { mutableStateOf("") }
    fun cmd(n: String, v: Number? = null) { link.command(n, v?.toDouble()) }
    val shown = if (typed.isNotEmpty()) typed.padEnd(4, '-') else x?.code ?: "----"
    val emergency = typed.isEmpty() && x?.code in setOf("7500", "7600", "7700")

    AirbusPlate("ATC / XPDR") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SegWindow(shown, if (emergency) AB_RED else AB_ORANGE, Modifier.weight(1f), size = 26, desc = loc("Kod transpondera $shown", "Transponder code $shown"))
            Box(
                Modifier.size(width = 72.dp, height = 44.dp).clip(RoundedCornerShape(5.dp)).background(AB_KEY)
                    .border(1.dp, if (x?.ident == true) AB_LAMP else Color(0x40FFFFFF), RoundedCornerShape(5.dp))
                    .clickable(role = Role.Button, onClickLabel = "IDENT") { cmd("XPNDR_IDENT_ON") },
                contentAlignment = Alignment.Center,
            ) { Text("IDENT", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (x?.ident == true) AB_LAMP else AB_LEGEND) }
        }
        Spacer(Modifier.height(10.dp))
        // Klawiatura jak na panelu ATC: 0–7 i CLR (kasuje wpisywane cyfry)
        listOf(listOf("1", "2", "3", "4"), listOf("5", "6", "7", "0")).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d ->
                    AtcKey(d, Modifier.weight(1f)) {
                        val next = typed + d
                        if (next.length == 4) { ControlMath.squawk(next)?.let { cmd("XPNDR_SET", it) }; typed = "" } else typed = next
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AtcKey("CLR", Modifier.weight(1f)) { typed = "" }
            // Tryb: STBY (1), AUTO = z wysokością (4), ON (3)
            listOf("STBY" to ("standby" to 1), "AUTO" to ("alt" to 4), "ON" to ("on" to 3)).forEach { (label, mv) ->
                val on = x?.mode == mv.first
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(5.dp)).background(if (on) Color(0xFF1C2228) else AB_KEY)
                        .border(1.dp, if (on) AB_LAMP else Color(0x40FFFFFF), RoundedCornerShape(5.dp))
                        .semantics { contentDescription = loc("Tryb $label", "Mode $label") + if (on) loc(", wybrany", ", selected") else ""; role = Role.RadioButton }
                        .clickable { cmd("XPNDR_MODE", mv.second) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (on) AB_LAMP else AB_LEGEND) }
            }
        }
    }
}

@Composable
private fun AtcKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(5.dp)).background(AB_KEY).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(5.dp))
            .clickable(role = Role.Button, onClickLabel = label) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AB_LEGEND) }
}

// ---------------- Systemy: EXT LT, podwozie, klapy, hamulec aerodynamiczny, hamulec postojowy, trymer ----------------

/** Klawisz-przełącznik w stylu nadgłowia Airbusa: ciemny, z białym „ON”, gdy włączony. */
@Composable
private fun OverheadSwitch(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1A1E22)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(4.dp))
                .semantics { contentDescription = label + if (on) loc(", włączone", ", on") else loc(", wyłączone", ", off"); role = Role.Switch }
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            contentAlignment = Alignment.Center,
        ) { Text(if (on) "ON" else "OFF", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) Color.White else Color(0x66FFFFFF)) }
        Text(label, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = AB_LEGEND, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Klawisz pozycji dźwigni (klapy, hamulec aerodynamiczny): podświetlony, gdy dźwignia jest w tej pozycji. */
@Composable
private fun DetentKey(label: String, on: Boolean, modifier: Modifier = Modifier, desc: String = label, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.height(42.dp).clip(RoundedCornerShape(5.dp)).background(if (on) Color.White else AB_KEY).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(5.dp))
            .semantics { contentDescription = desc + if (on) loc(", ustawione", ", set") else ""; role = Role.RadioButton }
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (on) Color.Black else AB_LEGEND, maxLines = 1) }
}

/** Klawisz z powtarzaniem przy przytrzymaniu (trymer): 400 ms, potem co 90 ms. */
@Composable
private fun HoldKey(label: String, desc: String, modifier: Modifier = Modifier, action: () -> Unit) {
    val scope = rememberCoroutineScope()
    val act by rememberUpdatedState(action)
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(5.dp)).background(AB_KEY).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(5.dp))
            .semantics { contentDescription = desc; role = Role.Button; onClick { act(); true } }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    act()
                    val job = scope.launch { delay(400); while (true) { act(); delay(90) } }
                    waitForUpOrCancellation()
                    job.cancel()
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AB_LEGEND) }
}

@Composable
fun AirbusSystems(link: LinkClient, s: FlightState) {
    fun cmd(n: String, v: Number? = null) { link.command(n, v?.toDouble()) }
    val ctl = s.controls
    val l = s.lights
    var confirmGear by remember { mutableStateOf(false) }

    // Nadgłowie: oświetlenie zewnętrzne (EXT LT) w kolejności z panelu
    AirbusPlate("EXT LT") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OverheadSwitch("STROBE", l.strobe == true, Modifier.weight(1f)) { cmd("STROBES_TOGGLE") }
            OverheadSwitch("BEACON", l.beacon == true, Modifier.weight(1f)) { cmd("TOGGLE_BEACON_LIGHTS") }
            OverheadSwitch("WING", l.wing == true, Modifier.weight(1f)) { cmd("TOGGLE_WING_LIGHTS") }
            OverheadSwitch("NAV", l.nav == true, Modifier.weight(1f)) { cmd("TOGGLE_NAV_LIGHTS") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OverheadSwitch("LOGO", l.logo == true, Modifier.weight(1f)) { cmd("TOGGLE_LOGO_LIGHTS") }
            OverheadSwitch("LAND", l.landing == true, Modifier.weight(1f)) { cmd("LANDING_LIGHTS_TOGGLE") }
            OverheadSwitch("NOSE", l.taxi == true, Modifier.weight(1f)) { cmd("TOGGLE_TAXI_LIGHTS") }
            OverheadSwitch("CABIN", l.cabin == true, Modifier.weight(1f)) { cmd("TOGGLE_CABIN_LIGHTS") }
        }
    }
    Spacer(Modifier.height(10.dp))

    // Podwozie: dźwignia UP/DOWN i trzy wskaźniki położenia (zielone trójkąty = zablokowane w dole)
    val gearDown = ctl.gearDown == true
    val moving = ctl.gearPct?.let { it > 1 && it < 99 } == true
    AirbusPlate("L/G") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(3) {
                    Text(if (moving) "UNLK" else if (gearDown) "▼" else "", fontFamily = Mono, fontWeight = FontWeight.Bold,
                        fontSize = if (moving) 11.sp else 22.sp, color = if (moving) AB_RED else AB_LAMP,
                        modifier = Modifier.width(44.dp).height(34.dp).clip(RoundedCornerShape(4.dp)).background(AB_WINDOW).padding(top = 4.dp),
                        textAlign = TextAlign.Center)
                }
            }
            DetentKey(if (gearDown) "DOWN" else "UP", true, Modifier.width(96.dp), loc("Dźwignia podwozia", "Gear lever")) {
                if (gearDown && s.aircraft.onGround == true) confirmGear = true else cmd("GEAR_TOGGLE")
            }
        }
    }
    Spacer(Modifier.height(10.dp))

    // Klapy: dźwignia CONF 0 / 1 / 2 / 3 / FULL (FLAPS_SET); przy innej liczbie pozycji — numery
    val positions = ctl.flapsPositions?.takeIf { it > 1 }
    AirbusPlate("FLAPS") {
        if (positions == null) Text("—", color = AB_LEGEND)
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(positions) { i ->
                val label = if (positions == 5) listOf("0", "1", "2", "3", "FULL")[i] else "$i"
                DetentKey(label, ctl.flapsIndex == i, Modifier.weight(1f), loc("Klapy $label", "Flaps $label")) {
                    cmd("FLAPS_SET", (16383.0 * i / (positions - 1)).roundToInt())
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))

    // Hamulec aerodynamiczny: RET / ½ / FULL i uzbrojenie (ARM) przed lądowaniem
    val sb = ctl.spoilersPct ?: 0.0
    AirbusPlate("SPEED BRAKE") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DetentKey("ARM", ctl.spoilersArmed == true, Modifier.weight(1f), loc("Uzbrojenie spoilerów", "Ground spoilers arm")) { cmd("SPOILERS_ARM_TOGGLE") }
            DetentKey("RET", sb < 5, Modifier.weight(1f), loc("Hamulec aerodynamiczny schowany", "Speed brake retracted")) { cmd("SPOILERS_SET", 0) }
            DetentKey("1/2", sb in 25.0..75.0, Modifier.weight(1f), loc("Hamulec aerodynamiczny w połowie", "Speed brake half")) { cmd("SPOILERS_SET", 8192) }
            DetentKey("FULL", sb > 95, Modifier.weight(1f), loc("Hamulec aerodynamiczny pełny", "Speed brake full")) { cmd("SPOILERS_SET", 16383) }
        }
    }
    Spacer(Modifier.height(10.dp))

    // Hamulec postojowy i trymer wysokości (koło trymera na pedestale)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AirbusPlate("PARK BRK", Modifier.weight(1f)) {
            DetentKey(if (ctl.parkingBrake == true) "ON" else "OFF", ctl.parkingBrake == true, Modifier.fillMaxWidth(), loc("Hamulec postojowy", "Parking brake")) { cmd("PARKING_BRAKES") }
        }
        AirbusPlate("PITCH TRIM", Modifier.weight(1.4f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HoldKey("DN", loc("Trym nosem w dół", "Trim nose down"), Modifier.weight(1f)) { cmd("ELEV_TRIM_DN") }
                SegWindow(ctl.elevatorTrimPct?.let { "%+.1f".format(it) } ?: "--", Color.White, Modifier.weight(1.2f), size = 16, desc = loc("Trym", "Trim"))
                HoldKey("UP", loc("Trym nosem w górę", "Trim nose up"), Modifier.weight(1f)) { cmd("ELEV_TRIM_UP") }
            }
        }
    }

    if (confirmGear) AlertDialog(
        onDismissRequest = { confirmGear = false },
        title = { Text(loc("Schować podwozie na ziemi?", "Retract the gear on the ground?")) },
        text = { Text(loc("Samolot stoi na ziemi. Schowanie podwozia może go uszkodzić w symulatorze.", "The aircraft is on the ground. Retracting the gear may damage it in the simulator.")) },
        confirmButton = { TextButton({ cmd("GEAR_UP"); confirmGear = false }) { Text(loc("Schowaj", "Retract"), color = Fm.c.danger) } },
        dismissButton = { TextButton({ confirmGear = false }) { Text(loc("Anuluj", "Cancel")) } },
    )
}

// ---------------- Silniki: E/WD i dźwignie ciągu ----------------

@Composable
fun AirbusEngines(link: LinkClient, s: FlightState) {
    AirbusEwd(s)
    Spacer(Modifier.height(12.dp))
    Quadrant(link, s, AircraftProfile.A320)
    if (s.autopilot.athr) Text(loc("A/THR włączony — symulator może sam przestawiać ciąg.", "A/THR is on — the simulator may move the thrust itself."),
        style = MaterialTheme.typography.labelSmall, color = Fm.c.caution, modifier = Modifier.padding(top = 8.dp))
}

/**
 * Górny ekran ECAM (E/WD) A320: na silnik łuk N1 z odczytem, niebieski znacznik położenia dźwigni i czerwony limit,
 * pod nim łuk EGT; w środku kolumna opisów (N1 %, EGT °C, N2 %, FF KG/H) jak na ekranie, po prawej FOB i położenie klap.
 */
@Composable
fun AirbusEwd(s: FlightState) {
    val m = rememberTextMeasurer()
    val engines = s.engines.take(2).ifEmpty { return }
    val desc = engines.mapIndexed { i, e -> "ENG ${i + 1} N1 ${"%.1f".format(e.n1Pct ?: 0.0)}%" }.joinToString(", ")
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(10.dp)).semantics { contentDescription = "E/WD: $desc" }) {
        val w = size.width; val h = size.height
        drawRect(Color.Black)
        fun text(t: String, x: Float, y: Float, col: Color, sz: Float, bold: Boolean = false, alignLeft: Boolean = false) {
            val l = m.measure(t, TextStyle(color = col, fontSize = sz.sp, fontFamily = Mono, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
            drawText(l, topLeft = Offset(if (alignLeft) x else x - l.size.width / 2f, y - l.size.height / 2f))
        }
        // Dwa silniki symetrycznie po bokach kolumny opisów
        val cxs = listOf(w * 0.24f, w * 0.62f)
        engines.forEachIndexed { i, e ->
            val cx = cxs[i]
            // Łuk N1: 0–110 %, kąt początkowy 150°, rozpiętość 220°
            val n1 = e.n1Pct ?: 0.0
            val r = w * 0.15f; val cy = h * 0.2f
            val start = 150f; val sweep = 220f
            fun ang(v: Double, max: Double) = Math.toRadians(start + sweep * (v / max).coerceIn(0.0, 1.0)).toFloat()
            drawArc(Color.White, start, sweep * (104f / 110f), false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(3f))
            drawArc(AB_RED, start + sweep * (104f / 110f), sweep * (6f / 110f), false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(5f))
            // Podziałka co 10 % od 20 %
            for (v in 20..100 step 20) {
                val a = ang(v.toDouble(), 110.0)
                drawLine(Color.White, Offset(cx + r * 0.85f * cos(a), cy + r * 0.85f * sin(a)), Offset(cx + r * cos(a), cy + r * sin(a)), 2f)
            }
            // Wskazówka N1 (zielona) i znacznik dźwigni ciągu (niebieskie kółko na łuku)
            val na = ang(n1, 110.0)
            drawLine(AB_LAMP, Offset(cx, cy), Offset(cx + r * cos(na), cy + r * sin(na)), 4f, cap = StrokeCap.Round)
            (e.throttlePct ?: s.controls.throttlePct)?.let { thr ->
                val ta = ang(thr * 1.04, 110.0)
                drawCircle(AB_CYAN, 5f, Offset(cx + r * 1.12f * cos(ta), cy + r * 1.12f * sin(ta)), style = Stroke(2.5f))
            }
            // Odczyt N1 w ramce
            drawRect(Color.White.copy(alpha = 0.7f), Offset(cx - r * 0.05f, cy + r * 0.12f), Size(r * 1.05f, r * 0.5f), style = Stroke(2f))
            text("%.1f".format(n1), cx + r * 0.47f, cy + r * 0.37f, AB_LAMP, 17f, true)
            // Łuk EGT: 0–1000 °C, czerwień od 950
            val egt = e.egtC ?: 0.0
            val r2 = r * 0.72f; val cy2 = h * 0.48f
            drawArc(Color.White, start, sweep * 0.95f, false, Offset(cx - r2, cy2 - r2), Size(r2 * 2, r2 * 2), style = Stroke(3f))
            drawArc(AB_RED, start + sweep * 0.95f, sweep * 0.05f, false, Offset(cx - r2, cy2 - r2), Size(r2 * 2, r2 * 2), style = Stroke(5f))
            val ea = ang(egt, 1000.0)
            drawLine(if (egt > 950) AB_RED else if (egt > 850) AB_AMBER else AB_LAMP, Offset(cx, cy2), Offset(cx + r2 * cos(ea), cy2 + r2 * sin(ea)), 4f, cap = StrokeCap.Round)
            text("${egt.roundToInt()}", cx + r2 * 0.4f, cy2 + r2 * 0.45f, AB_LAMP, 15f, true)
            // N2 i przepływ paliwa w wierszach
            text(e.n2Pct?.let { "%.1f".format(it) } ?: "XX", cx, h * 0.68f, AB_LAMP, 15f, true)
            text(e.fuelFlowKgH?.let { "${(it / 10).roundToInt() * 10}" } ?: "XX", cx, h * 0.78f, AB_LAMP, 15f, true)
        }
        // Kolumna opisów między silnikami
        val lx = w * 0.43f
        text("N1", lx, h * 0.2f, Color.White, 12f, true); text("%", lx, h * 0.25f, AB_CYAN, 11f)
        text("EGT", lx, h * 0.48f, Color.White, 12f, true); text("°C", lx, h * 0.53f, AB_CYAN, 11f)
        text("N2", lx, h * 0.68f, Color.White, 12f, true); text("%", lx + w * 0.045f, h * 0.68f, AB_CYAN, 11f)
        text("FF", lx, h * 0.78f, Color.White, 12f, true); text("KG/H", lx, h * 0.83f, AB_CYAN, 10f)
        // Prawa kolumna: FOB i klapy (S/F z numerem pozycji)
        val rx = w * 0.82f
        text("FOB :", rx, h * 0.08f, Color.White, 12f)
        text(s.fuel.totalKg?.let { "${(it / 10).roundToInt() * 10}" } ?: "XX", rx, h * 0.14f, AB_LAMP, 16f, true)
        text("KG", rx + w * 0.11f, h * 0.14f, AB_CYAN, 10f)
        val fi = s.controls.flapsIndex
        val fp = s.controls.flapsPositions
        text("FLAP", rx, h * 0.62f, Color.White, 11f)
        text(if (fi != null && fp == 5) listOf("0", "1", "2", "3", "FULL")[fi.coerceIn(0, 4)] else fi?.toString() ?: "--", rx, h * 0.68f, AB_LAMP, 16f, true)
        if (s.controls.spoilersArmed == true) text("SPLR ARM", rx, h * 0.78f, AB_LAMP, 11f)
        s.environment.oatC?.let { text("SAT %+d°C".format(it.roundToInt()), w * 0.06f, h * 0.95f, AB_CYAN, 11f, alignLeft = true) }
        if (s.autopilot.athr) text("A/THR", w * 0.82f, h * 0.95f, Color.White, 12f, true)
    }
}
