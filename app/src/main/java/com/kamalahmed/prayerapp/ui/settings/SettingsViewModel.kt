package com.kamalahmed.prayerapp.ui.settings

import android.app.Application
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.alarm.AdhanService
import com.kamalahmed.prayerapp.alarm.Sounds
import com.kamalahmed.prayerapp.alarm.SystemStatus
import com.kamalahmed.prayerapp.core.location.LocationRepository
import com.kamalahmed.prayerapp.core.location.LocationResult
import com.kamalahmed.prayerapp.core.model.SoundSource
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application,
    private val repo: SettingsRepository,
    private val location: LocationRepository,
) : AndroidViewModel(app) {

    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val status = MutableStateFlow(SystemStatus.read(app))
    val playing = AdhanService.playing
    val message = MutableStateFlow<String?>(null)

    fun refreshStatus() { status.value = SystemStatus.read(app) }

    fun update(fn: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.update(fn) }

    fun preview() {
        ContextCompat.startForegroundService(app, AdhanService.previewIntent(app))
    }

    fun stopPreview() { app.startService(AdhanService.stopIntent(app)) }

    fun setRingtone(uri: Uri?, title: String) = update {
        if (uri == null) it else it.copy(soundSource = SoundSource.RINGTONE, soundUri = uri.toString(), soundName = title)
    }

    fun importCustom(uri: Uri) = viewModelScope.launch {
        // Keep a persistable grant (useful for re-import), but play from a private copy for reliability.
        runCatching { app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val result = withContext(Dispatchers.IO) { Sounds.importCustom(app, uri) }
        if (result == null) message.value = "Could not read that audio file"
        else repo.update { it.copy(soundSource = SoundSource.CUSTOM_FILE, soundUri = result.first, soundName = result.second) }
    }

    fun refreshLocation() = viewModelScope.launch {
        message.value = when (val r = location.refresh(force = true)) {
            is LocationResult.Updated -> "Location set to ${r.city}"
            LocationResult.WithinGeofence -> "Location unchanged"
            LocationResult.NoPermission -> "Location permission not granted"
            LocationResult.Unavailable -> "Location unavailable – try again outdoors or pick a city"
        }
    }
}
