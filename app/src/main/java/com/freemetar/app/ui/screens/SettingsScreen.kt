package com.freemetar.app.ui.screens

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
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Autorenew
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
import com.freemetar.app.ui.theme.TextHi
import com.freemetar.app.ui.theme.TextLo
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
            Text("Ciśnienie", color = TextHi)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.pressureUnit == PressureUnit.HPA, { vm.setPressure(PressureUnit.HPA) }, { Text("hPa") }, leadingIcon = { Icon(Icons.Outlined.Compress, null, Modifier.size(16.dp)) })
                FilterChip(s.pressureUnit == PressureUnit.INHG, { vm.setPressure(PressureUnit.INHG) }, { Text("inHg") }, leadingIcon = { Icon(Icons.Outlined.Compress, null, Modifier.size(16.dp)) })
            }
            Spacer(Modifier.size(8.dp))
            Text("Wysokość", color = TextHi)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.altUnit == AltUnit.FT, { vm.setAlt(AltUnit.FT) }, { Text("stopy") }, leadingIcon = { Icon(Icons.Outlined.Height, null, Modifier.size(16.dp)) })
                FilterChip(s.altUnit == AltUnit.M, { vm.setAlt(AltUnit.M) }, { Text("metry") }, leadingIcon = { Icon(Icons.Outlined.Height, null, Modifier.size(16.dp)) })
            }
        }

        SectionCard("Wyświetlanie", icon = Icons.Outlined.Tune) {
            ToggleRow(Icons.AutoMirrored.Outlined.Article, "Surowy METAR na górze", "Pokaż tekst depeszy przed kafelkami", s.showRawFirst) { vm.setRawFirst(it) }
            ToggleRow(Icons.Outlined.Autorenew, "Automatyczne odświeżanie", "Co 5 minut, gdy aplikacja jest otwarta", s.autoRefresh) { vm.setAutoRefresh(it) }
        }

        SectionCard("Baza lotnisk", icon = Icons.Outlined.Storage) {
            val txt = when (val d = db) {
                is DbState.Ready -> "${d.count} lotnisk · aktualizacja " + (d.updated?.let { DateFormat.getDateInstance().format(Date(it)) } ?: "—")
                is DbState.Loading -> d.message
                is DbState.Error -> "Błąd: ${d.message}"
                DbState.Idle -> "—"
            }
            Text(txt, color = TextLo, fontSize = 13.sp)
            Spacer(Modifier.size(8.dp))
            OutlinedButton({ vm.loadDb(force = true) }, enabled = db !is DbState.Loading) {
                Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pobierz ponownie")
            }
            Text("Baza odświeża się sama co 30 dni.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        com.freemetar.app.ui.components.UpdateSection(vm)

        SectionCard("O aplikacji", icon = Icons.Outlined.Info) {
            Text("FreeMETAR ${BuildConfig.VERSION_NAME}", color = TextHi)
            Spacer(Modifier.size(4.dp))
            Text(
                "Pogoda: aviationweather.gov (NOAA/NWS, domena publiczna).\n" +
                    "Lotniska, pasy, częstotliwości, pomoce nawigacyjne: OurAirports (domena publiczna).\n\n" +
                    "Aplikacja przeznaczona wyłącznie do symulacji lotów. Dane mogą być nieaktualne lub niekompletne — nie używaj do rzeczywistej nawigacji lotniczej.",
                color = TextLo, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TextLo, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi)
            Text(sub, color = TextLo, fontSize = 12.sp)
        }
        Switch(value, onChange)
    }
}
