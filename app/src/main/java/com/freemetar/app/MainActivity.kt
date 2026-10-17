package com.freemetar.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalAirport
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LocalAirport
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freemetar.app.ui.components.Rule
import com.freemetar.app.ui.components.UpdateDialog
import com.freemetar.app.ui.components.isWide
import com.freemetar.app.ui.screens.BriefingScreen
import com.freemetar.app.ui.screens.HomeScreen
import com.freemetar.app.ui.screens.SettingsScreen
import com.freemetar.app.ui.screens.ToolsScreen
import com.freemetar.app.ui.theme.Fm
import com.freemetar.app.ui.theme.FreeMetarTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", "Lotniska", Icons.Outlined.LocalAirport, Icons.Filled.LocalAirport),
    Tab("tools", "Planowanie", Icons.Outlined.Explore, Icons.Filled.Explore),
    Tab("settings", "Ustawienia", Icons.Outlined.Tune, Icons.Filled.Tune),
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
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            FreeMetarTheme(settings.themeMode) {
                val night = Fm.c.isNight
                // Ikony paska stanu muszą pasować do motywu wybranego w aplikacji, nie do systemu
                LaunchedEffect(night) {
                    val style = if (night) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }

                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                val activeTab = if (route?.startsWith("airport") == true) "home" else route
                val wide = isWide()
                val c = Fm.c
                UpdateDialog(vm)

                Scaffold(
                    containerColor = c.paper,
                    // Pasek stanu obsługuje każdy ekran sam; tu tylko dolny pasek systemowy
                    contentWindowInsets = WindowInsets.navigationBars,
                    bottomBar = {
                        if (!wide && route in tabs.map { it.route }) {
                            androidx.compose.foundation.layout.Column {
                            Rule()
                            NavigationBar(containerColor = c.paper, tonalElevation = 0.dp) {
                                tabs.forEach { t ->
                                    val sel = activeTab == t.route
                                    NavigationBarItem(
                                        selected = sel,
                                        onClick = { nav.goTab(t.route) },
                                        icon = { Icon(if (sel) t.selectedIcon else t.icon, null) },
                                        label = { Text(t.label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = c.terrain, selectedIconColor = c.magenta, selectedTextColor = c.ink,
                                            unselectedIconColor = c.ink2, unselectedTextColor = c.ink2,
                                        ),
                                    )
                                }
                            }
                            }
                        }
                    },
                ) { pad ->
                    Row(Modifier.fillMaxSize().padding(pad)) {
                        if (wide) {
                            NavigationRail(containerColor = c.paper, modifier = Modifier.fillMaxHeight()) {
                                Spacer(Modifier.statusBarsPadding().size(16.dp))
                                Icon(Icons.Filled.LocalAirport, null, tint = c.magenta, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.size(24.dp))
                                tabs.forEach { t ->
                                    val sel = activeTab == t.route
                                    NavigationRailItem(
                                        selected = sel,
                                        onClick = { nav.goTab(t.route) },
                                        icon = { Icon(if (sel) t.selectedIcon else t.icon, null) },
                                        label = { Text(t.label) },
                                        colors = NavigationRailItemDefaults.colors(
                                            indicatorColor = c.terrain, selectedIconColor = c.magenta, selectedTextColor = c.ink,
                                            unselectedIconColor = c.ink2, unselectedTextColor = c.ink2,
                                        ),
                                    )
                                }
                            }
                            Box(Modifier.width(1.dp).fillMaxHeight().background(c.rule))
                        }
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                            NavHost(
                                nav, startDestination = "home",
                                modifier = Modifier.widthIn(max = 1280.dp),
                                enterTransition = { fadeIn() + slideInHorizontally { it / 14 } },
                                exitTransition = { fadeOut() },
                                popEnterTransition = { fadeIn() },
                                popExitTransition = { fadeOut() + slideOutHorizontally { it / 14 } },
                            ) {
                                composable("home") {
                                    HomeScreen(vm) { ident ->
                                        vm.open(ident)
                                        nav.navigate("airport/$ident")
                                    }
                                }
                                composable("airport/{ident}") { backStack ->
                                    // Po powrocie ze stosu (A → B → wstecz) przywróć briefing A
                                    val ident = backStack.arguments?.getString("ident").orEmpty()
                                    LaunchedEffect(ident) {
                                        val cur = vm.detail.value
                                        if (cur == null || (cur.ident != ident.uppercase() && cur.airport?.iata != ident.uppercase())) vm.open(ident)
                                    }
                                    BriefingScreen(vm, onBack = { nav.popBackStack() }, onOpen = { id ->
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
