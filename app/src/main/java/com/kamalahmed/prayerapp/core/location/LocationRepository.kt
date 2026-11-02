package com.kamalahmed.prayerapp.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface LocationResult {
    data class Updated(val city: String) : LocationResult
    data object WithinGeofence : LocationResult
    data object NoPermission : LocationResult
    data object Unavailable : LocationResult
}

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Fetch the device location (balanced power), honouring the geofence unless [force]. */
    @SuppressLint("MissingPermission")
    suspend fun refresh(force: Boolean = false): LocationResult {
        if (!hasPermission()) return LocationResult.NoPermission
        val client = LocationServices.getFusedLocationProviderClient(context)
        val loc = withTimeoutOrNull(15_000) {
            runCatching {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
            }.getOrNull() ?: runCatching { client.lastLocation.await() }.getOrNull()
        } ?: return LocationResult.Unavailable

        val s = settings.current()
        if (!force && !GeoFence.shouldUpdate(s.latitude, s.longitude, loc.latitude, loc.longitude, s.geofenceKm)) {
            return LocationResult.WithinGeofence
        }
        val city = reverseGeocode(loc.latitude, loc.longitude)
            ?: String.format(Locale.US, "%.3f, %.3f", loc.latitude, loc.longitude)
        settings.update {
            it.copy(latitude = loc.latitude, longitude = loc.longitude, cityName = city, timeZoneId = ZoneId.systemDefault().id)
        }
        return LocationResult.Updated(city)
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { list ->
                        cont.resume(list.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea })
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            }
        }.getOrNull()
    }
}
