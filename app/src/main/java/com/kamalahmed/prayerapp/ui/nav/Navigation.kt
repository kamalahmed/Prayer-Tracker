package com.kamalahmed.prayerapp.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kamalahmed.prayerapp.ui.screens.PlaceholderScreen
import com.kamalahmed.prayerapp.ui.prayers.PrayersScreen
import com.kamalahmed.prayerapp.ui.qibla.QiblaScreen
import com.kamalahmed.prayerapp.ui.times.HolidaysScreen
import com.kamalahmed.prayerapp.ui.times.MonthlyCalendarScreen
import com.kamalahmed.prayerapp.ui.times.TimesScreen
import com.kamalahmed.prayerapp.ui.theme.Card

enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    PRAYERS("prayers", "Prayers", Icons.Filled.History),
    TIMES("times", "Times", Icons.AutoMirrored.Filled.List),
    QIBLA("qibla", "Qibla", Icons.Filled.Navigation),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
    MORE("more", "More", Icons.Filled.MoreHoriz),
}

object Routes {
    const val CALENDAR = "calendar"
    const val HOLIDAYS = "holidays"
}

@Composable
fun PrayerAppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Card) {
                TopDest.entries.forEach { d ->
                    NavigationBarItem(
                        selected = current == d.route,
                        onClick = {
                            nav.navigate(d.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(d.icon, contentDescription = null) },
                        label = { Text(d.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(nav, startDestination = TopDest.PRAYERS.route) {
                composable(TopDest.PRAYERS.route) { PrayersScreen() }
                composable(TopDest.TIMES.route) {
                    TimesScreen(onCalendar = { nav.navigate(Routes.CALENDAR) }, onHolidays = { nav.navigate(Routes.HOLIDAYS) })
                }
                composable(TopDest.QIBLA.route) { QiblaScreen() }
                composable(TopDest.SETTINGS.route) { PlaceholderScreen("Settings") }
                composable(TopDest.MORE.route) { PlaceholderScreen("More") }
                composable(Routes.CALENDAR) { MonthlyCalendarScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.HOLIDAYS) { HolidaysScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}
