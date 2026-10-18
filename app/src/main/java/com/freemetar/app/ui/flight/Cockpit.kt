package com.freemetar.app.ui.flight

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freemetar.app.data.SavedRoute
import com.freemetar.app.data.link.FlightState
import com.freemetar.app.data.link.LinkClient
import com.freemetar.app.domain.AircraftProfile
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.Mono
import com.freemetar.app.ui.theme.tr

private enum class CockpitTab(val icon: ImageVector) {
    AP(Icons.Outlined.SettingsInputComponent), RADIO(Icons.Outlined.Radio), SYSTEMS(Icons.Outlined.ToggleOn),
    STICK(Icons.Outlined.Gamepad), ND(Icons.Outlined.Explore), ENGINES(Icons.Outlined.LocalGasStation), PROGRESS(Icons.Outlined.Route),
}

@Composable
private fun CockpitTab.label(): String = when (this) {
    CockpitTab.AP -> tr("Autopilot", "Autopilot")
    CockpitTab.RADIO -> tr("Radio", "Radio")
    CockpitTab.SYSTEMS -> tr("Systemy", "Systems")
    CockpitTab.STICK -> tr("Sterowanie", "Controls")
    CockpitTab.ND -> tr("Mapa ND", "ND map")
    CockpitTab.ENGINES -> tr("Silniki", "Engines")
    CockpitTab.PROGRESS -> tr("Postęp", "Progress")
}

/**
 * Kokpit: PFD zawsze na widoku, pod nim (telefon) lub obok (poziomo / tablet) panele.
 * Na szerokim ekranie z miejscem w pionie pod PFD jest też mapa ND.
 */
@Composable
fun CockpitView(
    link: LinkClient, s: FlightState, route: SavedRoute?, profile: AircraftProfile?, onProfile: (AircraftProfile?) -> Unit,
    controlMode: String = "TILT", onControlMode: (String) -> Unit = {}, floating: Boolean = false, onFloating: (Boolean) -> Unit = {},
) {
    val c = Fm.c
    var tab by rememberSaveable { mutableStateOf(CockpitTab.AP) }
    var range by rememberSaveable { mutableIntStateOf(40) }
    val effective = profile ?: AircraftProfile.detect(s.aircraft.title, s.aircraft.atcModel, s.aircraft.category, s.aircraft.engineType)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp || maxWidth > maxHeight
        val tallNd = wide && maxHeight >= 640.dp
        val statusBar: @Composable () -> Unit = { CockpitStatus(s, effective) }
        val panel: @Composable () -> Unit = {
            when (tab) {
                CockpitTab.AP -> AutopilotPanelV2(link, s, profile, onProfile)
                CockpitTab.RADIO -> RadioPanel(link, s)
                CockpitTab.SYSTEMS -> SystemsPanel(link, s, effective)
                CockpitTab.STICK -> FlightControlPanel(link, s, controlMode, onControlMode, floating, onFloating)
                CockpitTab.ND -> { NavDisplay(s, route?.fullPath().orEmpty(), range); RangeChips(range) { range = it } }
                CockpitTab.ENGINES -> Systems(s)
                CockpitTab.PROGRESS -> Progress(s, route)
            }
        }
        val tabs: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CockpitTab.entries.filter { !(tallNd && it == CockpitTab.ND) }.forEach { t ->
                    FilterChip(tab == t, { tab = t }, { Text(t.label()) }, leadingIcon = { Icon(t.icon, null, Modifier.size(16.dp)) })
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    statusBar()
                    Box(Modifier.clip(RoundedCornerShape(12.dp))) { Pfd(s) }
                    if (tallNd) {
                        Spacer(Modifier.size(10.dp))
                        Box(Modifier.clip(RoundedCornerShape(12.dp))) { NavDisplay(s, route?.fullPath().orEmpty(), range) }
                        RangeChips(range) { range = it }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    tabs()
                    Spacer(Modifier.size(10.dp))
                    panel()
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
                statusBar()
                Box(Modifier.clip(RoundedCornerShape(12.dp)).widthIn(max = 560.dp).align(Alignment.CenterHorizontally)) { Pfd(s) }
                Spacer(Modifier.size(10.dp))
                tabs()
                Spacer(Modifier.size(10.dp))
                panel()
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun CockpitStatus(s: FlightState, p: AircraftProfile) {
    val c = Fm.c
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.aircraft.paused == true) c.caution else c.green))
        Spacer(Modifier.width(8.dp))
        Text(s.aircraft.title ?: tr("Samolot", "Aircraft"), style = MaterialTheme.typography.titleSmall, color = c.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (s.aircraft.paused == true) Text(tr("PAUZA", "PAUSED"), fontFamily = Mono, fontSize = 11.sp, color = c.caution, modifier = Modifier.padding(end = 8.dp))
        Text(p.short, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Black,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.ink).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun RangeChips(range: Int, onRange: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(10, 20, 40, 80, 160).forEach { r -> FilterChip(range == r, { onRange(r) }, { Text("$r NM") }) }
    }
}
