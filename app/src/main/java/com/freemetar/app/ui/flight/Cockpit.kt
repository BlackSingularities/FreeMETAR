package com.freemetar.app.ui.flight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirlineSeatReclineNormal
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** Panele pod widokiem lotu — otwierane, gdy są potrzebne, drugie dotknięcie chowa. */
private enum class CockpitTab(val icon: ImageVector, val short: String) {
    AP(Icons.Outlined.SettingsInputComponent, "AP"), RADIO(Icons.Outlined.Radio, "RAD"), SYSTEMS(Icons.Outlined.ToggleOn, "SYS"),
    ENGINES(Icons.Outlined.LocalGasStation, "ENG"), STICK(Icons.Outlined.Gamepad, "CTL"), PROGRESS(Icons.Outlined.Route, "PRG"),
}

@Composable
private fun CockpitTab.label(): String = when (this) {
    CockpitTab.AP -> tr("Autopilot", "Autopilot")
    CockpitTab.RADIO -> tr("Radio", "Radio")
    CockpitTab.SYSTEMS -> tr("Systemy", "Systems")
    CockpitTab.STICK -> tr("Sterowanie", "Controls")
    CockpitTab.ENGINES -> tr("Silniki", "Engines")
    CockpitTab.PROGRESS -> tr("Postęp", "Progress")
}

/**
 * Kokpit = jeden widok do latania: PFD, ND i dźwignie mocy na ekranie naraz (bez przewijania),
 * nad nimi pływający przycisk sterowania. Autopilot, radio, systemy itd. otwierają się z paska ikon pod spodem.
 */
@Composable
fun CockpitView(
    link: LinkClient, s: FlightState, route: SavedRoute?, profile: AircraftProfile?, onProfile: (AircraftProfile?) -> Unit,
    controlMode: String = "TILT", onControlMode: (String) -> Unit = {}, floating: Boolean = false, onFloating: (Boolean) -> Unit = {},
    nearby: List<com.freemetar.app.data.nav.FixResult> = emptyList(), floatingTrim: Boolean = false,
    onShowPlan: () -> Unit = {},
) {
    var tabName by rememberSaveable { mutableStateOf<String?>(null) }
    val tab = CockpitTab.entries.firstOrNull { it.name == tabName }
    val effective = profile ?: AircraftProfile.detect(s.aircraft.title, s.aircraft.atcModel, s.aircraft.category, s.aircraft.engineType)

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Poziomo: pasek paneli pionowo z prawej, wyświetlacze na całą wysokość pod nagłówkiem
            val landscape = maxWidth > maxHeight * 1.3f
            val screenH = maxHeight
            val onTab: (CockpitTab) -> Unit = { t -> tabName = if (tab == t) null else t.name }
            val panel: (@Composable () -> Unit)? = tab?.let { t ->
                {
                    when (t) {
                        CockpitTab.AP -> AutopilotPanelV2(link, s, profile, onProfile, compact = true)
                        CockpitTab.RADIO -> RadioPanel(link, s)
                        CockpitTab.SYSTEMS -> SystemsPanel(link, s, effective)
                        CockpitTab.STICK -> FlightControlPanel(link, s, controlMode, onControlMode, floating, onFloating)
                        CockpitTab.ENGINES -> EnginePanel(link, s, effective)
                        CockpitTab.PROGRESS -> Progress(s, route)
                    }
                }
            }
            // Wszystko na jednym ekranie: otwarty panel zastępuje ND, PFD i dźwignie zostają
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp)) {
                CockpitHeader(link, s, effective, onShowPlan)
                if (landscape) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlyDeck(link, s, route, effective, nearby, screenH - 40.dp - 14.dp, Modifier.weight(1f), panel)
                        PanelRail(tab, onTab)
                    }
                } else {
                    // Miejsce na wyświetlacze: wysokość ekranu minus nagłówek, pasek paneli i marginesy
                    FlyDeck(link, s, route, effective, nearby, screenH - 40.dp - 56.dp - 16.dp, panel = panel)
                    PanelBar(tab, onTab)
                }
            }
        }
        // Stery zawsze pod ręką w widoku lotu (kontroler sprzętowy ich nie potrzebuje)
        if (controlMode != "GAMEPAD") FloatingControl(link, controlMode, s, withTrim = floatingTrim)
    }
}

