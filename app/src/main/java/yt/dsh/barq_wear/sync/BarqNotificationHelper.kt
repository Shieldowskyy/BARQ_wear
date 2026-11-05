package yt.dsh.barq_wear.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yt.dsh.barq_wear.MainActivity
import yt.dsh.barq_wear.R
import yt.dsh.barq_wear.data.ActivityItem

object BarqNotificationHelper {

    const val CHANNEL_ID = "barq_social_activities"
    private const val NOTIFICATION_ID = 2001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "BARQ! Social Activities"
            val descriptionText = "Notifications for Boops, Likes, and Mutuals"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    suspend fun showActivityNotification(context: Context, activity: ActivityItem) = withContext(Dispatchers.IO) {
        createNotificationChannel(context)

        val actor = activity.sourceProfile
        val actorName = actor?.displayName?.takeIf { it.isNotBlank() } ?: "Someone"

        val (title, text) = when (activity.type) {
            "PROFILE_BOOPED" -> {
                val count = activity.boopCount
                val countLabel = if (count > 1) " ($count boops)" else ""
                "🐾 Boop!$countLabel" to "$actorName booped you!"
            }
            "PROFILE_LIKED" -> {
                "❤️ New Profile Like" to "$actorName liked your profile!"
            }
            "PROFILE_MATCHED" -> {
                "🎉 New Mutual Match!" to "You and $actorName liked each other!"
            }
            "PROFILE_IMAGE_LIKED" -> {
                "📸 Photo Liked" to "$actorName liked your photo!"
            }
            else -> {
                "🐾 BARQ! Notification" to "New activity from $actorName"
            }
        }

        // Tap action: opens app directly to the actor's profile or home
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!actor?.uuid.isNullOrBlank()) {
                putExtra("tile_profile_uuid", actor?.uuid)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            activity.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Try downloading avatar for large icon if available
        var largeBitmap: Bitmap? = null
        val avatarUrl = actor?.primaryImage?.resolvedUrl(128)
        if (!avatarUrl.isNullOrBlank()) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .size(128)
                    .build()
                val result = Coil.imageLoader(context).execute(req)
                largeBitmap = (result.drawable as? BitmapDrawable)?.bitmap
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (largeBitmap != null) {
            builder.setLargeIcon(largeBitmap)
        }

        // Quick action: "Boop back!" directly from notification without opening the app
        val targetUuid = actor?.uuid
        if (activity.type == "PROFILE_BOOPED" && !targetUuid.isNullOrBlank()) {
            val boopBackIntent = Intent(context, BarqBoopBackReceiver::class.java).apply {
                action = BarqBoopBackReceiver.ACTION_BOOP_BACK
                putExtra(BarqBoopBackReceiver.EXTRA_TARGET_UUID, targetUuid)
                putExtra(BarqBoopBackReceiver.EXTRA_TARGET_NAME, actorName)
                putExtra(BarqBoopBackReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
            }
            val boopBackPendingIntent = PendingIntent.getBroadcast(
                context,
                targetUuid.hashCode(),
                boopBackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.mipmap.ic_launcher_round,
                    context.getString(R.string.notification_action_boop_back),
                    boopBackPendingIntent
                ).build()
            )
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Playful double-tap haptic vibration on watch for Boop action.
     */
    fun vibrateBoop(context: Context) {
        runCatching {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    android.os.VibrationEffect.createWaveform(longArrayOf(0, 60, 40, 80), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 60, 40, 80), -1)
            }
        }
    }
}
