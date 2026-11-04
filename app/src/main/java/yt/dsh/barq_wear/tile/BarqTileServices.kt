package yt.dsh.barq_wear.tile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Border
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import yt.dsh.barq_wear.MainActivity
import yt.dsh.barq_wear.R
import yt.dsh.barq_wear.data.BarqApiClient
import yt.dsh.barq_wear.data.BarqAuthManager
import yt.dsh.barq_wear.data.BarqSearchFilters
import yt.dsh.barq_wear.data.OverviewProfile
import yt.dsh.barq_wear.ui.AppLanguage
import yt.dsh.barq_wear.ui.LocaleHelper
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Full Wear OS Tile showing up to 4 closest nearby furs in a 2x2 grid with avatars,
 * display names, and distance in km.
 */
class BarqFullTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            BarqTileRenderer.buildTile(
                context = applicationContext,
                isCompact = false
            )
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        serviceScope.future {
            BarqTileRenderer.buildResources(
                context = applicationContext,
                version = requestParams.version,
                maxCount = 4
            )
        }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

/**
 * Compact / Small Wear OS Tile showing the 3 closest nearby furs in a minimal,
 * battery-friendly horizontal strip.
 */
class BarqCompactTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            BarqTileRenderer.buildTile(
                context = applicationContext,
                isCompact = true
            )
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        serviceScope.future {
            BarqTileRenderer.buildResources(
                context = applicationContext,
                version = requestParams.version,
                maxCount = 3
            )
        }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

object BarqTileRenderer {

    // 30-minute freshness interval to keep background wakeups minimal and save battery
    private const val FRESHNESS_INTERVAL_MS = 30L * 60L * 1000L
    private const val AVATAR_SIZE_PX = 56

    // True black OLED background & dark cards for minimal power usage
    private const val COLOR_BG = 0xFF000000.toInt()
    private const val COLOR_CARD = 0xFF1A1A22.toInt()
    private const val COLOR_BORDER = 0xFF2D2D3A.toInt()
    private const val COLOR_ORANGE = 0xFFFF6B2B.toInt()
    private const val COLOR_ORANGE_LIGHT = 0xFFFF8F5E.toInt()
    private const val COLOR_SKY = 0xFF38BDF8.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFFA1A1B5.toInt()

    fun notifyTilesUpdated(context: Context) {
        runCatching {
            val updater = TileService.getUpdater(context.applicationContext)
            updater.requestUpdate(BarqFullTileService::class.java)
            updater.requestUpdate(BarqCompactTileService::class.java)
        }
    }

    /**
     * Energy-efficient profile loader:
     * 1. Returns cached profiles from SharedPreferences if fresh (< 30 min) with ZERO network calls.
     * 2. If cache is stale, performs a single lightweight GraphQL query (limit = 6) using the saved city
     *    without waking up hardware GPS.
     * 3. Falls back to any previously cached profiles if offline.
     */
    suspend fun loadNearbyProfilesEnergyEfficient(
        context: Context,
        maxCount: Int
    ): List<OverviewProfile> {
        val authManager = BarqAuthManager(context)
        if (!authManager.isLoggedIn()) return emptyList()

        // 1. Check fresh local cache first (0 network, 0 GPS)
        val freshCached = authManager.getCachedNearbyProfiles(maxAgeMs = FRESHNESS_INTERVAL_MS)
        if (!freshCached.isNullOrEmpty()) {
            return freshCached.sortedBy { it.distanceKmForSort }.take(maxCount)
        }

        // 2. Perform a lightweight 6-item query using saved coordinates
        val apiClient = BarqApiClient(authManager)
        val savedPlace = authManager.getSavedPlace()
        val fetched = apiClient.searchProfiles(
            searchFilters = BarqSearchFilters(),
            isAd = false,
            latitude = savedPlace?.latitude,
            longitude = savedPlace?.longitude,
            cursor = "",
            limit = 6
        ).getOrNull()

        if (!fetched.isNullOrEmpty()) {
            val sorted = fetched.sortedBy { it.distanceKmForSort }
            authManager.saveCachedNearbyProfiles(sorted)
            return sorted.take(maxCount)
        }

        // 3. Offline fallback to any previously cached profiles
        return authManager.getAnyCachedNearbyProfiles()
            .sortedBy { it.distanceKmForSort }
            .take(maxCount)
    }

