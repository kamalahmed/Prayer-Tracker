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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kamalahmed.prayerapp.ui.more.MoreScreen
import com.kamalahmed.prayerapp.ui.streaks.StreaksScreen
import com.kamalahmed.prayerapp.ui.streaks.TodayTracker
import androidx.hilt.navigation.compose.hiltViewModel
import com.kamalahmed.prayerapp.ui.settings.NotificationSettingsScreen
import com.kamalahmed.prayerapp.ui.settings.SettingsDest
import com.kamalahmed.prayerapp.ui.settings.SettingsScreen
import com.kamalahmed.prayerapp.ui.settings.SoundSettingsScreen
import com.kamalahmed.prayerapp.ui.settings.SystemScreen
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

val availableSettings = SettingsDest.entries.toSet()

object Routes {
    const val CALENDAR = "calendar"
    const val HOLIDAYS = "holidays"
    const val STREAKS = "streaks"
}

@Composable
fun PrayerAppRoot() {
    val rootVm: com.kamalahmed.prayerapp.ui.settings.SettingsViewModel = hiltViewModel()
    val rootSettings by rootVm.settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { rootVm.autoRefreshIfEnabled() }
    val st = rootSettings ?: return
    if (!st.onboardingDone) {
        com.kamalahmed.prayerapp.ui.onboarding.OnboardingScreen(rootVm)
        return
    }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val back: () -> Unit = { nav.popBackStack() }
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
            val openUpdates by com.kamalahmed.prayerapp.update.UpdateNav.openRequested.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(openUpdates) {
                if (openUpdates) { com.kamalahmed.prayerapp.update.UpdateNav.openRequested.value = false; nav.navigate(SettingsDest.APP.route) }
            }
            NavHost(nav, startDestination = TopDest.PRAYERS.route) {
                composable(TopDest.PRAYERS.route) { PrayersScreen(trackerContent = { TodayTracker() }) }
                composable(Routes.STREAKS) { StreaksScreen(onBack = back) }
                composable(TopDest.TIMES.route) {
                    TimesScreen(onCalendar = { nav.navigate(Routes.CALENDAR) }, onHolidays = { nav.navigate(Routes.HOLIDAYS) })
                }
                composable(TopDest.QIBLA.route) { QiblaScreen() }
                composable(TopDest.SETTINGS.route) {
                    val uvm: com.kamalahmed.prayerapp.update.UpdateViewModel = hiltViewModel()
                    SettingsScreen(hiltViewModel(), open = { nav.navigate(it.route) }, available = availableSettings,
                        banner = { com.kamalahmed.prayerapp.update.UpdateBanner(uvm) { nav.navigate(SettingsDest.APP.route) } })
                }
                composable(SettingsDest.NOTIFICATIONS.route) {
                    NotificationSettingsScreen(hiltViewModel(), onBack = back, onSound = { nav.navigate(SettingsDest.SOUND.route) })
                }
                composable(SettingsDest.SOUND.route) { SoundSettingsScreen(hiltViewModel(), onBack = back) }
                composable(SettingsDest.SYSTEM.route) { SystemScreen(hiltViewModel(), onBack = back) }
                composable(SettingsDest.CALCULATION.route) { com.kamalahmed.prayerapp.ui.settings.CalculationScreen(hiltViewModel(), onBack = back) }
                composable(SettingsDest.LOCATION.route) { com.kamalahmed.prayerapp.ui.settings.LocationScreen(hiltViewModel(), onBack = back) }
                composable(SettingsDest.TIME.route) { com.kamalahmed.prayerapp.ui.settings.TimeSettingsScreen(hiltViewModel(), onBack = back) }
                composable(SettingsDest.IQAMA.route) { com.kamalahmed.prayerapp.ui.settings.IqamaScreen(hiltViewModel(), onBack = back) }
                composable(SettingsDest.APP.route) {
                    val vm: com.kamalahmed.prayerapp.ui.settings.SettingsViewModel = hiltViewModel()
                    com.kamalahmed.prayerapp.ui.settings.AppScreen(vm, onBack = back, onOnboarding = { vm.update { it.copy(onboardingDone = false) } }, updateVm = hiltViewModel())
                }
                composable(SettingsDest.WIDGETS.route) { com.kamalahmed.prayerapp.ui.widgets.WidgetStudioScreen(onBack = back) }
                composable(TopDest.MORE.route) {
                    MoreScreen(
                        onStreaks = { nav.navigate(Routes.STREAKS) },
                        onCalendar = { nav.navigate(Routes.CALENDAR) },
                        onHolidays = { nav.navigate(Routes.HOLIDAYS) },
                        onWidgets = { nav.navigate(SettingsDest.WIDGETS.route) },
                        onAbout = { nav.navigate(SettingsDest.APP.route) },
                        banner = {
                            val uvm: com.kamalahmed.prayerapp.update.UpdateViewModel = hiltViewModel()
                            com.kamalahmed.prayerapp.update.UpdateBanner(uvm) { nav.navigate(SettingsDest.APP.route) }
                        },
                    )
                }
                composable(Routes.CALENDAR) { MonthlyCalendarScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.HOLIDAYS) { HolidaysScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}
