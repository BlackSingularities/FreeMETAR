package com.freemetar.app.ui.flight

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.data.link.FlightState
import com.freemetar.app.data.link.LinkClient
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Dwie osobne osie: hamulec aerodynamiczny (spoilery, zostaje w ustawionej pozycji jak dźwignia)
 * i hamulce kół (jak pedały: działają, dopóki trzymasz, puszczone wracają do zera; tylko na ziemi).
 */
@Composable
internal fun BrakeAxes(link: LinkClient, s: FlightState) {
    val c = Fm.c
    val onGround = s.aircraft.onGround == true
    // Spoilery: podczas przeciągania pokazuje pozycję palca, potem stan z symulatora
    var sbDrag by remember { mutableStateOf<Float?>(null) }
    val sbSim = ((s.controls.spoilersPct ?: 0.0) / 100).toFloat().coerceIn(0f, 1f)
    val sb = sbDrag ?: sbSim
    var brake by remember { mutableFloatStateOf(0f) }

    // Hamulce kół: wysyłane co 50 ms, dopóki są wciśnięte; puszczenie (albo zamknięcie panelu) = jedno 0.
    // Nic nie idzie, dopóki ich nie dotkniesz — nie nadpisujemy pedałów podłączonych do symulatora.
    LaunchedEffect(brake > 0f) {
        if (brake <= 0f) return@LaunchedEffect
        try {
            while (isActive) {
                link.brakes(brake = brake.toDouble())
                delay(50)
            }
        } finally {
            link.brakes(brake = 0.0)
        }
    }
    // W powietrzu hamulce kół nie działają — suwak wraca do zera
    LaunchedEffect(onGround) { if (!onGround) brake = 0f }

    val sbDesc = tr("Hamulec aerodynamiczny, oś", "Speed brake axis")
    val brakeDesc = tr("Hamulce kół, oś — wraca do zera po puszczeniu", "Wheel brakes axis — springs back on release")
    Column(Modifier.padding(top = 10.dp)) {
        AxisRow(tr("Hamulec aerodynamiczny", "Speed brake"), "${(sb * 100).roundToInt()}%", c.ink)
        Slider(
            sb, { v -> sbDrag = v; link.brakes(speedbrake = v.toDouble()) },
            onValueChangeFinished = { sbDrag?.let { link.brakes(speedbrake = it.toDouble()) }; sbDrag = null },
            modifier = Modifier.semantics { contentDescription = sbDesc },
        )
        AxisRow(
            tr("Hamulce kół", "Wheel brakes"),
            if (onGround) "${(brake * 100).roundToInt()}%" else tr("tylko na ziemi", "ground only"),
            if (brake > 0f) c.caution else c.ink2,
        )
        Slider(
            brake, { brake = it }, enabled = onGround,
            onValueChangeFinished = { brake = 0f },
            modifier = Modifier.semantics { contentDescription = brakeDesc },
        )
    }
}

@Composable
private fun AxisRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    val c = Fm.c
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.weight(1f))
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
    }
}
