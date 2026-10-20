// SPDX-FileCopyrightText: 2026 57Million LLC
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onlytrusted.app.ui.about.AboutScreen
import com.onlytrusted.app.ui.blocklist.BlockListScreen
import com.onlytrusted.app.ui.dashboard.DashboardScreen
import com.onlytrusted.app.ui.events.EventsScreen
import com.onlytrusted.app.ui.legal.LegalScreen
import com.onlytrusted.app.ui.permissions.PermissionAuditScreen
import com.onlytrusted.app.ui.posture.PostureScreen
import com.onlytrusted.app.ui.settings.SettingsScreen
import com.onlytrusted.app.ui.shield.ShieldLogScreen
import com.onlytrusted.app.ui.vault.VaultEditScreen
import com.onlytrusted.app.ui.vault.VaultScreen

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Destination("dashboard", "Shield", Icons.Filled.Shield)
    data object Vault : Destination("vault", "Family", Icons.Filled.Groups)
    data object Events : Destination("events", "Events", Icons.Filled.History)
    data object BlockList : Destination("blocklist", "Blocked", Icons.Filled.Block)
    data object Settings : Destination("settings", "Settings", Icons.Filled.Settings)
    data object Permissions : Destination("permissions", "Permissions", Icons.Filled.Security)
    data object Posture : Destination("posture", "Posture", Icons.Filled.Security)
    data object ShieldLog : Destination("shieldlog", "Shield log", Icons.Filled.Shield)
    data object Legal : Destination("legal", "Legal", Icons.Filled.Security)
    data object About : Destination("about", "About", Icons.Filled.Security)
    data object VaultEdit : Destination("vault/edit?id={id}", "Vault edit", Icons.Filled.Security) {
        fun route(id: Long?) = "vault/edit?id=${id ?: -1L}"
    }
}

private val bottomBarDestinations = listOf(
    Destination.Dashboard,
    Destination.Vault,
    Destination.Events,
    Destination.BlockList,
    Destination.Settings
)

@Composable
fun AppNavigation(onPanic: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomBarDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomBarDestinations.forEach { dest ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == dest.route } == true ||
                            currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != dest.route) {
                                    navController.navigate(dest.route) {
                                        launchSingleTop = true
                                        popUpTo(Destination.Dashboard.route) {
                                            saveState = true
                                            inclusive = false
                                        }
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Dashboard.route) {
                DashboardScreen(
                    onPanic = onPanic,
                    onGoToVault = { navController.navigate(Destination.Vault.route) },
                    onGoToSettings = { navController.navigate(Destination.Settings.route) }
                )
            }
            composable(Destination.Vault.route) {
                VaultScreen(
                    onAdd = { navController.navigate(Destination.VaultEdit.route(null)) },
                    onEdit = { id -> navController.navigate(Destination.VaultEdit.route(id)) }
                )
            }
            composable(
                route = Destination.VaultEdit.route,
                arguments = listOf(navArgument("id") {
                    type = NavType.LongType
                    defaultValue = -1L
                })
            ) { backEntry ->
                val id = backEntry.arguments?.getLong("id")?.takeIf { it > 0 }
                VaultEditScreen(contactId = id, onDone = { navController.popBackStack() })
            }
            composable(Destination.Events.route) { EventsScreen() }
            composable(Destination.BlockList.route) { BlockListScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onOpenPermissions = { navController.navigate(Destination.Permissions.route) },
                    onOpenPosture = { navController.navigate(Destination.Posture.route) },
                    onOpenShieldLog = { navController.navigate(Destination.ShieldLog.route) },
                    onOpenLegal = { navController.navigate(Destination.Legal.route) },
                    onOpenAbout = { navController.navigate(Destination.About.route) }
                )
            }
            composable(Destination.Permissions.route) { PermissionAuditScreen() }
            composable(Destination.Posture.route) { PostureScreen() }
            composable(Destination.ShieldLog.route) { ShieldLogScreen() }
            composable(Destination.Legal.route) { LegalScreen() }
            composable(Destination.About.route) { AboutScreen() }
        }
    }
}
