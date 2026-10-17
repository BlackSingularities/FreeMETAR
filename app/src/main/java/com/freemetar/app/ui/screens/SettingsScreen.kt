package com.freemetar.app.ui.screens

import com.freemetar.app.ui.theme.Fm
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

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val db by vm.dbState.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ustawienia", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))

        SectionCard("Jednostki", icon = Icons.Outlined.Straighten) {
            Text("Ciśnienie", color = Fm.c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.pressureUnit == PressureUnit.HPA, { vm.setPressure(PressureUnit.HPA) }, { Text("hPa") }, leadingIcon = { Icon(Icons.Outlined.Compress, null, Modifier.size(16.dp)) })
                FilterChip(s.pressureUnit == PressureUnit.INHG, { vm.setPressure(PressureUnit.INHG) }, { Text("inHg") }, leadingIcon = { Icon(Icons.Outlined.Compress, null, Modifier.size(16.dp)) })
            }
            Spacer(Modifier.size(8.dp))
            Text("Wysokość", color = Fm.c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.altUnit == AltUnit.FT, { vm.setAlt(AltUnit.FT) }, { Text("stopy") }, leadingIcon = { Icon(Icons.Outlined.Height, null, Modifier.size(16.dp)) })
                FilterChip(s.altUnit == AltUnit.M, { vm.setAlt(AltUnit.M) }, { Text("metry") }, leadingIcon = { Icon(Icons.Outlined.Height, null, Modifier.size(16.dp)) })
            }
        }

        SectionCard("Wyświetlanie", icon = Icons.Outlined.Tune) {
            Text("Motyw", color = Fm.c.ink, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                listOf(
                    com.freemetar.app.data.ThemeMode.SYSTEM to "Kokpit",
                    com.freemetar.app.data.ThemeMode.DAY to "Jasny ekran",
                ).forEach { (mode, label) ->
                    FilterChip(s.themeMode == mode || (mode == com.freemetar.app.data.ThemeMode.SYSTEM && s.themeMode == com.freemetar.app.data.ThemeMode.NIGHT), { vm.setTheme(mode) }, { Text(label) },
                        leadingIcon = { Icon(when (mode) { com.freemetar.app.data.ThemeMode.DAY -> Icons.Outlined.LightMode; com.freemetar.app.data.ThemeMode.NIGHT -> Icons.Outlined.DarkMode; else -> Icons.Outlined.Smartphone }, null, Modifier.size(16.dp)) })
                }
            }
            ToggleRow(Icons.Outlined.Autorenew, "Automatyczne odświeżanie", "Co 5 minut, gdy aplikacja jest otwarta", s.autoRefresh) { vm.setAutoRefresh(it) }
        }

        SectionCard("Baza lotnisk", icon = Icons.Outlined.Storage) {
            val txt = when (val d = db) {
                is DbState.Ready -> "${d.count} lotnisk · aktualizacja " + (d.updated?.let { DateFormat.getDateInstance().format(Date(it)) } ?: "—")
                is DbState.Loading -> d.message
                is DbState.Error -> "Błąd: ${d.message}"
                DbState.Idle -> "—"
            }
            Text(txt, color = Fm.c.ink2, fontSize = 13.sp)
            Spacer(Modifier.size(8.dp))
            OutlinedButton({ vm.loadDb(force = true) }, enabled = db !is DbState.Loading) {
                Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pobierz ponownie")
            }
            Text("Baza odświeża się sama co 30 dni.", color = Fm.c.ink2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        com.freemetar.app.ui.components.UpdateSection(vm)

        SectionCard("O aplikacji", icon = Icons.Outlined.Info) {
            Text("OpenAirBook ${BuildConfig.VERSION_NAME}", color = Fm.c.ink)
            Spacer(Modifier.size(4.dp))
            Text(
                "Pogoda: aviationweather.gov (NOAA/NWS, domena publiczna).\n" +
                    "Lotniska, pasy, częstotliwości, pomoce nawigacyjne: OurAirports (domena publiczna).\n\n" +
                    "Aplikacja przeznaczona wyłącznie do symulacji lotów. Dane mogą być nieaktualne lub niekompletne — nie używaj do rzeczywistej nawigacji lotniczej.",
                color = Fm.c.ink2, fontSize = 13.sp,
            )
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
