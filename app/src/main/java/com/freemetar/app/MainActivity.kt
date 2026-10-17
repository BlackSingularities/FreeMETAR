package com.freemetar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freemetar.app.ui.components.UpdateDialog
import com.freemetar.app.ui.components.isWide
import com.freemetar.app.ui.screens.AirportScreen
import com.freemetar.app.ui.screens.HomeScreen
import com.freemetar.app.ui.screens.SettingsScreen
import com.freemetar.app.ui.screens.ToolsScreen
import com.freemetar.app.ui.theme.FreeMetarTheme
import com.freemetar.app.ui.theme.Night
import com.freemetar.app.ui.theme.Sky

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", "Lotniska", Icons.Outlined.FlightTakeoff, Icons.Filled.FlightTakeoff),
    Tab("tools", "Narzędzia", Icons.Outlined.Calculate, Icons.Filled.Calculate),
    Tab("settings", "Ustawienia", Icons.Outlined.Settings, Icons.Filled.Settings),
)

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreeMetarTheme {
                val vm: MainViewModel = viewModel()
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                // Karta lotniska podświetla zakładkę „Lotniska”
                val activeTab = if (route?.startsWith("airport") == true) "home" else route
                val wide = isWide()
                UpdateDialog(vm)

                Scaffold(
                    containerColor = Night,
                    // Pasek stanu obsługuje każdy ekran sam (statusBarsPadding / TopAppBar);
                    // tu tylko dolny pasek systemowy, inaczej górny odstęp liczy się podwójnie
                    contentWindowInsets = WindowInsets.navigationBars,
                    bottomBar = {
                        if (!wide && route in tabs.map { it.route }) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                tabs.forEach { t ->
                                    val sel = activeTab == t.route
                                    NavigationBarItem(
                                        selected = sel,
                                        onClick = { nav.goTab(t.route) },
                                        icon = { Icon(if (sel) t.selectedIcon else t.icon, null) },
                                        label = { Text(t.label) },
                                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceVariant, selectedIconColor = Sky),
                                    )
                                }
                            }
                        }
                    },
                ) { pad ->
                    Row(Modifier.fillMaxSize().padding(pad)) {
                        if (wide) {
                            NavigationRail(containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxHeight()) {
                                Spacer(Modifier.statusBarsPadding().size(12.dp))
                                Icon(Icons.Filled.FlightTakeoff, null, tint = Sky, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.size(20.dp))
                                tabs.forEach { t ->
                                    val sel = activeTab == t.route
                                    NavigationRailItem(
                                        selected = sel,
                                        onClick = { nav.goTab(t.route) },
                                        icon = { Icon(if (sel) t.selectedIcon else t.icon, null) },
                                        label = { Text(t.label) },
                                        colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceVariant, selectedIconColor = Sky),
                                    )
                                }
                            }
                        }
                        // Na bardzo szerokich ekranach nie rozciągamy treści w nieskończoność
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                            NavHost(
                                nav, startDestination = "home",
                                modifier = Modifier.widthIn(max = 1280.dp),
                                enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
                                exitTransition = { fadeOut() },
                                popEnterTransition = { fadeIn() },
                                popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
                            ) {
                                composable("home") {
                                    HomeScreen(vm) { ident ->
                                        vm.open(ident)
                                        nav.navigate("airport/$ident")
                                    }
                                }
                                composable("airport/{ident}") { backStack ->
                                    // Po powrocie ze stosu (A → B → wstecz) przywróć kartę A
                                    val ident = backStack.arguments?.getString("ident").orEmpty()
                                    LaunchedEffect(ident) {
                                        val cur = vm.detail.value
                                        if (cur == null || (cur.ident != ident.uppercase() && cur.airport?.iata != ident.uppercase())) vm.open(ident)
                                    }
                                    AirportScreen(vm, onBack = { nav.popBackStack() }, onOpen = { id ->
                                        vm.open(id)
                                        nav.navigate("airport/$id")
                                    })
                                }
                                composable("tools") { ToolsScreen(vm) }
                                composable("settings") { SettingsScreen(vm) }
                            }
                        }
                    }
                }
            }
        }
    }
}
