package yt.dsh.barq_wear.sync

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import yt.dsh.barq_wear.R
import yt.dsh.barq_wear.data.BarqApiClient
import yt.dsh.barq_wear.data.BarqAuthManager

class BarqBoopBackReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_BOOP_BACK = "yt.dsh.barq_wear.action.BOOP_BACK"
        const val EXTRA_TARGET_UUID = "extra_target_uuid"
        const val EXTRA_TARGET_NAME = "extra_target_name"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BOOP_BACK) return

        val targetUuid = intent.getStringExtra(EXTRA_TARGET_UUID) ?: return
        val targetName = intent.getStringExtra(EXTRA_TARGET_NAME) ?: "fur"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 2001)

        // Instant haptic feedback for user tap on watch
        BarqNotificationHelper.vibrateBoop(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val authManager = BarqAuthManager(context)
                val apiClient = BarqApiClient(authManager)

                val result = apiClient.boopProfile(targetUuid)
                result.fold(
                    onSuccess = { newBalance ->
                        authManager.setLastKnownBoopBalance(newBalance)

                        // Update notification to show success & remaining boop balance
                        val successText = context.getString(R.string.boop_back_success_fmt, targetName, newBalance)
                        val title = context.getString(R.string.boop_back_title)

                        val updatedNotification = NotificationCompat.Builder(context, BarqNotificationHelper.CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_launcher_round)
                            .setContentTitle(title)
                            .setContentText(successText)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(successText))
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true)
                            .setTimeoutAfter(6000)
                            .build()

                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(notificationId, updatedNotification)
                    },
                    onFailure = {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(notificationId)
                    }
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

