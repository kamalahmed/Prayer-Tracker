package com.kamalahmed.prayerapp.masjid

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.core.location.LocationRepository
import com.kamalahmed.prayerapp.core.location.LocationResult
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.common.SubScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MasjidUi(
    val loading: Boolean = true,
    val city: String = "",
    val result: MasjidResult? = null,
    val note: String? = null,
)

@HiltViewModel
class MasjidViewModel @Inject constructor(
    private val repo: MasjidRepository,
    private val settings: SettingsRepository,
    private val location: LocationRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(MasjidUi())
    val ui: StateFlow<MasjidUi> = _ui

    fun load(force: Boolean = false) = viewModelScope.launch {
        val s = settings.current()
        _ui.value = _ui.value.copy(loading = true, city = s.cityName)
        val r = repo.nearby(s.latitude, s.longitude, force)
        _ui.value = _ui.value.copy(loading = false, result = r)
    }

    /** Refresh the device GPS location first, then search around it. */
    fun useGps() = viewModelScope.launch {
        _ui.value = _ui.value.copy(loading = true)
        val note = when (location.refresh(force = true)) {
            is LocationResult.Updated, LocationResult.WithinGeofence -> null
            LocationResult.NoPermission -> "Location permission is off, so the saved location is used."
            LocationResult.Unavailable -> "Couldn't get a GPS fix, so the saved location is used."
        }
        _ui.value = _ui.value.copy(note = note)
        load(force = false)
    }
}

/** Opens a place in whatever maps app the user has (geo: URI, no Maps SDK). */
fun openInMaps(ctx: Context, m: Mosque) {
    val geo = Uri.parse("geo:${m.lat},${m.lng}?q=${m.lat},${m.lng}(${Uri.encode(m.name)})")
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, geo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        val web = Uri.parse("https://www.openstreetmap.org/?mlat=${m.lat}&mlon=${m.lng}#map=18/${m.lat}/${m.lng}")
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
fun MasjidScreen(onBack: () -> Unit, vm: MasjidViewModel = hiltViewModel()) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }
    SubScreen("Nearby masjids", onBack) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Around ${s.city}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.useGps() }, enabled = !s.loading) { Icon(Icons.Filled.MyLocation, null); Text(" GPS") }
            TextButton(onClick = { vm.load(force = true) }, enabled = !s.loading) { Icon(Icons.Filled.Refresh, "Refresh") }
        }
        s.note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 16.dp)) }
        when {
            s.loading -> Center { CircularProgressIndicator(); Text("Searching OpenStreetMap…", modifier = Modifier.padding(top = 12.dp)) }
            s.result is MasjidResult.Error -> {
                val e = s.result as MasjidResult.Error
                Center {
                    Text(e.message, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { vm.load(force = true) }, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
                }
            }
            s.result is MasjidResult.Found -> {
                val f = s.result as MasjidResult.Found
                if (f.mosques.isEmpty()) Center {
                    Text("No masjids are mapped on OpenStreetMap within ${Overpass.formatDistance(f.radiusM)}.", textAlign = TextAlign.Center)
                } else LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "${f.mosques.size} found within ${Overpass.formatDistance(f.radiusM)}" + if (f.fromCache) " · cached" else "",
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp, 8.dp),
                        )
                    }
                    item {
                        CardGroup {
                            f.mosques.forEachIndexed { i, m ->
                                if (i > 0) RowDivider()
                                ListRow(
                                    m.name,
                                    subtitle = listOfNotNull(m.nameAr, m.address).joinToString(" · ").ifBlank { null },
                                    leading = Icons.Filled.Place,
                                    onClick = { openInMaps(ctx, m) },
                                    showChevron = false,
                                ) { Text(Overpass.formatDistance(m.distanceM), color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                    item {
                        Text("Data © OpenStreetMap contributors. Tap a masjid to open it in your maps app.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
    }
}