    suspend fun buildTile(context: Context, isCompact: Boolean): Tile {
        val authManager = BarqAuthManager(context)
        val lang = AppLanguage.fromCode(authManager.getAppLanguageCode())
        val locCtx = LocaleHelper.localizedContext(context, lang)

        val maxItems = if (isCompact) 3 else 4
        val profiles = loadNearbyProfilesEnergyEfficient(context, maxItems)
        val placeLabel = authManager.getSavedPlace()?.place

        // Resource version encodes the displayed UUIDs so resources are only re-fetched when profiles change
        val versionKey = if (profiles.isEmpty()) {
            "empty_v1"
        } else {
            profiles.joinToString("_") { it.uuid.take(8) }
        }

        val rootElement: LayoutElement = when {
            !authManager.isLoggedIn() -> buildMessageLayout(
                locCtx = locCtx,
                title = locCtx.getString(R.string.tile_header_nearby),
                message = locCtx.getString(R.string.tile_login_required)
            )
            profiles.isEmpty() -> buildMessageLayout(
                locCtx = locCtx,
                title = locCtx.getString(R.string.tile_header_nearby),
                message = locCtx.getString(R.string.tile_empty_nearby)
            )
            isCompact -> buildCompactLayout(locCtx, profiles, placeLabel)
            else -> buildFullLayout(locCtx, profiles, placeLabel)
        }

        val timeline = Timeline.Builder()
            .addTimelineEntry(
                TimelineEntry.Builder()
                    .setLayout(
                        Layout.Builder()
                            .setRoot(rootElement)
                            .build()
                    )
                    .build()
            )
            .build()

        return Tile.Builder()
            .setResourcesVersion(versionKey)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
            .setTileTimeline(timeline)
            .build()
    }

    suspend fun buildResources(
        context: Context,
        version: String,
        maxCount: Int
    ): ResourceBuilders.Resources {
        val builder = ResourceBuilders.Resources.Builder()
            .setVersion(version)

        val authManager = BarqAuthManager(context)
        val profiles = authManager.getAnyCachedNearbyProfiles()
            .sortedBy { it.distanceKmForSort }
            .take(maxCount)

        for (profile in profiles) {
            val resId = avatarResId(profile.uuid)
            val avatarBytes = loadCircularAvatarPng(context, profile.avatarUrl(isAd = false, size = 96))
            if (avatarBytes != null) {
                builder.addIdToImageMapping(
                    resId,
                    ResourceBuilders.ImageResource.Builder()
                        .setInlineResource(
                            ResourceBuilders.InlineImageResource.Builder()
                                .setData(avatarBytes)
                                .setWidthPx(AVATAR_SIZE_PX)
                                .setHeightPx(AVATAR_SIZE_PX)
                                .build()
                        )
                        .build()
                )
            }
        }

        return builder.build()
    }

    private fun avatarResId(uuid: String): String = "avatar_${uuid.take(12)}"

