package com.freemetar.app.ui.flight

import com.freemetar.app.domain.loc

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.data.link.FlightState
import com.freemetar.app.data.link.LinkClient
import com.freemetar.app.domain.AircraftProfile
import com.freemetar.app.domain.ControlMath
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Granica między wewnętrzną gałką a zewnętrznym pierścieniem (ułamek promienia). */
private const val INNER = 0.58f

/**
 * Podwójne pokrętło jak w awionice Garmina: zewnętrzny pierścień = duży krok, wewnętrzna gałka = mały.
 * Obrót palcem wokół środka; zgodnie z ruchem wskazówek zegara = w górę. Każdy krok to „ząbek” z wibracją.
 * Dotknięcie gałki bez obrotu = „wciśnięcie” (wpisanie wartości).
 */
@Composable
fun DualKnob(
    size: Dp, desc: String, modifier: Modifier = Modifier,
    onStep: (Int) -> Unit, onBigStep: (Int) -> Unit, onPush: () -> Unit,
) {
    val c = Fm.c
    val haptic = LocalHapticFeedback.current
    var inner by remember { mutableFloatStateOf(0f) }   // kąt wizualny gałki (stopnie)
    var outer by remember { mutableFloatStateOf(0f) }
    var grip by remember { mutableStateOf(0) }           // 0 nic, 1 gałka, 2 pierścień — podświetlenie
    val step by rememberUpdatedState(onStep)
    val big by rememberUpdatedState(onBigStep)
    val push by rememberUpdatedState(onPush)

    Canvas(
        modifier.size(size)
            .semantics {
                contentDescription = desc
                role = Role.Button
                onClick { push(); true }
                customActions = listOf(
                    CustomAccessibilityAction(loc("W górę", "Increase")) { step(1); true },
                    CustomAccessibilityAction(loc("W dół", "Decrease")) { step(-1); true },
                    CustomAccessibilityAction(loc("Duży krok w górę", "Big step up")) { big(1); true },
                    CustomAccessibilityAction(loc("Duży krok w dół", "Big step down")) { big(-1); true },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val ctr = Offset(this.size.width / 2f, this.size.height / 2f)
                    val r = this.size.width / 2f
                    val ring = (down.position - ctr).getDistance() > r * INNER
                    grip = if (ring) 2 else 1
                    down.consume()
                    val detent = if (ring) 30f else 20f      // stopnie na ząbek
                    var last = Math.toDegrees(atan2((down.position.y - ctr.y).toDouble(), (down.position.x - ctr.x).toDouble())).toFloat()
                    var acc = 0f
                    var turned = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        val p = ch.position - ctr
                        if (hypot(p.x, p.y) < r * 0.12f) continue   // przy samym środku kąt skacze
                        val a = Math.toDegrees(atan2(p.y.toDouble(), p.x.toDouble())).toFloat()
                        var d = a - last
                        if (d > 180f) d -= 360f
                        if (d < -180f) d += 360f
                        last = a
                        acc += d
                        if (ring) outer += d else inner += d
                        while (acc >= detent || acc <= -detent) {
                            val dir = if (acc > 0) 1 else -1
                            acc -= dir * detent
                            turned = true
                            if (ring) big(dir) else step(dir)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    if (!turned && !ring) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); push() }
                    grip = 0
                }
            },
    ) {
        val ctr = center
        val r = this.size.minDimension / 2f
        // Pierścień zewnętrzny z radełkowaniem
        drawCircle(Brush.radialGradient(listOf(Color(0xFF262626), Color(0xFF101010)), ctr, r), r, ctr)
        drawCircle(if (grip == 2) c.accent else Color(0x40FFFFFF), r - 1f, ctr, style = Stroke(if (grip == 2) 3f else 1.5f))
        rotate(outer, ctr) {
            for (i in 0 until 36) {
                rotate(i * 10f, ctr) {
                    drawLine(Color(0x33FFFFFF), Offset(ctr.x, ctr.y - r * 0.96f), Offset(ctr.x, ctr.y - r * 0.74f), 2f)
                }
            }
        }
        // Gałka wewnętrzna: jaśniejsza, z kreską położenia
        val ri = r * INNER
        drawCircle(Color.Black, ri + 3f, ctr)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF3A3A3A), Color(0xFF1A1A1A)), Offset(ctr.x - ri * 0.3f, ctr.y - ri * 0.3f), ri * 1.4f), ri, ctr)
        drawCircle(if (grip == 1) c.accent else Color(0x4DFFFFFF), ri, ctr, style = Stroke(if (grip == 1) 3f else 1f))
        rotate(inner, ctr) {
            drawLine(if (grip == 1) c.accent else Color.White, Offset(ctr.x, ctr.y - ri * 0.86f), Offset(ctr.x, ctr.y - ri * 0.45f), 5f, cap = StrokeCap.Round)
        }
    }
}

