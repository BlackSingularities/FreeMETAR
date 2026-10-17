package com.freemetar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.freemetar.app.ui.components.Section
import com.freemetar.app.ui.theme.Fm

/**
 * Sekcja Lot: połączenie z symulatorem, plan lotu i zapisane trasy.
 * Widoki na żywo (PFD, mapa ruchoma, panele) dochodzą wraz z OpenAirBook Link.
 */
@Composable
fun FlightScreen() {
    val c = Fm.c
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text("Lot", style = MaterialTheme.typography.headlineLarge, color = c.ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))

        Section("Symulator", Icons.Outlined.Computer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.ink2))
                Spacer(Modifier.width(10.dp))
                Text("Nie połączono z MSFS", style = MaterialTheme.typography.titleMedium, color = c.ink)
            }
            Text(
                "Uruchom OpenAirBook Link na komputerze z Microsoft Flight Simulator 2024. Telefon i komputer muszą być w tej samej sieci Wi-Fi. " +
                    "Po połączeniu zobaczysz tu PFD, swój samolot na mapie i panele autopilota, radia oraz systemów.",
                style = MaterialTheme.typography.bodyMedium, color = c.ink2, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Section("Plan lotu", Icons.Outlined.Route) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CloudDownload, null, tint = c.accent)
                Text("Import z SimBrief po nazwie użytkownika: start, cel, zapasowe, trasa, poziom i paliwo, z pogodą wzdłuż trasy.",
                    style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
            }
        }
        Section("Zapisane trasy", Icons.Outlined.Folder) {
            Text("Trasy zapisujesz w telefonie; zostają po aktualizacji aplikacji. Możesz je wyeksportować do pliku i zaimportować z powrotem.",
                style = MaterialTheme.typography.bodyMedium, color = c.ink2, modifier = Modifier.fillMaxWidth())
        }
    }
}