    /**
     * Full Tile Layout:
     * Header ("BARQ! Nearby • <City>") + 2x2 Grid of 4 closest furs (Avatar + Name + Distance km) + Open button
     */
    private fun buildFullLayout(
        locCtx: Context,
        profiles: List<OverviewProfile>,
        placeLabel: String?
    ): LayoutElement {
        val headerText = if (!placeLabel.isNullOrBlank()) {
            "🐾 $placeLabel"
        } else {
            "🐾 ${locCtx.getString(R.string.tile_header_nearby)}"
        }

        val row1Items = profiles.take(2)
        val row2Items = profiles.drop(2).take(2)

        val column = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            // Header
            .addContent(
                Text.Builder()
                    .setText(headerText)
                    .setMaxLines(1)
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(sp(11f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(argb(COLOR_ORANGE))
                            .build()
                    )
                    .setModifiers(
                        Modifiers.Builder()
                            .setClickable(launchAppClickable(locCtx, null))
                            .setPadding(
                                Padding.Builder()
                                    .setBottom(dp(4f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )

        if (row1Items.isNotEmpty()) {
            column.addContent(buildFullGridRow(locCtx, row1Items))
        }
        if (row2Items.isNotEmpty()) {
            column.addContent(Spacer.Builder().setHeight(dp(4f)).build())
            column.addContent(buildFullGridRow(locCtx, row2Items))
        }

        // Bottom pill to open BARQ!
        column.addContent(Spacer.Builder().setHeight(dp(5f)).build())
        column.addContent(
            Box.Builder()
                .setModifiers(
                    Modifiers.Builder()
                        .setClickable(launchAppClickable(locCtx, null))
                        .setBackground(
                            Background.Builder()
                                .setColor(argb(COLOR_CARD))
                                .setCorner(Corner.Builder().setRadius(dp(10f)).build())
                                .build()
                        )
                        .setBorder(
                            Border.Builder()
                                .setWidth(dp(1f))
                                .setColor(argb(COLOR_BORDER))
                                .build()
                        )
                        .setPadding(
                            Padding.Builder()
                                .setStart(dp(10f))
                                .setEnd(dp(10f))
                                .setTop(dp(3f))
                                .setBottom(dp(3f))
                                .build()
                        )
                        .build()
                )
                .addContent(
                    Text.Builder()
                        .setText(locCtx.getString(R.string.tile_open_app))
                        .setFontStyle(
                            FontStyle.Builder()
                                .setSize(sp(10f))
                                .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                .setColor(argb(COLOR_ORANGE_LIGHT))
                                .build()
                        )
                        .build()
                )
                .build()
        )

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(Background.Builder().setColor(argb(COLOR_BG)).build())
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(12f))
                            .setEnd(dp(12f))
                            .setTop(dp(10f))
                            .setBottom(dp(10f))
                            .build()
                    )
                    .build()
            )
            .addContent(column.build())
            .build()
    }

    private fun buildFullGridRow(
        locCtx: Context,
        items: List<OverviewProfile>
    ): LayoutElement {
        val row = Row.Builder()
            .setWidth(wrap())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

        items.forEachIndexed { idx, profile ->
            if (idx > 0) {
                row.addContent(Spacer.Builder().setWidth(dp(5f)).build())
            }
            row.addContent(buildFullProfileChip(locCtx, profile))
        }
        return row.build()
    }

    private fun buildFullProfileChip(
        locCtx: Context,
        profile: OverviewProfile
    ): LayoutElement {
        val distStr = profile.location?.formattedDistanceKm() ?: "0 km"
        val shortName = profile.displayName.take(9)

        val row = Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(buildAvatarElement(profile, sizeDp = 26f))
            .addContent(Spacer.Builder().setWidth(dp(5f)).build())
            .addContent(
                Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                    .addContent(
                        Text.Builder()
                            .setText(shortName)
                            .setMaxLines(1)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(10f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .setColor(argb(COLOR_TEXT_PRIMARY))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText(distStr)
                            .setMaxLines(1)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(9f))
                                    .setColor(argb(COLOR_ORANGE_LIGHT))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return Box.Builder()
            .setWidth(dp(84f))
            .setHeight(dp(38f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setClickable(launchAppClickable(locCtx, profile.uuid))
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(COLOR_CARD))
                            .setCorner(Corner.Builder().setRadius(dp(12f)).build())
                            .build()
                    )
                    .setBorder(
                        Border.Builder()
                            .setWidth(dp(1f))
                            .setColor(argb(COLOR_BORDER))
                            .build()
                    )
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(6f))
                            .setEnd(dp(6f))
                            .build()
                    )
                    .build()
            )
            .addContent(row)
            .build()
    }

    /**
     * Compact / Small Tile Layout:
     * Minimal centered pill with 3 closest furs (circular avatar + distance badge + short name).
     */
    private fun buildCompactLayout(
        locCtx: Context,
        profiles: List<OverviewProfile>,
        placeLabel: String?
    ): LayoutElement {
        val stripRow = Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

        profiles.take(3).forEachIndexed { index, profile ->
            if (index > 0) {
                stripRow.addContent(Spacer.Builder().setWidth(dp(6f)).build())
            }
            val distStr = profile.location?.formattedDistanceKm() ?: "0 km"
            val shortName = profile.displayName.take(7)

            val itemCol = Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setModifiers(
                    Modifiers.Builder()
                        .setClickable(launchAppClickable(locCtx, profile.uuid))
                        .setBackground(
                            Background.Builder()
                                .setColor(argb(COLOR_CARD))
                                .setCorner(Corner.Builder().setRadius(dp(12f)).build())
                                .build()
                        )
                        .setBorder(
                            Border.Builder()
                                .setWidth(dp(1f))
                                .setColor(argb(COLOR_BORDER))
                                .build()
                        )
                        .setPadding(
                            Padding.Builder()
                                .setStart(dp(6f))
                                .setEnd(dp(6f))
                                .setTop(dp(6f))
                                .setBottom(dp(6f))
                                .build()
                        )
                        .build()
                )
                .addContent(buildAvatarElement(profile, sizeDp = 30f))
                .addContent(Spacer.Builder().setHeight(dp(3f)).build())
                .addContent(
                    Text.Builder()
                        .setText(shortName)
                        .setMaxLines(1)
                        .setFontStyle(
                            FontStyle.Builder()
                                .setSize(sp(9f))
                                .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                .setColor(argb(COLOR_TEXT_PRIMARY))
                                .build()
                        )
                        .build()
                )
                .addContent(
                    Text.Builder()
                        .setText(distStr)
                        .setMaxLines(1)
                        .setFontStyle(
                            FontStyle.Builder()
                                .setSize(sp(8f))
                                .setColor(argb(COLOR_SKY))
                                .build()
                        )
                        .build()
                )
                .build()

            stripRow.addContent(itemCol)
        }

        val titleStr = if (!placeLabel.isNullOrBlank()) {
            "BARQ! • $placeLabel"
        } else {
            locCtx.getString(R.string.tile_header_nearby)
        }

        val outerCol = Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder()
                    .setText(titleStr)
                    .setMaxLines(1)
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(sp(10f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(argb(COLOR_ORANGE))
                            .build()
                    )
                    .setModifiers(
                        Modifiers.Builder()
                            .setClickable(launchAppClickable(locCtx, null))
                            .setPadding(Padding.Builder().setBottom(dp(5f)).build())
                            .build()
                    )
                    .build()
            )
            .addContent(stripRow.build())
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(Background.Builder().setColor(argb(COLOR_BG)).build())
                    .build()
            )
            .addContent(outerCol)
            .build()
    }

