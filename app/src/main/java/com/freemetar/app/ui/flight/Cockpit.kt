package com.freemetar.app.ui.flight

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
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
    nearby: List<com.freemetar.app.data.nav.FixResult> = emptyList(), floatingTrim: Boolean = false, leversLeft: Boolean = false,
    onShowPlan: () -> Unit = {},
    recording: com.freemetar.app.MainViewModel.RecordingInfo = com.freemetar.app.MainViewModel.RecordingInfo(),
    onStopRecording: () -> Unit = {}, onResumeRecording: () -> Unit = {},
) {
    var tabName by rememberSaveable { mutableStateOf<String?>(null) }
    val tab = CockpitTab.entries.firstOrNull { it.name == tabName }
    val effective = profile ?: AircraftProfile.detect(s.aircraft.title, s.aircraft.atcModel, s.aircraft.category, s.aircraft.engineType)

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Poziomo: pasek paneli pionowo z prawej, wyświetlacze na całą wysokość pod nagłówkiem
            val landscape = maxWidth > maxHeight * 1.3f
            val screenH = maxHeight
            val screenW = maxWidth
            // Szeroki ekran (tablet poziomo): stała kolumna paneli obok wyświetlaczy.
            // Tablet pionowo: wyświetlacze w rzędzie u góry, stały panel pod nimi. Panel nie zastępuje wtedy ND.
            val wide = maxWidth >= 840.dp && landscape
            val tabletPortrait = !landscape && maxWidth >= 600.dp
            val persistent = wide || tabletPortrait
            val shownTab = tab ?: if (persistent) CockpitTab.AP else null
            val onTab: (CockpitTab) -> Unit = { t -> tabName = if (tab == t && !persistent) null else t.name }
            val panelFor: @Composable (CockpitTab, Boolean) -> Unit = { t, compact ->
                when (t) {
                    CockpitTab.AP -> AutopilotPanelV2(link, s, profile, onProfile, compact = compact)
                    CockpitTab.RADIO -> RadioPanel(link, s)
                    CockpitTab.SYSTEMS -> SystemsPanel(link, s, effective)
                    CockpitTab.STICK -> FlightControlPanel(link, s, controlMode, onControlMode, floating, onFloating)
                    CockpitTab.ENGINES -> EnginePanel(link, s, effective)
                    CockpitTab.PROGRESS -> Progress(s, route)
                }
            }
            val panel: (@Composable () -> Unit)? = tab?.takeIf { !persistent }?.let { t ->
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
                CockpitHeader(link, s, effective, onShowPlan, controlMode, recording, onStopRecording, onResumeRecording)
                if (wide) {
                    val sideW = (screenW * 0.34f).coerceIn(340.dp, 480.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlyDeck(link, s, route, effective, nearby, screenH - 40.dp - 14.dp, Modifier.weight(1f), leversLeft = leversLeft)
                        Column(Modifier.width(sideW)) {
                            PanelBar(shownTab, onTab, indicatorTop = false)
                            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                                panelFor(shownTab!!, false)
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                } else if (tabletPortrait) {
                    FlyDeck(link, s, route, effective, nearby, screenH * 0.5f, leversLeft = leversLeft)
                    PanelBar(shownTab, onTab, indicatorTop = false)
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                        panelFor(shownTab!!, false)
                        Spacer(Modifier.height(16.dp))
                    }
                } else if (landscape) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlyDeck(link, s, route, effective, nearby, screenH - 40.dp - 14.dp, Modifier.weight(1f), panel, leversLeft)
                        PanelRail(tab, onTab)
                    }
                } else {
                    // Miejsce na wyświetlacze: wysokość ekranu minus nagłówek, pasek paneli i marginesy
                    FlyDeck(link, s, route, effective, nearby, screenH - 40.dp - 56.dp - 16.dp, panel = panel, leversLeft = leversLeft)
                    PanelBar(tab, onTab)
                }
            }
        }
        // Stery zawsze pod ręką w widoku lotu (kontroler sprzętowy ich nie potrzebuje)
        if (controlMode != "GAMEPAD") FloatingControl(link, controlMode, s, withTrim = floatingTrim, positionKey = "flight")
    }
}

