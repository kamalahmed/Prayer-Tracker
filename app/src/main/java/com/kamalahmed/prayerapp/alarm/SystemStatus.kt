package com.kamalahmed.prayerapp.alarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Everything that can silently block prayer alarms, for the System settings screen. */
data class SystemStatus(
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
    val batteryUnrestricted: Boolean,
    val fullScreenAllowed: Boolean,
    val adhanChannelBlocked: Boolean,
    val locationAllowed: Boolean,
) {
    /** Problems that actually block alarms (full-screen only matters when the setting is on). */
    fun hasProblem(fullScreenEnabled: Boolean) =
        !notificationsAllowed || !exactAlarmsAllowed || !batteryUnrestricted || adhanChannelBlocked ||
            (fullScreenEnabled && !fullScreenAllowed)

    companion object {
        fun read(ctx: Context): SystemStatus {
            val pm = ctx.getSystemService(PowerManager::class.java)
            return SystemStatus(
                notificationsAllowed = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
                exactAlarmsAllowed = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
                batteryUnrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName),
                fullScreenAllowed = AdhanService.canUseFullScreen(ctx),
                adhanChannelBlocked = Channels.isBlocked(ctx, Channels.ADHAN),
                locationAllowed = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
            )
        }

        fun notificationSettings(ctx: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

        fun exactAlarmSettings(ctx: Context) = if (Build.VERSION.SDK_INT >= 31)
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
        else appDetails(ctx)

        @android.annotation.SuppressLint("BatteryLife")
        fun batterySettings(ctx: Context) =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))

        fun fullScreenSettings(ctx: Context) = if (Build.VERSION.SDK_INT >= 34)
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
        else notificationSettings(ctx)

        fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

        /** Launch [intent], falling back to the app details page if the OEM removed that screen. */
        fun open(ctx: Context, intent: Intent) {
            try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { ctx.startActivity(appDetails(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