    private fun buildAvatarElement(profile: OverviewProfile, sizeDp: Float): LayoutElement {
        val hasImage = profile.avatarUrl(isAd = false, size = 96) != null
        if (hasImage) {
            return Image.Builder()
                .setResourceId(avatarResId(profile.uuid))
                .setWidth(dp(sizeDp))
                .setHeight(dp(sizeDp))
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FIT)
                .build()
        }
        val initial = profile.displayName.firstOrNull()?.uppercase() ?: "🐾"
        return Box.Builder()
            .setWidth(dp(sizeDp))
            .setHeight(dp(sizeDp))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(COLOR_BORDER))
                            .setCorner(Corner.Builder().setRadius(dp(sizeDp / 2f)).build())
                            .build()
                    )
                    .setBorder(
                        Border.Builder()
                            .setWidth(dp(1.5f))
                            .setColor(argb(COLOR_ORANGE))
                            .build()
                    )
                    .build()
            )
            .addContent(
                Text.Builder()
                    .setText(initial)
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(sp(11f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(argb(COLOR_ORANGE))
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun buildMessageLayout(
        locCtx: Context,
        title: String,
        message: String
    ): LayoutElement {
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(Background.Builder().setColor(argb(COLOR_BG)).build())
                    .setClickable(launchAppClickable(locCtx, null))
                    .setPadding(Padding.Builder().setAll(dp(16f)).build())
                    .build()
            )
            .addContent(
                Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText("🐾 $title")
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(13f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .setColor(argb(COLOR_ORANGE))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(Spacer.Builder().setHeight(dp(6f)).build())
                    .addContent(
                        Text.Builder()
                            .setText(message)
                            .setMaxLines(2)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setColor(argb(COLOR_TEXT_SECONDARY))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun launchAppClickable(context: Context, profileUuid: String?): Clickable {
        val activityBuilder = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(context.packageName)
            .setClassName(MainActivity::class.java.name)

        if (!profileUuid.isNullOrBlank()) {
            activityBuilder.addKeyToExtraMapping(
                "tile_profile_uuid",
                ActionBuilders.stringExtra(profileUuid)
            )
        }

        return Clickable.Builder()
            .setId(profileUuid?.take(12) ?: "open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(activityBuilder.build())
                    .build()
            )
            .build()
    }

    /**
     * Loads the avatar from Coil's disk/memory cache (or network if needed) and crops it into
     * a 56x56 circular PNG with a subtle orange border.
     */
    private suspend fun loadCircularAvatarPng(context: Context, url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(Size(AVATAR_SIZE_PX, AVATAR_SIZE_PX))
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()

            val drawable = loader.execute(request).drawable ?: return null
            val srcBitmap = Bitmap.createBitmap(
                AVATAR_SIZE_PX,
                AVATAR_SIZE_PX,
                Bitmap.Config.ARGB_8888
            )
            val srcCanvas = Canvas(srcBitmap)
            drawable.setBounds(0, 0, AVATAR_SIZE_PX, AVATAR_SIZE_PX)
            drawable.draw(srcCanvas)

            val output = Bitmap.createBitmap(
                AVATAR_SIZE_PX,
                AVATAR_SIZE_PX,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(output)
            val radius = min(AVATAR_SIZE_PX, AVATAR_SIZE_PX) / 2f

            val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(srcBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(radius, radius, radius - 2f, shaderPaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = COLOR_ORANGE
            }
            canvas.drawCircle(radius, radius, radius - 1.5f, borderPaint)

            val bos = ByteArrayOutputStream()
            output.compress(Bitmap.CompressFormat.PNG, 90, bos)
            srcBitmap.recycle()
            output.recycle()
            bos.toByteArray()
        }.getOrNull()
    }
}

