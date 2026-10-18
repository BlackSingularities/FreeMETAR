package com.freemetar.app.ui.flight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.freemetar.app.data.link.LinkClient
import com.freemetar.app.domain.ControlMath
import com.freemetar.app.domain.loc
import com.freemetar.app.ui.theme.Fm
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Pływający przycisk sterowania nad całą aplikacją. Przytrzymanie = sterujesz:
 * w trybie żyroskopu przechylasz telefon (zero to pozycja z chwili naciśnięcia),
 * w trybie drążka przesuwasz kciuk od miejsca naciśnięcia. Puszczenie od razu oddaje stery.
 * Uchwyt pod przyciskiem służy do przestawienia go w inne miejsce ekranu.
 */
@Composable
fun FloatingControl(link: LinkClient, modeName: String, modifier: Modifier = Modifier) {
    val c = Fm.c
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val tiltMode = modeName != StickMode.TOUCH.name     // kontroler sprzętowy nie potrzebuje przycisku — wtedy żyroskop
    var active by remember { mutableStateOf(false) }
    var x by remember { mutableFloatStateOf(0f) }        // −1…1 lotki
    var y by remember { mutableFloatStateOf(0f) }        // −1…1 ster wysokości, + = nos w górę
    var roll0 by remember { mutableDoubleStateOf(0.0) }
    var pitch0 by remember { mutableDoubleStateOf(0.0) }
    val tilt = rememberTilt(enabled = tiltMode)
    val max = link.limits.maxDeflection

    // Pozycja przycisku (ułamek dostępnego obszaru) przetrwa obrót ekranu i zmianę zakładki
    var fx by rememberSaveable { mutableFloatStateOf(1f) }
    var fy by rememberSaveable { mutableFloatStateOf(0.62f) }

    fun axes(): Pair<Double, Double> = if (tiltMode) {
        ControlMath.axis(tilt.roll - roll0, 35.0, 2.0, 1.0, max) to ControlMath.axis(tilt.pitch - pitch0, 30.0, 2.0, 1.0, max)
    } else {
        expo(x.toDouble(), 0.4) * max to expo(y.toDouble(), 0.4) * max
    }

    LaunchedEffect(active) {
        if (!active) { link.releaseAxes(); return@LaunchedEffect }
        while (isActive && active) {
            val (a, e) = axes()
            link.axis(a, e, 0.0)
            delay(50)
        }
    }
    DisposableEffect(Unit) { onDispose { link.releaseAxes() } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val btn = 68.dp
        val total = btn + 22.dp   // przycisk + uchwyt
        val pad = 12.dp
        val wPx = with(density) { (maxWidth - btn - pad * 2).toPx() }.coerceAtLeast(1f)
        val hPx = with(density) { (maxHeight - total - pad * 2).toPx() }.coerceAtLeast(1f)
        val padPx = with(density) { pad.toPx() }
        val reach = with(density) { 56.dp.toPx() }

        Column(
            Modifier.offset { IntOffset((padPx + fx * wPx).roundToInt(), (padPx + fy * hPx).roundToInt()) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(btn)
                    .semantics {
                        contentDescription = if (tiltMode) loc("Sterowanie żyroskopem: przytrzymaj i przechylaj telefon", "Gyro control: hold and tilt the phone")
                        else loc("Drążek: przytrzymaj i przesuwaj kciuk", "Stick: hold and move your thumb")
                    }
                    .pointerInput(tiltMode) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            roll0 = tilt.roll; pitch0 = tilt.pitch
                            x = 0f; y = 0f
                            active = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                if (!tiltMode) {
                                    var dx = (ch.position.x - down.position.x) / reach
                                    var dy = (ch.position.y - down.position.y) / reach
                                    val len = hypot(dx, dy)
                                    if (len > 1f) { dx /= len; dy /= len }
                                    x = dx; y = dy   // kciuk w dół = drążek do siebie = nos w górę
                                }
                            }
                            active = false
                            x = 0f; y = 0f
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val (ail, elv) = if (active) axes() else 0.0 to 0.0
                Canvas(Modifier.matchParentSize()) {
                    val r = size.minDimension / 2f
                    // Przy aktywnym drążku pokaż zasięg kciuka poza przyciskiem
                    if (active && !tiltMode) drawCircle(Color(0x33FFFFFF), reach, center, style = Stroke(2f))
                    drawCircle(Color.Black, r, center)
                    drawCircle(if (active) c.accent else Color(0x66FFFFFF), r - 1.5f, center, style = Stroke(if (active) 4f else 2f))
                    if (active) {
                        val k = if (tiltMode) r * 0.8f else reach
                        drawCircle(c.accent, 9.dp.toPx(), center + Offset((ail / max * k).toFloat().coerceIn(-k, k), (elv / max * k).toFloat().coerceIn(-k, k)))
                    }
                }
                if (!active) Icon(if (tiltMode) Icons.Outlined.ScreenRotation else Icons.Outlined.Gamepad, null, tint = c.ink, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(6.dp))
            // Uchwyt do przestawiania
            Box(
                Modifier.width(36.dp).height(16.dp).clip(RoundedCornerShape(50)).background(c.terrain).border(1.dp, c.rule, RoundedCornerShape(50))
                    .semantics { contentDescription = loc("Uchwyt: przeciągnij, aby przesunąć przycisk", "Handle: drag to move the button") }
                    .pointerInput(wPx, hPx) {
                        detectDragGestures { ch, d ->
                            ch.consume()
                            fx = (fx + d.x / wPx).coerceIn(0f, 1f)
                            fy = (fy + d.y / hPx).coerceIn(0f, 1f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(16.dp).height(3.dp).clip(CircleShape).background(c.ink2))
            }
        }
    }
}