/** Nagłówek: stan i nazwa samolotu, profil panelu, kamera MSFS jako trzy małe ikony. */
@Composable
private fun CockpitHeader(
    link: LinkClient, s: FlightState, p: AircraftProfile, onShowPlan: () -> Unit, controlMode: String,
    rec: com.freemetar.app.MainViewModel.RecordingInfo, onStop: () -> Unit, onResume: () -> Unit,
) {
    val c = Fm.c
    var askStop by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.aircraft.paused == true) c.caution else c.green))
        Spacer(Modifier.width(8.dp))
        Text(s.aircraft.title ?: tr("Samolot", "Aircraft"), style = MaterialTheme.typography.titleSmall, color = c.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        FlightParams(s, Modifier.weight(1f).padding(start = 10.dp))
        if (s.aircraft.paused == true) Text(tr("PAUZA", "PAUSED"), fontFamily = Mono, fontSize = 11.sp, color = c.caution, modifier = Modifier.padding(end = 6.dp))
        Text(p.short, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Black,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.ink).padding(horizontal = 7.dp, vertical = 2.dp))
        Spacer(Modifier.width(6.dp))
        RecordChip(rec, onClick = { if (rec.recording) askStop = true else if (rec.held) onResume() })
        Spacer(Modifier.width(6.dp))
        CameraSwitch(link, s, controlMode = controlMode)
        Spacer(Modifier.width(6.dp))
        // Plan lotu, połączenie i dziennik — zamiast osobnego przełącznika nad kokpitem
        Text(tr("PLAN", "PLAN"), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = c.ink,
            modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.rule, RoundedCornerShape(50))
                .clickable(role = Role.Button, onClickLabel = tr("Plan lotu i dziennik", "Flight plan and logbook"), onClick = onShowPlan)
                .padding(horizontal = 10.dp, vertical = 7.dp))
    }
    if (askStop) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askStop = false },
        title = { Text(tr("Zakończyć zapis lotu?", "Stop recording this flight?")) },
        text = { Text(tr("Lot trafi do dziennika. Nowy zapis zacznie się sam po wylądowaniu albo po wznowieniu (dotknij REC).",
            "The flight goes to the logbook. A new recording starts by itself after landing, or when you resume it (tap REC).")) },
        confirmButton = { androidx.compose.material3.TextButton({ askStop = false; onStop() }) { Text(tr("Zakończ zapis", "Stop recording"), color = c.danger) } },
        dismissButton = { androidx.compose.material3.TextButton({ askStop = false }) { Text(tr("Anuluj", "Cancel")) } },
    )
}

/**
 * Podstawowe parametry lotu w nagłówku: prędkość, wysokość, kurs, prędkość pionowa.
 * Gdy brakuje miejsca, pola od końca znikają, zamiast ściskać nazwę samolotu.
 */
@Composable
private fun FlightParams(s: FlightState, modifier: Modifier = Modifier) {
    val c = Fm.c
    fun n(v: Double?) = v?.roundToInt()?.toString() ?: "—"
    val vs = s.speed.vsFpm?.roundToInt()
    val items = listOf(
        "IAS" to n(s.speed.iasKt),
        "ALT" to n(s.position.altIndFt ?: s.position.altMslFt),
        "HDG" to (s.attitude.hdgMagDeg?.let { "%03d".format(((it.roundToInt() % 360) + 360) % 360) } ?: "—"),
        "VS" to (vs?.let { if (it > 0) "+$it" else "$it" } ?: "—"),
    )
    BoxWithConstraints(modifier) {
        val fit = (maxWidth / 66.dp).toInt().coerceIn(0, items.size)   // ok. 66 dp na pole
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            items.take(fit).forEach { (label, value) ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(label, fontFamily = Mono, fontSize = 9.sp, color = c.ink2)
                    Spacer(Modifier.width(3.dp))
                    Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = c.ink, maxLines = 1)
                }
            }
        }
    }
}

