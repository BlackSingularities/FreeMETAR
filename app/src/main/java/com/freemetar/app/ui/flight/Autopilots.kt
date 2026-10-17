package com.freemetar.app.ui.flight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.data.link.FlightState
import com.freemetar.app.data.link.LinkClient
import com.freemetar.app.domain.AircraftProfile
import com.freemetar.app.domain.ControlMath
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Okno nastawy działające jak pokrętło: przeciągnięcie w bok = obrót (mały krok co 14 dp),
 * dotknięcie lewej/prawej krawędzi = duży krok w dół/górę, przytrzymanie środka = wpisanie wartości.
 */
@Composable
fun KnobWindow(
    label: String, value: String, modifier: Modifier = Modifier,
    onStep: (Int) -> Unit, onBigStep: (Int) -> Unit, onEnter: () -> Unit,
) {
    val c = Fm.c
    val haptic = LocalHapticFeedback.current
    val stepPx = with(LocalDensity.current) { 14.dp.toPx() }
    var acc by remember { mutableFloatStateOf(0f) }
    val step by rememberUpdatedState(onStep)
    val big by rememberUpdatedState(onBigStep)
    val enter by rememberUpdatedState(onEnter)
    BoxWithConstraints(
        modifier.height(64.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black).border(1.dp, c.rule, RoundedCornerShape(12.dp))
            .semantics { contentDescription = "$label $value — przeciągnij w bok, aby zmienić; przytrzymaj, aby wpisać" },
    ) {
        val w = constraints.maxWidth.toFloat()
        Box(
            Modifier.matchParentSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = { acc = 0f }) { _, dx ->
                        acc += dx
                        while (abs(acc) >= stepPx) {
                            val dir = if (acc > 0) 1 else -1
                            acc -= dir * stepPx
                            step(dir)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { o ->
                            when {
                                o.x < w * 0.28f -> { big(-1); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                o.x > w * 0.72f -> { big(1); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            }
                        },
                        onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); enter() },
                    )
                },
        )
        Text("−", color = c.ink2, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp))
        Text("+", color = c.ink2, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = c.ink2, fontWeight = FontWeight.Medium)
            Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.accent)
        }
    }
}

/** Klawisz trybu: zielony, gdy tryb jest aktywny (jak lampka na panelu). Kompaktowy — 40 dp. */
@Composable
fun AfdsKey(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Fm.c
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(50)).background(if (on) c.green.copy(alpha = 0.16f) else c.terrain)
            .border(1.dp, if (on) c.green else Color.Transparent, RoundedCornerShape(50))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (on) c.green else c.ink, maxLines = 1)
    }
}

