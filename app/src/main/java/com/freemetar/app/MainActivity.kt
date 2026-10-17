package com.freemetar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freemetar.app.ui.screens.AirportScreen
import com.freemetar.app.ui.screens.HomeScreen
import com.freemetar.app.ui.screens.SettingsScreen
import com.freemetar.app.ui.screens.ToolsScreen
import com.freemetar.app.ui.theme.FreeMetarTheme
import com.freemetar.app.ui.theme.Night

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Lotniska", Icons.Outlined.Flight),
    Tab("tools", "Narzędzia", Icons.Outlined.Calculate),
    Tab("settings", "Ustawienia", Icons.Outlined.Settings),
)

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

                Scaffold(
                    containerColor = Night,
                    bottomBar = {
                        if (route in tabs.map { it.route }) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                tabs.forEach { t ->
                                    NavigationBarItem(
                                        selected = route == t.route,
                                        onClick = {
                                            nav.navigate(t.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(t.icon, null) },
                                        label = { Text(t.label) },
                                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceVariant),
                                    )
                                }
                            }
                        }
                    },
                ) { pad ->
                    NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
                        composable("home") {
                            HomeScreen(vm) { ident ->
                                vm.open(ident)
                                nav.navigate("airport/$ident")
                            }
                        }
                        composable("airport/{ident}") { backStack ->
                            // Po powrocie ze stosu (A → B → wstecz) przywróć kartę A
                            val ident = backStack.arguments?.getString("ident").orEmpty()
                            androidx.compose.runtime.LaunchedEffect(ident) {
                                val cur = vm.detail.value
                                if (cur == null || (cur.ident != ident.uppercase() && cur.airport?.iata != ident.uppercase())) vm.open(ident)
                            }
                            AirportScreen(vm, onBack = { nav.popBackStack() }, onOpen = { ident ->
                                vm.open(ident)
                                nav.navigate("airport/$ident")
                            })
                        }
                        composable("tools") { ToolsScreen() }
                        composable("settings") { SettingsScreen(vm) }
                    }
                }
            }
        }
    }
}
