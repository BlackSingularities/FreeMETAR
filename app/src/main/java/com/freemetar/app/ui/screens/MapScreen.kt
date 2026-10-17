package com.freemetar.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.freemetar.app.ui.components.EmptyState

/** Mapa: lotniska w kolorach kategorii lotu, SIGMET, PIREP, trasa i samolot z symulatora. */
@Composable
fun MapScreen() {
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
        EmptyState(
            Icons.Outlined.Map, "Mapa pogody",
            "Tu zobaczysz lotniska w kolorach kategorii lotu, obszary SIGMET, raporty pilotów, swoją trasę i samolot z symulatora.",
        )
    }
}
