package com.kamalahmed.prayerapp.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.kamalahmed.prayerapp.ui.adaptive.AdaptiveSpec
import com.kamalahmed.prayerapp.ui.adaptive.LocalAdaptive
import com.kamalahmed.prayerapp.ui.adaptive.MaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
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
    STREAKS("streaks", "Streaks", Icons.Filled.LocalFireDepartment),
    QIBLA("qibla", "Qibla", Icons.Filled.Navigation),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

val availableSettings = SettingsDest.entries.toSet()

object Routes {
    const val CALENDAR = "calendar"
    const val HOLIDAYS = "holidays"
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
    val goTop: (TopDest) -> Unit = { d ->
        nav.navigate(d.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // Window size drives the chrome; recomputed on fold/unfold, rotation and resizing.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val spec = AdaptiveSpec(maxWidth.value, maxHeight.value)
        CompositionLocalProvider(LocalAdaptive provides spec) {
            val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
            val appError by com.kamalahmed.prayerapp.core.safety.AppErrors.latest.collectAsStateWithLifecycle()
            LaunchedEffect(appError) {
                appError?.let { snackbar.showSnackbar(it, withDismissAction = true); com.kamalahmed.prayerapp.core.safety.AppErrors.latest.value = null }
            }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
                bottomBar = {
                    if (!spec.useRail) NavigationBar(containerColor = Card) {
                        TopDest.entries.forEach { d ->
                            NavigationBarItem(
                                selected = current == d.route, onClick = { goTop(d) },
                                icon = { Icon(d.icon, contentDescription = null) }, label = { Text(d.label) },
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
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (spec.useRail) {
                        NavigationRail(containerColor = Card, modifier = Modifier.fillMaxHeight()) {
                            Spacer(Modifier.weight(1f))
                            TopDest.entries.forEach { d ->
                                NavigationRailItem(
                                    selected = current == d.route, onClick = { goTop(d) },
                                    icon = { Icon(d.icon, contentDescription = null) }, label = { Text(d.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { AppNavHost(nav, back, spec) }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: androidx.navigation.NavHostController, back: () -> Unit, spec: AdaptiveSpec) {
    val openUpdates by com.kamalahmed.prayerapp.update.UpdateNav.openRequested.collectAsStateWithLifecycle()
    LaunchedEffect(openUpdates) {
        if (openUpdates) { com.kamalahmed.prayerapp.update.UpdateNav.openRequested.value = false; nav.navigate(SettingsDest.APP.route) }
    }
    NavHost(nav, startDestination = TopDest.PRAYERS.route) {
        composable(TopDest.PRAYERS.route) {
            Column(Modifier.fillMaxSize()) {
                com.kamalahmed.prayerapp.update.TodayUpdateBanner()
                Box(Modifier.weight(1f)) { PrayersScreen(trackerContent = { TodayTracker() }) }
            }
        }
        composable(TopDest.STREAKS.route) { StreaksScreen() }
        composable(TopDest.TIMES.route) {
            TimesScreen(onCalendar = { nav.navigate(Routes.CALENDAR) }, onHolidays = { nav.navigate(Routes.HOLIDAYS) })
        }
        composable(TopDest.QIBLA.route) { QiblaScreen(onOpenLocation = { nav.navigate(SettingsDest.LOCATION.route) }) }
        composable(TopDest.SETTINGS.route) {
            if (spec.listDetail) SettingsListDetail()
            else MaxWidth { SettingsList(open = { nav.navigate(it.route) }, openApp = { nav.navigate(SettingsDest.APP.route) }) }
        }
        SettingsDest.entries.forEach { d ->
            composable(d.route) { SettingsDetail(d, onBack = back, open = { nav.navigate(it.route) }) }
        }
        composable(Routes.CALENDAR) { MonthlyCalendarScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.HOLIDAYS) { HolidaysScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun SettingsList(open: (SettingsDest) -> Unit, openApp: () -> Unit, selected: SettingsDest? = null) {
    val uvm: com.kamalahmed.prayerapp.update.UpdateViewModel = hiltViewModel()
    SettingsScreen(hiltViewModel(), open = open, available = availableSettings, selected = selected,
        banner = { com.kamalahmed.prayerapp.update.UpdateBanner(uvm) { openApp() } })
}

/** Wide screens: settings list on the left, the chosen page on the right (selection survives fold/rotation). */
@Composable
private fun SettingsListDetail() {
    var selected by rememberSaveable { mutableStateOf(SettingsDest.NOTIFICATIONS) }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(AdaptiveSpec.SETTINGS_LIST_WIDTH.dp).fillMaxHeight()) {
            SettingsList(open = { selected = it }, openApp = { selected = SettingsDest.APP }, selected = selected)
        }
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            androidx.compose.runtime.key(selected) {
                // In the detail pane only "Adhan sound" (opened from Notifications) has a back arrow.
                val nested = selected == SettingsDest.SOUND
                CompositionLocalProvider(com.kamalahmed.prayerapp.ui.common.LocalShowBack provides nested) {
                    SettingsDetail(selected, onBack = { selected = SettingsDest.NOTIFICATIONS }, open = { selected = it })
                }
            }
        }
    }
}

@Composable
private fun SettingsDetail(d: SettingsDest, onBack: () -> Unit, open: (SettingsDest) -> Unit) {
    MaxWidth {
        when (d) {
            SettingsDest.NOTIFICATIONS -> NotificationSettingsScreen(hiltViewModel(), onBack = onBack, onSound = { open(SettingsDest.SOUND) })
            SettingsDest.SOUND -> SoundSettingsScreen(hiltViewModel(), onBack = onBack)
            SettingsDest.SYSTEM -> SystemScreen(hiltViewModel(), onBack = onBack)
            SettingsDest.CALCULATION -> com.kamalahmed.prayerapp.ui.settings.CalculationScreen(hiltViewModel(), onBack = onBack)
            SettingsDest.LOCATION -> com.kamalahmed.prayerapp.ui.settings.LocationScreen(hiltViewModel(), onBack = onBack)
            SettingsDest.TIME -> com.kamalahmed.prayerapp.ui.settings.TimeSettingsScreen(hiltViewModel(), onBack = onBack)
            SettingsDest.IQAMA -> com.kamalahmed.prayerapp.ui.settings.IqamaScreen(hiltViewModel(), onBack = onBack)
            SettingsDest.APP -> {
                val vm: com.kamalahmed.prayerapp.ui.settings.SettingsViewModel = hiltViewModel()
                com.kamalahmed.prayerapp.ui.settings.AppScreen(vm, onBack = onBack, onOnboarding = { vm.update { it.copy(onboardingDone = false) } }, updateVm = hiltViewModel())
            }
            SettingsDest.WIDGETS -> com.kamalahmed.prayerapp.ui.widgets.WidgetStudioScreen(onBack = onBack)
        }
    }
}