private data class Key(val label: String, val on: Boolean, val event: String)

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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text(p.short, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.ink).padding(horizontal = 10.dp, vertical = 3.dp))
        Spacer(Modifier.width(8.dp))
        Text(p.label + if (override == null) " · automatycznie" else "", style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.weight(1f), maxLines = 1)
        Box {
            Row(Modifier.clip(RoundedCornerShape(50)).clickable { menu = true }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Profil", style = MaterialTheme.typography.labelMedium, color = c.ink)
                Icon(Icons.Outlined.ExpandMore, null, tint = c.ink, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Automatycznie (${detected.label})") }, onClick = { onOverride(null); menu = false })
                AircraftProfile.entries.forEach { e -> DropdownMenuItem(text = { Text(e.label) }, onClick = { onOverride(e); menu = false }) }
            }
        }
    }

    // Klawisze trybów w kolejności jak na prawdziwym panelu
    val keys = when (p) {
        AircraftProfile.AIRBUS -> listOf(
            Key("LOC", m.loc, "AP_LOC_HOLD"), Key("AP1", ap.master, "AP_MASTER"), Key("A/THR", ap.athr, "AUTO_THROTTLE_ARM"),
            Key("APPR", m.apr, "AP_APR_HOLD"), Key("FD", ap.fd, "TOGGLE_FLIGHT_DIRECTOR"), Key("HDG", m.hdg, "AP_HDG_HOLD"),
            Key("NAV", m.nav, "AP_NAV1_HOLD"), Key("ALT", m.alt, "AP_ALT_HOLD"), Key("V/S", m.vs, "AP_VS_HOLD"), Key("OP CLB", m.flc, "FLIGHT_LEVEL_CHANGE"),
        )
        AircraftProfile.BOEING -> listOf(
            Key("A/T", ap.athr, "AUTO_THROTTLE_ARM"), Key("N1", false, "AP_N1_HOLD"), Key("SPEED", false, "AP_PANEL_SPEED_HOLD"),
            Key("LVL CHG", m.flc, "FLIGHT_LEVEL_CHANGE"), Key("HDG SEL", m.hdg, "AP_HDG_HOLD"), Key("LNAV", m.nav, "AP_NAV1_HOLD"),
            Key("VOR/LOC", m.loc, "AP_LOC_HOLD"), Key("APP", m.apr, "AP_APR_HOLD"), Key("ALT HLD", m.alt, "AP_ALT_HOLD"),
            Key("V/S", m.vs, "AP_VS_HOLD"), Key("CMD A", ap.master, "AP_MASTER"), Key("F/D", ap.fd, "TOGGLE_FLIGHT_DIRECTOR"),
        )
        AircraftProfile.BIZJET, AircraftProfile.TURBOPROP -> listOfNotNull(
            Key("AP", ap.master, "AP_MASTER"), Key("FD", ap.fd, "TOGGLE_FLIGHT_DIRECTOR"), Key("YD", false, "YAW_DAMPER_TOGGLE"),
            Key("HDG", m.hdg, "AP_HDG_HOLD"), Key("NAV", m.nav, "AP_NAV1_HOLD"), Key("APR", m.apr, "AP_APR_HOLD"),
            Key("ALT", m.alt, "AP_ALT_HOLD"), Key("VS", m.vs, "AP_VS_HOLD"), Key("FLC", m.flc, "FLIGHT_LEVEL_CHANGE"),
            if (p == AircraftProfile.BIZJET) Key("A/T", ap.athr, "AUTO_THROTTLE_ARM") else null,
        )
        AircraftProfile.PISTON -> listOf(
            Key("AP", ap.master, "AP_MASTER"), Key("HDG", m.hdg, "AP_HDG_HOLD"), Key("NAV", m.nav, "AP_NAV1_HOLD"),
            Key("APR", m.apr, "AP_APR_HOLD"), Key("REV", false, "AP_BC_HOLD"), Key("ALT", m.alt, "AP_ALT_HOLD"), Key("VS", m.vs, "AP_VS_HOLD"),
        )
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 5) {
        keys.forEach { k -> AfdsKey(k.label, k.on, Modifier.weight(1f)) { cmd(k.event) } }
    }
    Spacer(Modifier.size(10.dp))

    // Okna nastaw: na telefonie 2×2, szeroko w jednym rzędzie
    val windows = buildList<@Composable (Modifier) -> Unit> {
        if (p != AircraftProfile.PISTON) add { mod ->
            KnobWindow(if (p == AircraftProfile.BOEING) "IAS/MACH" else "SPD", ap.spdKt?.let { "${it.roundToInt()}" } ?: "---", mod,
                onStep = { d -> cmd("AP_SPD_VAR_SET", ControlMath.speed(live()?.spdKt, d)) },
                onBigStep = { d -> cmd("AP_SPD_VAR_SET", ControlMath.speed(live()?.spdKt, d * 10)) },
                onEnter = { enter = "SPD" })
        }
        add { mod ->
            KnobWindow(if (p == AircraftProfile.AIRBUS) "HDG" else "HEADING", ap.hdgDeg?.let { "%03d".format(it.roundToInt() % 360) } ?: "---", mod,
                onStep = { d -> cmd("HEADING_BUG_SET", ControlMath.heading(live()?.hdgDeg, d)) },
                onBigStep = { d -> cmd("HEADING_BUG_SET", ControlMath.heading(live()?.hdgDeg, d * 10)) },
                onEnter = { enter = "HDG" })
        }
        add { mod ->
            KnobWindow("ALT", ap.altFt?.let { "${it.roundToInt()}" } ?: "-----", mod,
                onStep = { d -> cmd("AP_ALT_VAR_SET_ENGLISH", ControlMath.altitude(live()?.altFt, d * 100)) },
                onBigStep = { d -> cmd("AP_ALT_VAR_SET_ENGLISH", ControlMath.altitude(live()?.altFt, d * 1000)) },
                onEnter = { enter = "ALT" })
        }
        add { mod ->
            KnobWindow(if (p == AircraftProfile.AIRBUS) "V/S" else "VERT SPD", ap.vsFpm?.let { "%+d".format(it.roundToInt()) } ?: "----", mod,
                onStep = { d -> cmd("AP_VS_VAR_SET_ENGLISH", ControlMath.verticalSpeed(live()?.vsFpm, d * 100)) },
                onBigStep = { d -> cmd("AP_VS_VAR_SET_ENGLISH", ControlMath.verticalSpeed(live()?.vsFpm, d * 500)) },
                onEnter = { enter = "VS" })
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= 520.dp) windows.size else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            windows.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { w -> w(Modifier.weight(1f)) }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    Text("Przeciągnij okno w bok, aby kręcić; dotknij krawędzi dla dużego kroku; przytrzymaj, aby wpisać.",
        style = MaterialTheme.typography.labelSmall, color = c.ink2, modifier = Modifier.padding(top = 6.dp))

    when (enter) {
        "HDG" -> EnterDialog("Kurs autopilota", "np. 270", KeyboardType.Number, { it.toIntOrNull() in 0..359 }, { cmd("HEADING_BUG_SET", it.toInt()) }) { enter = null }
        "ALT" -> EnterDialog("Wysokość (ft)", "np. 12000", KeyboardType.Number, { it.toIntOrNull() in 0..50_000 }, { cmd("AP_ALT_VAR_SET_ENGLISH", it.toInt()) }) { enter = null }
        "SPD" -> EnterDialog("Prędkość (kt)", "np. 250", KeyboardType.Number, { it.toIntOrNull() in 0..500 }, { cmd("AP_SPD_VAR_SET", it.toInt()) }) { enter = null }
        "VS" -> EnterDialog("Prędkość pionowa (ft/min)", "np. -1500", KeyboardType.Number, { it.toIntOrNull() in -8000..8000 }, { cmd("AP_VS_VAR_SET_ENGLISH", it.toInt()) }) { enter = null }
    }
}
