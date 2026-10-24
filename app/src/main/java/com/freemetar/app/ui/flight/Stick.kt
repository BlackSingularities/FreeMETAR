package com.freemetar.app.ui.flight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.data.input.Gamepad
import com.freemetar.app.data.link.FlightState
import com.freemetar.app.data.link.LinkClient
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign

internal enum class StickMode { TOUCH, TILT, GAMEPAD }

/** Krzywa „expo” jak w aparaturach RC: precyzja przy środku, pełne wychylenie na końcu. */
internal fun expo(v: Double, e: Double): Double = sign(v) * (abs(v).pow(1 + e * 2))

/**
 * Sterowanie lotem: drążek na ekranie, przechylanie telefonu albo fizyczny kontroler.
 * Wszystkie tryby wysyłają osie 20 Hz tylko w trakcie sterowania; koniec = natychmiastowe zwolnienie osi.
 */
@Composable
fun FlightControlPanel(
    link: LinkClient, s: FlightState,
    modeName: String = "TILT", onMode: (String) -> Unit = {},
    floating: Boolean = false, onFloating: (Boolean) -> Unit = {},
) {
    val c = Fm.c
    // Domyślnie żyroskop: telefon trzymany jak wolant jest naturalniejszy niż drążek pod kciukiem
    val mode = StickMode.entries.firstOrNull { it.name == modeName } ?: StickMode.TILT
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        FilterChip(mode == StickMode.TILT, { onMode(StickMode.TILT.name) }, { Text(tr("Żyroskop", "Gyro")) })
        FilterChip(mode == StickMode.TOUCH, { onMode(StickMode.TOUCH.name) }, { Text(tr("Drążek", "Stick")) })
        FilterChip(mode == StickMode.GAMEPAD, { onMode(StickMode.GAMEPAD.name) }, { Text(tr("Kontroler", "Controller")) })
    }
    if (mode != StickMode.GAMEPAD) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(tr("Pływający przycisk sterowania", "Floating control button"), style = MaterialTheme.typography.bodyMedium, color = c.ink)
            Text(tr("Stery pod ręką na każdej zakładce. Przesuwasz go za uchwyt pod spodem.", "Controls on every tab. Move it by the handle underneath."),
                style = MaterialTheme.typography.labelSmall, color = c.ink2)
        }
        Switch(floating, onFloating)
    }
    when (mode) {
        StickMode.TOUCH -> TouchStick(link, s)
        StickMode.TILT -> YokePanel(link, s)
        StickMode.GAMEPAD -> GamepadPanel(link)
    }
    BrakeAxes(link, s)
    Text(
        tr("Link ogranicza wychylenie do ±${(link.limits.maxDeflection * 100).toInt()}% i oddaje stery po 0,5 s bez sygnału.",
            "Link limits deflection to ±${(link.limits.maxDeflection * 100).toInt()}% and releases the controls after 0.5 s without input."),
        style = MaterialTheme.typography.labelSmall, color = c.ink2, modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun TouchStick(link: LinkClient, s: FlightState) {
    val c = Fm.c
    val haptic = LocalHapticFeedback.current
    var x by remember { mutableFloatStateOf(0f) }   // −1…1
    var y by remember { mutableFloatStateOf(0f) }   // −1…1, + = drążek do siebie
    var rudder by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf(false) }
    var expoAmt by rememberSaveable { mutableFloatStateOf(0.4f) }
    val max = link.limits.maxDeflection
    val stickDesc = tr("Drążek sterowy — przeciągnij, aby sterować lotkami i sterem wysokości", "Control stick — drag to move ailerons and elevator")

    LaunchedEffect(active) {
        if (!active) { link.releaseAxes(); return@LaunchedEffect }
        while (isActive && active) {
            link.axis(
                (expo(x.toDouble(), expoAmt.toDouble()) * max),
                (expo(y.toDouble(), expoAmt.toDouble()) * max),
                (rudder.toDouble() * max),
            )
            delay(50)
        }
    }
    DisposableEffect(Unit) { onDispose { link.releaseAxes() } }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // Pole drążka: przeciągnięcie przesuwa gałkę, puszczenie wraca do środka
        Box(
            Modifier.weight(1f).widthIn(max = 360.dp).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color.Black)
                .border(1.dp, if (active) c.accent else c.rule, RoundedCornerShape(20.dp))
                .semantics { contentDescription = stickDesc }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        active = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val r = size.width / 2f
                        fun setFrom(o: Offset) {
                            var dx = (o.x - r) / (r * 0.85f); var dy = (o.y - r) / (r * 0.85f)
                            val len = hypot(dx, dy)
                            if (len > 1f) { dx /= len; dy /= len }
                            x = dx; y = dy  // w dół ekranu = drążek do siebie = nos w górę
                        }
                        setFrom(down.position)
                        do {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            if (ch.positionChange() != Offset.Zero) setFrom(ch.position)
                            ch.consume()
                        } while (true)
                        x = 0f; y = 0f; active = false
                    }
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val ctr = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension / 2 * 0.85f
                drawCircle(c.rule, r, ctr, style = Stroke(1.5f))
                drawCircle(c.rule, r * max.toFloat(), ctr, style = Stroke(1f))
                drawLine(c.rule, Offset(ctr.x - r, ctr.y), Offset(ctr.x + r, ctr.y), 1f)
                drawLine(c.rule, Offset(ctr.x, ctr.y - r), Offset(ctr.x, ctr.y + r), 1f)
                val knob = Offset(ctr.x + x * r, ctr.y + y * r)
                drawLine(c.ink2, ctr, knob, 3f)
                drawCircle(if (active) c.accent else c.ink, size.minDimension * 0.07f, knob)
            }
        }
        // Wskaźniki osi: lotki, ster wysokości, ster kierunku
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AxisReadout(tr("Lotki", "Ail"), x.toDouble())
            AxisReadout(tr("Ster wys.", "Elev"), y.toDouble())
            AxisReadout(tr("Ster kier.", "Rud"), rudder.toDouble())
            Text(if (s.axes.active) tr("Link: sterujesz", "Link: in control") else tr("Link: wolne", "Link: idle"),
                fontSize = 11.sp, color = if (s.axes.active) c.accent else c.ink2)
        }
    }
    Spacer(Modifier.size(10.dp))
    Text(tr("Ster kierunku (wraca do środka)", "Rudder (springs back)"), style = MaterialTheme.typography.labelMedium, color = c.ink2)
    Slider(rudder, { rudder = it; active = true }, valueRange = -1f..1f, onValueChangeFinished = { rudder = 0f; if (x == 0f && y == 0f) active = false })
    Text(tr("Krzywa (expo) %.1f".format(expoAmt), "Curve (expo) %.1f".format(expoAmt)), style = MaterialTheme.typography.labelMedium, color = c.ink2)
    Slider(expoAmt, { expoAmt = it }, valueRange = 0f..1f, steps = 9)
}