/** Podpis zakładki: krótki, ale słowem (AP zostaje skrótem, jak na panelu). */
@Composable
private fun CockpitTab.tabName(): String = when (this) {
    CockpitTab.AP -> "AP"
    CockpitTab.RADIO -> tr("Radio", "Radio")
    CockpitTab.SYSTEMS -> tr("Systemy", "Systems")
    CockpitTab.ENGINES -> tr("Silniki", "Engines")
    CockpitTab.STICK -> tr("Stery", "Controls")
    CockpitTab.PROGRESS -> tr("Postęp", "Progress")
}

/**
 * Zakładki w pionie (telefon poziomo, panel po lewej): aktywna ma jaśniejsze tło i kropkę przy ikonie
 * — bez pionowej belki z boku.
 */
@Composable
private fun PanelRail(tab: CockpitTab?, onTab: (CockpitTab) -> Unit) {
    val c = Fm.c
    Column(Modifier.width(58.dp).border(1.dp, c.rule, RoundedCornerShape(12.dp)).padding(vertical = 4.dp)) {
        CockpitTab.entries.forEach { t ->
            val on = t == tab
            val label = t.label()
            Column(
                Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 4.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (on) c.terrain else Color.Transparent)
                    .semantics { contentDescription = label }
                    .clickable(role = Role.Tab) { onTab(t) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box {
                    Icon(t.icon, null, tint = if (on) c.ink else c.ink2, modifier = Modifier.size(18.dp))
                    if (on) Box(Modifier.align(Alignment.TopEnd).padding(start = 18.dp).size(5.dp).clip(CircleShape).background(c.accent))
                }
                Text(t.tabName(), fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = if (on) c.ink else c.ink2, maxLines = 1)
            }
        }
    }
}

/**
 * Zakładki paneli: ikona z podpisem, aktywna ma jasny napis i linię wskaźnika od strony otwartego panelu
 * ([indicatorTop] = panel nad zakładkami, jak na telefonie; inaczej panel pod nimi, jak na tablecie).
 * Cienka linia oddziela zakładki od panelu. Na telefonie drugie dotknięcie chowa panel.
 */
@Composable
private fun PanelBar(tab: CockpitTab?, onTab: (CockpitTab) -> Unit, modifier: Modifier = Modifier, indicatorTop: Boolean = true) {
    val c = Fm.c
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (indicatorTop) Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
        Row(Modifier.fillMaxWidth().height(50.dp)) {
            CockpitTab.entries.forEach { t ->
                val on = t == tab
                val label = t.label()
                Box(
                    Modifier.weight(1f).fillMaxSize()
                        .semantics { contentDescription = label }
                        .clickable(role = Role.Tab) { onTab(t) },
                ) {
                    // Wskaźnik aktywnej zakładki przy krawędzi, od której otwiera się panel
                    if (on) Box(
                        Modifier.align(if (indicatorTop) Alignment.TopCenter else Alignment.BottomCenter)
                            .fillMaxWidth(0.62f).height(3.dp).clip(RoundedCornerShape(50)).background(c.ink),
                    )
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(t.icon, null, tint = if (on) c.ink else c.ink2, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(t.tabName(), fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 10.sp,
                            color = if (on) c.ink else c.ink2, maxLines = 1)
                    }
                }
            }
        }
        if (!indicatorTop) Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
    }
}

/**
 * Kamera MSFS: kabina, zewnętrzna (pościg) i dron — trzy kompaktowe ikony. Aktywny widok podświetlony
 * na podstawie CAMERA STATE z symulatora. Krótkie dotknięcie zmienia kamerę; **przytrzymanie** włącza
 * rozglądanie się tym samym sposobem co sterowanie: żyroskop (przechylanie telefonu od pozycji z chwili
 * naciśnięcia) albo przesuwanie palca. Puszczenie wraca z kamerą na wprost.
 */