/** Mały okrągły przycisk −/+ z powtarzaniem przy przytrzymaniu (400 ms, potem co 90 ms). */
@Composable
private fun NudgeKey(label: String, desc: String, modifier: Modifier = Modifier, action: () -> Unit) {
    val c = Fm.c
    val scope = rememberCoroutineScope()
    val act by rememberUpdatedState(action)
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.size(36.dp).clip(CircleShape).background(if (pressed) c.ink else c.terrain)
            .semantics { contentDescription = desc; role = Role.Button; onClick { act(); true } }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    act()
                    val job = scope.launch { delay(400); while (true) { act(); delay(90) } }
                    waitForUpOrCancellation()
                    job.cancel()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (pressed) Color.Black else c.ink) }
}

/**
 * Klawisz trybu jak na FCU: lampka nad napisem świeci na zielono, gdy tryb jest aktywny.
 */
@Composable
fun AfdsKey(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Fm.c
    val haptic = LocalHapticFeedback.current
    Column(
        modifier.height(42.dp).clip(RoundedCornerShape(8.dp)).background(c.terrain)
            .semantics { contentDescription = label + if (on) loc(", włączony", ", on") else loc(", wyłączony", ", off") }
            .clickable(role = Role.Switch) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(22.dp).height(3.dp).clip(RoundedCornerShape(50)).background(if (on) c.green else Color(0x26FFFFFF)))
        Spacer(Modifier.height(5.dp))
        Text(label, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (on) c.green else c.ink, maxLines = 1)
    }
}

private data class Key(val label: String, val on: Boolean, val event: String)

