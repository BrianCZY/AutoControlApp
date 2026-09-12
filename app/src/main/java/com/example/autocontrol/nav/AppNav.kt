package com.example.autocontrol.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.autocontrol.ui.components.BottomTabBar
import com.example.autocontrol.ui.components.TabItem
import com.example.autocontrol.ui.home.HomeScreen
import com.example.autocontrol.ui.settings.SettingsScreen
import com.example.autocontrol.ui.theme.BgRoot

object Routes {
    const val Home = "home"
    const val Settings = "settings"
}

private val Tabs = listOf(
    TabItem(Routes.Home, "首页", "🏠"),
    TabItem(Routes.Settings, "设置", "⚙️"),
)

@Composable
fun AppNav(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.Home

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BgRoot,
        bottomBar = {
            BottomTabBar(
                currentRoute = currentRoute,
                tabs = Tabs,
                onTabSelected = { tab ->
                    if (tab.route != currentRoute) {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())
                )
            }
        }
    }
}