/** Nagłówek: stan i nazwa samolotu, profil panelu, kamera MSFS jako trzy małe ikony. */
@Composable
private fun CockpitHeader(link: LinkClient, s: FlightState, p: AircraftProfile, onShowPlan: () -> Unit) {
    val c = Fm.c
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.aircraft.paused == true) c.caution else c.green))
        Spacer(Modifier.width(8.dp))
        Text(s.aircraft.title ?: tr("Samolot", "Aircraft"), style = MaterialTheme.typography.titleSmall, color = c.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (s.aircraft.paused == true) Text(tr("PAUZA", "PAUSED"), fontFamily = Mono, fontSize = 11.sp, color = c.caution, modifier = Modifier.padding(end = 6.dp))
        Text(p.short, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Black,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.ink).padding(horizontal = 7.dp, vertical = 2.dp))
        Spacer(Modifier.width(8.dp))
        CameraSwitch(link, s)
        Spacer(Modifier.width(6.dp))
        // Plan lotu, połączenie i dziennik — zamiast osobnego przełącznika nad kokpitem
        Text(tr("PLAN", "PLAN"), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = c.ink,
            modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.rule, RoundedCornerShape(50))
                .clickable(role = Role.Button, onClickLabel = tr("Plan lotu i dziennik", "Flight plan and logbook"), onClick = onShowPlan)
                .padding(horizontal = 10.dp, vertical = 7.dp))
    }
}

/** Pasek paneli w pionie (ekran poziomy): te same ikony co [PanelBar], przy prawej krawędzi. */
@Composable
private fun PanelRail(tab: CockpitTab?, onTab: (CockpitTab) -> Unit) {
    val c = Fm.c
    Column(Modifier.width(52.dp).clip(RoundedCornerShape(12.dp)).background(c.terrain).padding(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        CockpitTab.entries.forEach { t ->
            val on = t == tab
            val label = t.label()
            Column(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(9.dp)).background(if (on) c.ink else Color.Transparent)
                    .semantics { contentDescription = label }
                    .clickable(role = Role.Tab) { onTab(t) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(t.icon, null, tint = if (on) c.paper else c.ink2, modifier = Modifier.size(16.dp))
                Text(t.short, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = if (on) c.paper else c.ink2)
            }
        }
    }
}

/** Pasek paneli: ikony z krótkim podpisem; aktywny panel podświetlony, drugie dotknięcie chowa. */
@Composable
private fun PanelBar(tab: CockpitTab?, onTab: (CockpitTab) -> Unit) {
    val c = Fm.c
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.terrain).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        CockpitTab.entries.forEach { t ->
            val on = t == tab
            val label = t.label()
            Column(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(9.dp)).background(if (on) c.ink else Color.Transparent)
                    .semantics { contentDescription = label }
                    .clickable(role = Role.Tab) { onTab(t) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(t.icon, null, tint = if (on) c.paper else c.ink2, modifier = Modifier.size(17.dp))
                Text(t.short, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = if (on) c.paper else c.ink2)
            }
        }
    }
}

/**
 * Kamera MSFS: kabina, zewnętrzna (pościg) i dron — trzy kompaktowe ikony. Aktywny widok podświetlony
 * na podstawie CAMERA STATE z symulatora, więc zmiana klawiaturą w MSFS też się tu pokaże.
 */
@Composable
internal fun CameraSwitch(link: LinkClient, s: FlightState, modifier: Modifier = Modifier) {
    val c = Fm.c
    val cam = s.controls.cameraState
    Row(modifier.clip(RoundedCornerShape(50)).border(1.dp, c.rule, RoundedCornerShape(50)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(
            Triple(2, tr("Kamera: kabina", "Camera: cockpit"), Icons.Outlined.AirlineSeatReclineNormal),
            Triple(3, tr("Kamera: zewnętrzna", "Camera: external"), Icons.Outlined.FlightTakeoff),
            Triple(4, tr("Kamera: dron", "Camera: drone"), Icons.Outlined.Videocam),
        ).forEach { (state, label, icon) ->
            val on = cam == state
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (on) c.ink else Color.Transparent)
                    .clickable(role = Role.RadioButton, onClickLabel = label) { link.command("CAMERA_STATE", state.toDouble()) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (on) c.paper else c.ink2, modifier = Modifier.size(17.dp)) }
        }
    }
}