/** Jedna nastawa autopilota: okienko z wartością, pokrętło, −/+ i klawisze trybów, które jej dotyczą. */
private data class Selector(
    val label: String, val unit: String, val value: String,
    val step: (Int) -> Unit, val big: (Int) -> Unit, val enter: String, val keys: List<Key>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorModule(sel: Selector, onEnter: (String) -> Unit, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Fm.c
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, c.rule, RoundedCornerShape(14.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Okienko wartości: dotknięcie = wpisanie liczby
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))
                .clickable(onClickLabel = loc("Wpisz wartość", "Type a value")) { onEnter(sel.enter) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sel.label, fontFamily = Mono, fontSize = 10.sp, color = c.ink2, modifier = Modifier.weight(1f), maxLines = 1)
                Text(sel.unit, fontFamily = Mono, fontSize = 10.sp, color = c.ink2)
            }
            Text(sel.value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = c.accent, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        // Pokrętło z −/+ w dolnych narożnikach
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            DualKnob(
                88.dp, loc("Pokrętło ${sel.label}: ${sel.value}", "${sel.label} knob: ${sel.value}"), Modifier.align(Alignment.TopCenter),
                onStep = sel.step, onBigStep = sel.big, onPush = { onEnter(sel.enter) },
            )
            NudgeKey("−", loc("${sel.label} w dół", "${sel.label} down"), Modifier.align(Alignment.BottomStart)) { sel.step(-1) }
            NudgeKey("+", loc("${sel.label} w górę", "${sel.label} up"), Modifier.align(Alignment.BottomEnd)) { sel.step(1) }
        }
        if (sel.keys.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 2) {
                sel.keys.forEach { k -> AfdsKey(k.label, k.on, Modifier.weight(1f)) { onKey(k.event) } }
                if (sel.keys.size % 2 == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Panel autopilota dopasowany do profilu samolotu, z wyborem profilu (automatycznie / ręcznie). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutopilotPanelV2(link: LinkClient, s: FlightState, override: AircraftProfile?, onOverride: (AircraftProfile?) -> Unit) {
    val c = Fm.c
    val detected = AircraftProfile.detect(s.aircraft.title, s.aircraft.atcModel, s.aircraft.category, s.aircraft.engineType)
    val p = override ?: detected
    val ap = s.autopilot
    val m = ap.modes
    var enter by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    fun cmd(n: String, v: Number? = null) { link.command(n, v?.toDouble()) }
    fun live() = link.state.value?.autopilot

    // Nagłówek: profil i jego zmiana
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        Text(p.short, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.ink).padding(horizontal = 10.dp, vertical = 3.dp))
        Spacer(Modifier.width(8.dp))
        Text(p.label + if (override == null) loc(", automatycznie", ", automatic") else "", style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.weight(1f), maxLines = 1)
        Box {
            Row(Modifier.clip(RoundedCornerShape(50)).clickable { menu = true }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(loc("Profil", "Profile"), style = MaterialTheme.typography.labelMedium, color = c.ink)
                Icon(Icons.Outlined.ExpandMore, null, tint = c.ink, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(loc("Automatycznie (${detected.label})", "Automatic (${detected.label})")) }, onClick = { onOverride(null); menu = false })
                AircraftProfile.entries.forEach { e -> DropdownMenuItem(text = { Text(e.label) }, onClick = { onOverride(e); menu = false }) }
            }
        }
    }

    // Klawisze główne (autopilot, dyrektor lotu, automat ciągu) — w jednym rzędzie nad pokrętłami
    val master = when (p) {
        AircraftProfile.AIRBUS -> listOf(Key("AP1", ap.master, "AP_MASTER"), Key("FD", ap.fd, "TOGGLE_FLIGHT_DIRECTOR"), Key("A/THR", ap.athr, "AUTO_THROTTLE_ARM"))
        AircraftProfile.BOEING -> listOf(Key("CMD A", ap.master, "AP_MASTER"), Key("F/D", ap.fd, "TOGGLE_FLIGHT_DIRECTOR"), Key("A/T ARM", ap.athr, "AUTO_THROTTLE_ARM"))
        AircraftProfile.BIZJET -> listOf(Key("AP", ap.master, "AP_MASTER"), Key("FD", ap.fd, "TOGGLE_FLIGHT_DIRECTOR"), Key("YD", false, "YAW_DAMPER_TOGGLE"), Key("A/T", ap.athr, "AUTO_THROTTLE_ARM"))
        AircraftProfile.TURBOPROP -> listOf(Key("AP", ap.master, "AP_MASTER"), Key("FD", ap.fd, "TOGGLE_FLIGHT_DIRECTOR"), Key("YD", false, "YAW_DAMPER_TOGGLE"))
        AircraftProfile.PISTON -> listOf(Key("AP", ap.master, "AP_MASTER"))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        master.forEach { k -> AfdsKey(k.label, k.on, Modifier.weight(1f)) { cmd(k.event) } }
    }
    Spacer(Modifier.size(10.dp))

    // Nastawy wysyłane jako wartości bezwzględne (*_SET) — zgubiony pakiet nie kumuluje błędu
    val spd = Selector(
        if (p == AircraftProfile.BOEING) "IAS/MACH" else "SPD", "kt", ap.spdKt?.let { "${it.roundToInt()}" } ?: "---",
        { d -> cmd("AP_SPD_VAR_SET", ControlMath.speed(live()?.spdKt, d)) },
        { d -> cmd("AP_SPD_VAR_SET", ControlMath.speed(live()?.spdKt, d * 10)) }, "SPD",
        when (p) {
            AircraftProfile.BOEING -> listOf(Key("N1", false, "AP_N1_HOLD"), Key("SPEED", false, "AP_PANEL_SPEED_HOLD"), Key("LVL CHG", m.flc, "FLIGHT_LEVEL_CHANGE"))
            AircraftProfile.AIRBUS -> emptyList()
            else -> listOf(Key("FLC", m.flc, "FLIGHT_LEVEL_CHANGE"))
        },
    )
    val hdg = Selector(
        if (p == AircraftProfile.BOEING) "HDG SEL" else "HDG", "°", ap.hdgDeg?.let { "%03d".format(it.roundToInt() % 360) } ?: "---",
        { d -> cmd("HEADING_BUG_SET", ControlMath.heading(live()?.hdgDeg, d)) },
        { d -> cmd("HEADING_BUG_SET", ControlMath.heading(live()?.hdgDeg, d * 10)) }, "HDG",
        when (p) {
            AircraftProfile.AIRBUS -> listOf(Key("HDG", m.hdg, "AP_HDG_HOLD"), Key("NAV", m.nav, "AP_NAV1_HOLD"), Key("LOC", m.loc, "AP_LOC_HOLD"), Key("APPR", m.apr, "AP_APR_HOLD"))
            AircraftProfile.BOEING -> listOf(Key("HDG SEL", m.hdg, "AP_HDG_HOLD"), Key("LNAV", m.nav, "AP_NAV1_HOLD"), Key("VOR/LOC", m.loc, "AP_LOC_HOLD"), Key("APP", m.apr, "AP_APR_HOLD"))
            AircraftProfile.PISTON -> listOf(Key("HDG", m.hdg, "AP_HDG_HOLD"), Key("NAV", m.nav, "AP_NAV1_HOLD"), Key("APR", m.apr, "AP_APR_HOLD"), Key("REV", false, "AP_BC_HOLD"))
            else -> listOf(Key("HDG", m.hdg, "AP_HDG_HOLD"), Key("NAV", m.nav, "AP_NAV1_HOLD"), Key("APR", m.apr, "AP_APR_HOLD"))
        },
    )
    val alt = Selector(
        "ALT", "ft", ap.altFt?.let { "${it.roundToInt()}" } ?: "-----",
        { d -> cmd("AP_ALT_VAR_SET_ENGLISH", ControlMath.altitude(live()?.altFt, d * 100)) },
        { d -> cmd("AP_ALT_VAR_SET_ENGLISH", ControlMath.altitude(live()?.altFt, d * 1000)) }, "ALT",
        when (p) {
            AircraftProfile.AIRBUS -> listOf(Key("ALT", m.alt, "AP_ALT_HOLD"), Key("OP CLB", m.flc, "FLIGHT_LEVEL_CHANGE"))
            AircraftProfile.BOEING -> listOf(Key("ALT HLD", m.alt, "AP_ALT_HOLD"))
            else -> listOf(Key("ALT", m.alt, "AP_ALT_HOLD"))
        },
    )
    val vs = Selector(
        if (p == AircraftProfile.AIRBUS || p == AircraftProfile.BOEING) "V/S" else "VS", "fpm", ap.vsFpm?.let { "%+d".format(it.roundToInt()) } ?: "----",
        { d -> cmd("AP_VS_VAR_SET_ENGLISH", ControlMath.verticalSpeed(live()?.vsFpm, d * 100)) },
        { d -> cmd("AP_VS_VAR_SET_ENGLISH", ControlMath.verticalSpeed(live()?.vsFpm, d * 500)) }, "VS",
        listOf(Key(if (p == AircraftProfile.AIRBUS || p == AircraftProfile.BOEING) "V/S" else "VS", m.vs, "AP_VS_HOLD")),
    )
    // Kolejność jak na panelu: prędkość, kurs, wysokość, prędkość pionowa (GA bez prędkości)
    val modules = if (p == AircraftProfile.PISTON) listOf(hdg, alt, vs) else listOf(spd, hdg, alt, vs)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Moduł potrzebuje ~168 dp; trzy kolumny przy czterech modułach zostawiłyby sierotę — wtedy dwie
        var cols = ((maxWidth + 8.dp) / 176.dp).toInt().coerceIn(1, modules.size)
        if (cols == 3 && modules.size == 4) cols = 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            modules.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { sel -> SelectorModule(sel, { enter = it }, { cmd(it) }, Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    Text(loc("Kręć palcem wokół pokrętła: pierścień zmienia o duży krok, środek o mały. Dotknij okienka, aby wpisać wartość.",
        "Turn a knob with your finger: the outer ring moves in big steps, the centre in small ones. Tap the window to type a value."),
        style = MaterialTheme.typography.labelSmall, color = c.ink2, modifier = Modifier.padding(top = 8.dp))

    when (enter) {
        "HDG" -> EnterDialog(loc("Kurs autopilota", "Autopilot heading"), loc("np. 270", "e.g. 270"), KeyboardType.Number, { it.toIntOrNull() in 0..359 }, { cmd("HEADING_BUG_SET", it.toInt()) }) { enter = null }
        "ALT" -> EnterDialog(loc("Wysokość (ft)", "Altitude (ft)"), loc("np. 12000", "e.g. 12000"), KeyboardType.Number, { it.toIntOrNull() in 0..50_000 }, { cmd("AP_ALT_VAR_SET_ENGLISH", it.toInt()) }) { enter = null }
        "SPD" -> EnterDialog(loc("Prędkość (kt)", "Speed (kt)"), loc("np. 250", "e.g. 250"), KeyboardType.Number, { it.toIntOrNull() in 0..500 }, { cmd("AP_SPD_VAR_SET", it.toInt()) }) { enter = null }
        "VS" -> EnterDialog(loc("Prędkość pionowa (ft/min)", "Vertical speed (ft/min)"), loc("np. -1500", "e.g. -1500"), KeyboardType.Number, { it.toIntOrNull() in -8000..8000 }, { cmd("AP_VS_VAR_SET_ENGLISH", it.toInt()) }) { enter = null }
    }
}
