package com.kamalahmed.prayerapp.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives PackageInstaller session status. Launches the system confirmation when user action is required. */
@AndroidEntryPoint
class InstallReceiver : BroadcastReceiver() {
    @Inject lateinit var manager: UpdateManager

    override fun onReceive(context: Context, intent: Intent) {
        com.kamalahmed.prayerapp.core.safety.guard("InstallReceiver","status") {
            if (intent.action != ACTION) return@guard
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    manager.onPendingConfirmation(confirm)
                    // Do not rely on a background broadcast launching an Activity: Android can silently refuse it.
                    UpdateWorker.createChannel(context)
                    val open = android.app.PendingIntent.getActivity(context,7004,confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                    if (Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        androidx.core.app.NotificationManagerCompat.from(context).notify(7001,
                        androidx.core.app.NotificationCompat.Builder(context,UpdateWorker.CHANNEL).setSmallIcon(com.kamalahmed.prayerapp.R.drawable.ic_stat_update)
                            .setContentTitle("Confirm prayer app update").setContentText("Tap Update to finish in Android's installer")
                            .setContentIntent(open).addAction(0,"Update",open).setAutoCancel(true).build())
                    }
                } else manager.onInstallStatus(PackageInstaller.STATUS_FAILURE,"Installer confirmation was missing")
            } else manager.onInstallStatus(status,intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }

    companion object { const val ACTION = "com.kamalahmed.prayerapp.update.INSTALL_STATUS" }
}
