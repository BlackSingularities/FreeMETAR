package com.freemetar.app.ui.screens

import androidx.compose.foundation.layout.wrapContentWidth

import com.freemetar.app.domain.loc

import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.AUTHOR_SITE
import com.freemetar.app.ui.theme.AUTHOR_URL
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freemetar.app.BuildConfig
import com.freemetar.app.DbState
import com.freemetar.app.MainViewModel
import com.freemetar.app.data.AltUnit
import com.freemetar.app.data.PressureUnit
import com.freemetar.app.ui.components.SectionCard
import java.text.DateFormat
import java.util.Date

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val db by vm.dbState.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 760.dp)
            .statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(loc("Ustawienia", "Settings"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))

        SectionCard(loc("Jednostki", "Units"), icon = Icons.Outlined.Straighten) {
            Text(loc("Ciśnienie", "Pressure"), color = Fm.c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.pressureUnit == PressureUnit.HPA, { vm.setPressure(PressureUnit.HPA) }, { Text("hPa") }, leadingIcon = { Icon(Icons.Outlined.Compress, null, Modifier.size(16.dp)) })
                FilterChip(s.pressureUnit == PressureUnit.INHG, { vm.setPressure(PressureUnit.INHG) }, { Text("inHg") }, leadingIcon = { Icon(Icons.Outlined.Compress, null, Modifier.size(16.dp)) })
            }
            Spacer(Modifier.size(8.dp))
            Text(loc("Wysokość", "Altitude"), color = Fm.c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.altUnit == AltUnit.FT, { vm.setAlt(AltUnit.FT) }, { Text(loc("stopy", "feet")) }, leadingIcon = { Icon(Icons.Outlined.Height, null, Modifier.size(16.dp)) })
                FilterChip(s.altUnit == AltUnit.M, { vm.setAlt(AltUnit.M) }, { Text(loc("metry", "metres")) }, leadingIcon = { Icon(Icons.Outlined.Height, null, Modifier.size(16.dp)) })
            }
        }

        SectionCard(loc("Język", "Language"), icon = Icons.Outlined.Translate) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SYSTEM" to loc("Jak system", "System default"), "PL" to "Polski", "EN" to "English").forEach { (code, label) ->
                    FilterChip(s.language == code, { vm.setLanguage(code) }, { Text(label) })
                }
            }
        }

        SectionCard(loc("Wyświetlanie", "Display"), icon = Icons.Outlined.Tune) {
            Text(loc("Motyw", "Theme"), color = Fm.c.ink, style = MaterialTheme.typography.titleSmall)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                listOf(
                    com.freemetar.app.data.ThemeMode.SYSTEM to "AMOLED",
                    com.freemetar.app.data.ThemeMode.NIGHT to loc("Grafit", "Graphite"),
                    com.freemetar.app.data.ThemeMode.DAY to loc("Jasny ekran", "Bright screen"),
                ).forEach { (mode, label) ->
                    FilterChip(s.themeMode == mode, { vm.setTheme(mode) }, { Text(label) },
                        leadingIcon = { Icon(when (mode) { com.freemetar.app.data.ThemeMode.DAY -> Icons.Outlined.LightMode; com.freemetar.app.data.ThemeMode.NIGHT -> Icons.Outlined.DarkMode; else -> Icons.Outlined.Smartphone }, null, Modifier.size(16.dp)) })
                }
            }
            ToggleRow(Icons.Outlined.Fullscreen, loc("Ukryj pasek stanu", "Hide the status bar"),
                loc("Aplikacja zajmuje cały ekran; pasek wysuniesz przeciągnięciem od góry.", "The app uses the whole screen; swipe down from the top to show the bar."),
                s.hideStatusBar) { vm.setHideStatusBar(it) }
            ToggleRow(Icons.Outlined.Autorenew, loc("Automatyczne odświeżanie", "Auto refresh"), loc("Co 5 minut, gdy aplikacja jest otwarta", "Every 5 minutes while the app is open"), s.autoRefresh) { vm.setAutoRefresh(it) }
        }

        SectionCard("SimBrief", icon = Icons.Outlined.CloudDownload) {
            var user by androidx.compose.runtime.saveable.rememberSaveable(s.simbriefUser) { androidx.compose.runtime.mutableStateOf(s.simbriefUser) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(user, { user = it.trim() }, Modifier.weight(1f), singleLine = true,
                    label = { Text(loc("Użytkownik lub Pilot ID", "Username or Pilot ID")) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                OutlinedButton({ vm.setSimbriefUser(user) }, enabled = user != s.simbriefUser) { Text(loc("Zapisz", "Save")) }
            }
            Text(
                if (s.simbriefUser.isBlank()) loc("Zapisz konto raz — na ekranie Lot wystarczy potem jeden przycisk, żeby pobrać ostatni plan.", "Save your account once — then one button on the Flight screen downloads your latest plan.")
                else loc("Zapisane konto: ${s.simbriefUser}.", "Saved account: ${s.simbriefUser}."),
                color = Fm.c.ink2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }

        SectionCard(loc("Sterowanie samolotem", "Flying the aircraft"), icon = Icons.Outlined.ScreenRotation) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TILT" to loc("Żyroskop", "Gyro"), "TOUCH" to loc("Drążek palcem", "Thumb stick"), "GAMEPAD" to loc("Kontroler", "Controller")).forEach { (code, label) ->
                    FilterChip(s.controlMode == code, { vm.setControlMode(code) }, { Text(label) })
                }
            }
            ToggleRow(Icons.Outlined.TouchApp, loc("Pływający przycisk sterowania", "Floating control button"),
                loc("Widoczny na każdej zakładce, gdy symulator jest połączony. Przytrzymaj go, żeby sterować.", "Shown on every tab while the simulator is connected. Hold it to fly."),
                s.floatingControl) { vm.setFloatingControl(it) }
            if (s.floatingControl) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 32.dp)) {
                FilterChip(s.floatingStyle == "SIMPLE", { vm.setFloatingStyle("SIMPLE") }, { Text(loc("Prosty", "Simple")) })
                FilterChip(s.floatingStyle == "TRIM", { vm.setFloatingStyle("TRIM") }, { Text(loc("Z trymem", "With trim")) })
            }
            if (s.floatingControl && s.floatingStyle == "TRIM") Text(
                loc("Żyroskop: trzymając przycisk, przeciągnij palec w dół, żeby trymować nos w górę, albo w górę, żeby trymować nos w dół. Drążek palcem: trym ma osobne przyciski obok.",
                    "Gyro: while holding the button, drag your finger down to trim nose up, or up to trim nose down. Thumb stick: trim has its own buttons next to it."),
                color = Fm.c.ink2, fontSize = 12.sp, modifier = Modifier.padding(start = 32.dp, top = 4.dp),
            )
        }

        SectionCard(loc("Zapis lotów", "Flight recording"), icon = Icons.Outlined.History) {
            Text(loc("Częstotliwość zapisu pozycji, wysokości, prędkości i prędkości pionowej", "How often position, altitude, speed and vertical speed are saved"), color = Fm.c.ink)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf(250 to loc("4× na s", "4×/s"), 500 to loc("2× na s", "2×/s"), 1000 to loc("1× na s", "1×/s"), 2000 to loc("co 2 s", "every 2 s"), 5000 to loc("co 5 s", "every 5 s")).forEach { (ms, label) ->
                    FilterChip(s.recordIntervalMs == ms, { vm.setRecordInterval(ms) }, { Text(label) })
                }
            }
            val logs by vm.flightLogs.collectAsStateWithLifecycle()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val kb = remember(logs) { vm.flightArchiveBytes() / 1024 }
            Text(
                loc("Zapisane loty: ${logs.size}, archiwum $kb KB. Tor jest zapisywany jako różnice między punktami i spakowany, więc nawet zapis 4× na sekundę zajmuje mało miejsca. Eksport tworzy jeden plik .oab do kopii zapasowej lub przeniesienia na inny telefon.",
                    "Saved flights: ${logs.size}, archive $kb KB. The track is stored as differences between points and compressed, so even 4×/s takes little space. Export makes one .oab file for backup or moving to another phone."),
                color = Fm.c.ink2, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            val exporter = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
                uri?.let { vm.exportFlightArchive(ctx, it) }
            }
            val importer = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { vm.importFlightArchive(ctx, it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ exporter.launch("OpenTakeoff-loty.oab") }, enabled = logs.isNotEmpty()) {
                    Icon(Icons.Outlined.Upload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(loc("Eksportuj", "Export"))
                }
                OutlinedButton({ importer.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(loc("Importuj", "Import"))
                }
            }
        }

        SectionCard(loc("Baza lotnisk", "Airport database"), icon = Icons.Outlined.Storage) {
            val txt = when (val d = db) {
                is DbState.Ready -> loc("${d.count} lotnisk · aktualizacja ", "${d.count} airports · updated ") + (d.updated?.let { DateFormat.getDateInstance().format(Date(it)) } ?: "—")
                is DbState.Loading -> d.message
                is DbState.Error -> loc("Błąd: ${d.message}", "Error: ${d.message}")
                DbState.Idle -> "—"
            }
            Text(txt, color = Fm.c.ink2, fontSize = 13.sp)
            Spacer(Modifier.size(8.dp))
            OutlinedButton({ vm.loadDb(force = true) }, enabled = db !is DbState.Loading) {
                Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(loc("Pobierz ponownie", "Download again"))
            }
            Text(loc("Baza odświeża się sama co 30 dni.", "The database refreshes itself every 30 days."), color = Fm.c.ink2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        com.freemetar.app.ui.components.UpdateSection(vm)

        SectionCard(loc("O aplikacji", "About"), icon = Icons.Outlined.Info) {
            Text("OpenTakeoff ${BuildConfig.VERSION_NAME}", color = Fm.c.ink, style = MaterialTheme.typography.titleMedium)
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            Row(
                Modifier.padding(vertical = 6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(Fm.c.ink).clickable { uri.openUri(AUTHOR_URL) }.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(loc("Pomysł i wykonanie: ", "Idea and development: "), color = Fm.c.paper, fontSize = 13.sp)
                Text(AUTHOR_SITE, color = Fm.c.paper, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Fm.c.ink2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Fm.c.ink)
            Text(sub, color = Fm.c.ink2, fontSize = 12.sp)
        }
        Switch(value, onChange)
    }
}
