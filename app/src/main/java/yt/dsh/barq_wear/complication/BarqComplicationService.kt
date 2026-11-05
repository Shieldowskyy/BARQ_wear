package yt.dsh.barq_wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import yt.dsh.barq_wear.MainActivity
import yt.dsh.barq_wear.R
import yt.dsh.barq_wear.data.BarqAuthManager

/**
 * Watch face complication provider for BARQ! Wear.
 * Provides Short Text, Long Text, Monochromatic Image, and Small Image complication data
 * showing nearby fur counts and distance to closest profile with one-tap launch.
 */
class BarqComplicationService : SuspendingComplicationDataSourceService() {

    companion object {
        fun requestComplicationUpdate(context: Context) {
            runCatching {
                ComplicationDataSourceUpdateRequester.create(
                    context,
                    ComponentName(context, BarqComplicationService::class.java)
                ).requestUpdateAll()
            }
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val authManager = BarqAuthManager(applicationContext)
        val isLoggedIn = authManager.isLoggedIn()
        val cachedProfiles = authManager.getAnyCachedNearbyProfiles()

        val tapIntent = PendingIntent.getActivity(
            this,
            1001,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = Icon.createWithResource(this, R.mipmap.ic_launcher_round)
        val monochromaticImage = MonochromaticImage.Builder(icon).build()
        val smallImage = SmallImage.Builder(icon, SmallImageType.ICON).build()

        val closest = cachedProfiles.firstOrNull()
        val count = cachedProfiles.size
        val countText = if (count > 0) "🐾 $count" else "BARQ!"
        val distText = closest?.location?.distance?.let { "${it.toInt()}km" } ?: "0km"
        val closestName = closest?.displayName?.take(10) ?: "BARQ!"

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                val primaryText = if (!isLoggedIn) {
                    "BARQ"
                } else if (count > 0) {
                    countText
                } else {
                    "BARQ"
                }
                val titleText = if (count > 0 && closest != null) distText else "BARQ!"

                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(primaryText).build(),
                    contentDescription = PlainComplicationText.Builder("BARQ! Nearby").build()
                )
                    .setTitle(PlainComplicationText.Builder(titleText).build())
                    .setMonochromaticImage(monochromaticImage)
                    .setTapAction(tapIntent)
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                val primaryText = if (!isLoggedIn) {
                    "Tap to log in"
                } else if (count > 0 && closest != null) {
                    "$closestName • $distText"
                } else {
                    "No furs cached"
                }
                val titleText = if (count > 0) "🐾 $count nearby" else "BARQ! Nearby"

                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(primaryText).build(),
                    contentDescription = PlainComplicationText.Builder("BARQ! Nearby").build()
                )
                    .setTitle(PlainComplicationText.Builder(titleText).build())
                    .setMonochromaticImage(monochromaticImage)
                    .setTapAction(tapIntent)
                    .build()
            }

            ComplicationType.MONOCHROMATIC_IMAGE -> {
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = monochromaticImage,
                    contentDescription = PlainComplicationText.Builder("BARQ!").build()
                )
                    .setTapAction(tapIntent)
                    .build()
            }

            ComplicationType.SMALL_IMAGE -> {
                SmallImageComplicationData.Builder(
                    smallImage = smallImage,
                    contentDescription = PlainComplicationText.Builder("BARQ!").build()
                )
                    .setTapAction(tapIntent)
                    .build()
            }

            else -> null
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val icon = Icon.createWithResource(this, R.mipmap.ic_launcher_round)
        val monochromaticImage = MonochromaticImage.Builder(icon).build()
        val smallImage = SmallImage.Builder(icon, SmallImageType.ICON).build()

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("🐾 4").build(),
                    contentDescription = PlainComplicationText.Builder("BARQ! Nearby").build()
                )
                    .setTitle(PlainComplicationText.Builder("0 km").build())
                    .setMonochromaticImage(monochromaticImage)
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("Xeran • 0 km").build(),
                    contentDescription = PlainComplicationText.Builder("BARQ! Nearby").build()
                )
                    .setTitle(PlainComplicationText.Builder("🐾 4 nearby").build())
                    .setMonochromaticImage(monochromaticImage)
                    .build()
            }

            ComplicationType.MONOCHROMATIC_IMAGE -> {
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = monochromaticImage,
                    contentDescription = PlainComplicationText.Builder("BARQ!").build()
                ).build()
            }

            ComplicationType.SMALL_IMAGE -> {
                SmallImageComplicationData.Builder(
                    smallImage = smallImage,
                    contentDescription = PlainComplicationText.Builder("BARQ!").build()
                ).build()
            }

            else -> null
        }
    }
}