@Composable
internal fun CameraSwitch(link: LinkClient, s: FlightState, modifier: Modifier = Modifier, controlMode: String = "TILT") {
    val c = Fm.c
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tiltMode = controlMode != "TOUCH"
    val tilt = rememberTilt(enabled = tiltMode)
    val cam = s.controls.cameraState
    val holdHint = tr(" — przytrzymaj, aby się rozglądać", " — hold to look around")
    var looking by remember { mutableStateOf(false) }
    var yaw by remember { mutableFloatStateOf(0f) }    // stopnie, + w prawo
    var pitch by remember { mutableFloatStateOf(0f) }  // stopnie, + w górę
    var roll0 by remember { mutableFloatStateOf(0f) }
    var pitch0 by remember { mutableFloatStateOf(0f) }

    // ok. 16 razy na sekundę dwie komendy (limit Link to 50/s); po puszczeniu jeden raz „na wprost”
    LaunchedEffect(looking) {
        if (!looking) return@LaunchedEffect
        try {
            while (isActive) {
                if (tiltMode) {
                    yaw = ((tilt.roll.toFloat() - roll0) * 3f).coerceIn(-180f, 180f)
                    pitch = ((tilt.pitch.toFloat() - pitch0) * 2.5f).coerceIn(-80f, 80f)
                }
                link.command("CAMERA_YAW_SET", yaw.roundToInt().toDouble())
                link.command("CAMERA_PITCH_SET", pitch.roundToInt().toDouble())
                delay(60)
            }
        } finally {
            link.command("CAMERA_YAW_SET", 0.0)
            link.command("CAMERA_PITCH_SET", 0.0)
        }
    }

    Row(modifier.clip(RoundedCornerShape(50)).border(1.dp, if (looking) c.accent else c.rule, RoundedCornerShape(50)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            Triple(2, tr("Kamera: kabina", "Camera: cockpit"), Icons.Outlined.AirlineSeatReclineNormal),
            Triple(3, tr("Kamera: zewnętrzna", "Camera: external"), Icons.Outlined.FlightTakeoff),
            Triple(4, tr("Kamera: dron", "Camera: drone"), Icons.Outlined.Videocam),
        ).forEach { (state, label, icon) ->
            val on = cam == state
            val desc = label + holdHint
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (on) c.ink else Color.Transparent)
                    .semantics {
                        contentDescription = desc
                        role = Role.RadioButton
                    }
                    .pointerInput(tiltMode) {
                        val reach = with(density) { 120.dp.toPx() }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            // Krótkie dotknięcie = zmiana kamery; przytrzymanie = rozglądanie się
                            val up = withTimeoutOrNull(300) { waitForUpOrCancellation() }
                            if (up != null) { link.command("CAMERA_STATE", state.toDouble()); return@awaitEachGesture }
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            roll0 = tilt.roll.toFloat(); pitch0 = tilt.pitch.toFloat()
                            yaw = 0f; pitch = 0f
                            looking = true
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                if (!tiltMode) {
                                    yaw = ((ch.position.x - down.position.x) / reach * 180f).coerceIn(-180f, 180f)
                                    pitch = (-(ch.position.y - down.position.y) / reach * 80f).coerceIn(-80f, 80f)
                                }
                            }
                            looking = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (on) c.paper else c.ink2, modifier = Modifier.size(17.dp)) }
        }
    }
}

/**
 * Wskaźnik zapisu lotu w nagłówku: „● REC 12:34” w czerwieni, gdy lot się nagrywa (dotknięcie — zakończ zapis),
 * „REC ‖” gdy wstrzymany po ręcznym zakończeniu (dotknięcie — wznów), nic przed startem.
 */
@Composable
private fun RecordChip(rec: com.freemetar.app.MainViewModel.RecordingInfo, onClick: () -> Unit) {
    val c = Fm.c
    if (!rec.recording && !rec.held) return
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(rec.recording) { while (rec.recording) { now = System.currentTimeMillis(); delay(1000) } }
    val min = rec.startedAt?.let { ((now - it) / 60_000).toInt() } ?: 0
    val label = if (rec.recording) "%d:%02d".format(min / 60, min % 60) else "REC ‖"
    Row(
        Modifier.clip(RoundedCornerShape(50)).border(1.dp, if (rec.recording) c.danger else c.rule, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = if (rec.recording) tr("Zakończ zapis lotu", "Stop recording the flight") else tr("Wznów zapis lotów", "Resume flight recording"), onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rec.recording) { Box(Modifier.size(7.dp).clip(CircleShape).background(c.danger)); Spacer(Modifier.width(5.dp)) }
        Text(label, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = if (rec.recording) c.danger else c.ink2, maxLines = 1)
    }
}