@Composable
private fun AxisReadout(label: String, v: Double) {
    val c = Fm.c
    Column(horizontalAlignment = Alignment.End) {
        Text(label, fontSize = 10.sp, color = c.ink2)
        Text("%+.2f".format(v), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
    }
}

@Composable
private fun GamepadPanel(link: LinkClient) {
    val c = Fm.c
    val g by Gamepad.state.collectAsStateWithLifecycle()
    var enabled by rememberSaveable { mutableStateOf(false) }
    var buttons by rememberSaveable { mutableStateOf(true) }
    val names = remember(g.deviceName) { Gamepad.connectedNames() }
    val state by rememberUpdatedState(g)

    LaunchedEffect(enabled) {
        if (!enabled) { link.releaseAxes(); return@LaunchedEffect }
        var brakeWasOn = false
        while (isActive && enabled) {
            val st = state
            link.axis(st.roll * link.limits.maxDeflection, st.pitch * link.limits.maxDeflection, st.yaw * link.limits.maxDeflection)
            // Spusty L2/R2 = hamulce kół (osobna oś); puszczone wysyłają jedno 0
            if (st.brake > 0 || brakeWasOn) link.brakes(brake = st.brake)
            brakeWasOn = st.brake > 0
            delay(50)
        }
    }
    // Przyciski kontrolera → komendy z listy dozwolonych
    LaunchedEffect(g.lastButtonAt) {
        if (enabled && buttons) g.lastButton?.let { k -> Gamepad.buttonCommands[k]?.let { link.command(it) } }
    }
    DisposableEffect(Unit) { onDispose { link.releaseAxes() } }

    Text(
        names.firstOrNull()?.let { tr("Kontroler: $it", "Controller: $it") }
            ?: tr("Nie wykryto kontrolera. Podłącz gamepad lub joystick przez Bluetooth albo USB-C.", "No controller detected. Connect a gamepad or joystick via Bluetooth or USB-C."),
        style = MaterialTheme.typography.titleSmall, color = if (names.isEmpty()) c.caution else c.ink,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(52.dp)) {
        Text(tr("Steruj kontrolerem", "Fly with the controller"), style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
        Switch(enabled, { enabled = it })
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
        Text(tr("Przyciski → komendy samolotu", "Buttons → aircraft commands"), style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
        Switch(buttons, { buttons = it })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AxisReadout(tr("Lotki", "Ail"), g.roll); AxisReadout(tr("Ster wys.", "Elev"), g.pitch); AxisReadout(tr("Ster kier.", "Rud"), g.yaw)
        AxisReadout(tr("Hamulce", "Brakes"), g.brake)
    }
    Text(
        tr("Y podwozie · L1/R1 klapy · L2/R2 hamulce kół · X autopilot · B hamulec postojowy · Select spoilery · krzyżak ↑↓ trym",
            "Y gear · L1/R1 flaps · L2/R2 wheel brakes · X autopilot · B parking brake · Select spoilers · D-pad ↑↓ trim"),
        style = MaterialTheme.typography.labelSmall, color = c.ink2, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
    )
}
