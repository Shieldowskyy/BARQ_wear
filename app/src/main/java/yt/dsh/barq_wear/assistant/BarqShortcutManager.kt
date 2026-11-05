package yt.dsh.barq_wear.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import yt.dsh.barq_wear.MainActivity
import yt.dsh.barq_wear.R

enum class BarqNavDestination(val routeId: String) {
    NEARBY("nearby"),
    PROFILE("profile"),
    ACCOUNT("account"),
    ACTIVITIES("activities"),
    NEWS("news"),
    RELATIONS("relations"),
    FILTERS("filters"),
    LOCATION("location"),
    SETTINGS("settings");

    companion object {
        fun fromString(query: String?): BarqNavDestination? {
            if (query.isNullOrBlank()) return null
            val normalized = query.trim().lowercase()

            return when {
                normalized in listOf("nearby", "blisko", "w pobliżu", "w poblizu", "ludzie w pobliżu", "osoby w pobliżu", "mapa", "furs") -> NEARBY
                normalized in listOf("profile", "mój profil", "moj profil", "profil", "mój profil barq", "moj profil barq") -> PROFILE
                normalized in listOf("account", "konto", "moje konto") -> ACCOUNT
                normalized in listOf("activities", "activity", "aktywności", "aktywnosci", "aktywność", "powiadomienia", "boop", "boopy", "notifications") -> ACTIVITIES
                normalized in listOf("news", "aktualności", "aktualnosci", "nowości", "nowosci", "wiadomości", "wiadomosci") -> NEWS
                normalized in listOf("relations", "relacje", "znajomi", "polubienia", "friends", "mutuals", "likes") -> RELATIONS
                normalized in listOf("filters", "filtry", "szukaj", "szukanie", "wyszukiwanie", "search") -> FILTERS
                normalized in listOf("location", "lokalizacja", "miasto", "gps", "zmień miasto", "zmien miasto", "city") -> LOCATION
                normalized in listOf("settings", "ustawienia", "opcje", "preferences", "options") -> SETTINGS
                else -> entries.firstOrNull { it.routeId.equals(normalized, ignoreCase = true) }
            }
        }
    }
}

data class BarqDestinationRequest(
    val destination: BarqNavDestination,
    val profileUuid: String? = null
)

object BarqShortcutManager {

    private const val CAPABILITY_OPEN_APP_FEATURE = "actions.intent.OPEN_APP_FEATURE"
    private const val PARAMETER_FEATURE = "feature"

    /**
     * Publishes dynamic shortcuts to Android ShortcutManager.
     * This indexes all sections directly on the device so Gemini and Google Assistant
     * can open any tab by voice without needing Play Store publication.
     */
    fun publishShortcuts(context: Context) {
        try {
            val resources = context.resources
            val shortcuts = listOf(
                createShortcut(
                    context = context,
                    id = "shortcut_nearby",
                    shortLabelRes = R.string.shortcut_nearby_short,
                    longLabelRes = R.string.shortcut_nearby_long,
                    destination = BarqNavDestination.NEARBY,
                    synonyms = resources.getStringArray(R.array.feature_nearby_synonyms).toList()
                ),
                createShortcut(
                    context = context,
                    id = "shortcut_profile",
                    shortLabelRes = R.string.shortcut_profile_short,
                    longLabelRes = R.string.shortcut_profile_long,
                    destination = BarqNavDestination.PROFILE,
                    synonyms = resources.getStringArray(R.array.feature_profile_synonyms).toList()
                ),
                createShortcut(
                    context = context,
                    id = "shortcut_account",
                    shortLabelRes = R.string.shortcut_account_short,
                    longLabelRes = R.string.shortcut_account_long,
                    destination = BarqNavDestination.ACCOUNT,
                    synonyms = resources.getStringArray(R.array.feature_account_synonyms).toList()
                ),
                createShortcut(
                    context = context,
                    id = "shortcut_activities",
                    shortLabelRes = R.string.shortcut_activities_short,
                    longLabelRes = R.string.shortcut_activities_long,
                    destination = BarqNavDestination.ACTIVITIES,
                    synonyms = resources.getStringArray(R.array.feature_activities_synonyms).toList()
                ),
                createShortcut(
                    context = context,
                    id = "shortcut_news",
                    shortLabelRes = R.string.shortcut_news_short,
                    longLabelRes = R.string.shortcut_news_long,
                    destination = BarqNavDestination.NEWS,
                    synonyms = resources.getStringArray(R.array.feature_news_synonyms).toList()
                ),
                createShortcut(
                    context = context,
                    id = "shortcut_relations",
                    shortLabelRes = R.string.shortcut_relations_short,
                    longLabelRes = R.string.shortcut_relations_long,
                    destination = BarqNavDestination.RELATIONS,
                    synonyms = resources.getStringArray(R.array.feature_relations_synonyms).toList()
                ),
                createShortcut(
                    context = context,
                    id = "shortcut_filters",
                    shortLabelRes = R.string.shortcut_filters_short,
                    longLabelRes = R.string.shortcut_filters_long,
                    destination = BarqNavDestination.FILTERS,
                    synonyms = resources.getStringArray(R.array.feature_filters_synonyms).toList()
                ),
                createShortcut(
                    context = context,
                    id = "shortcut_location",
                    shortLabelRes = R.string.shortcut_location_short,
                    longLabelRes = R.string.shortcut_location_long,
                    destination = BarqNavDestination.LOCATION,
                    synonyms = resources.getStringArray(R.array.feature_location_synonyms).toList()
                ),
                createShortcut(
                    context = context,
                    id = "shortcut_settings",
                    shortLabelRes = R.string.shortcut_settings_short,
                    longLabelRes = R.string.shortcut_settings_long,
                    destination = BarqNavDestination.SETTINGS,
                    synonyms = resources.getStringArray(R.array.feature_settings_synonyms).toList()
                )
            )

            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: Exception) {
            // Non-critical if shortcuts cannot be registered on older OS/devices
            e.printStackTrace()
        }
    }

    private fun createShortcut(
        context: Context,
        id: String,
        shortLabelRes: Int,
        longLabelRes: Int,
        destination: BarqNavDestination,
        synonyms: List<String>
    ): ShortcutInfoCompat {
        val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("barq://${destination.routeId}")).apply {
            setClass(context, MainActivity::class.java)
            setPackage(context.packageName)
            putExtra(PARAMETER_FEATURE, destination.routeId)
        }

        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(shortLabelRes))
            .setLongLabel(context.getString(longLabelRes))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher_round))
            .setIntent(launchIntent)
            .setCategories(setOf(CAPABILITY_OPEN_APP_FEATURE, "android.intent.category.DEFAULT"))
            .addCapabilityBinding(CAPABILITY_OPEN_APP_FEATURE, PARAMETER_FEATURE, synonyms)
            .build()
    }

    /**
     * Resolves an incoming Intent (Deep link, Assistant / Gemini Voice Command, Tile, etc.)
     * into a specific app destination.
     */
    fun parseDestination(intent: Intent?): BarqDestinationRequest? {
        if (intent == null) return null

        // 1. Direct Tile or Notification target
        val tileUuid = intent.getStringExtra("tile_profile_uuid")
        if (!tileUuid.isNullOrBlank()) {
            return BarqDestinationRequest(BarqNavDestination.PROFILE, profileUuid = tileUuid)
        }

        // 2. Extra passed from Google Assistant / Gemini
        val rawFeatureExtra = intent.getStringExtra("feature")
            ?: intent.getStringExtra("actions.intent.extra.FEATURE")
            ?: intent.getStringExtra("target_screen")
            ?: intent.getStringExtra("screen")
        if (!rawFeatureExtra.isNullOrBlank()) {
            val destination = BarqNavDestination.fromString(rawFeatureExtra)
            if (destination != null) {
                val uuid = intent.getStringExtra("uuid")
                return BarqDestinationRequest(destination, profileUuid = uuid)
            }
        }

        // 3. Deep link URI analysis
        val uri = intent.data ?: return null

        // If it's a login token deep link, do not treat as navigation
        val hasLoginToken = uri.getQueryParameter("access_token") != null
            || uri.getQueryParameter("token") != null
            || uri.getQueryParameter("refresh_token") != null
        if (hasLoginToken) return null

        if (uri.scheme == "barq" || (uri.scheme == "https" && uri.host == "barq.app")) {
            val host = uri.host.orEmpty().lowercase()
            val pathSegments = uri.pathSegments

            // E.g. barq://open?feature=activities or barq://app?feature=profile
            val featureParam = uri.getQueryParameter("feature") ?: uri.getQueryParameter("screen")
            if (!featureParam.isNullOrBlank()) {
                val destination = BarqNavDestination.fromString(featureParam)
                if (destination != null) {
                    val uuid = uri.getQueryParameter("uuid")
                    return BarqDestinationRequest(destination, profileUuid = uuid)
                }
            }

            // E.g. barq://profile or barq://profile/{uuid} or https://barq.app/profile/{uuid}
            val primarySegment = if (uri.scheme == "barq") {
                if (host.isNotBlank() && host != "app" && host != "open") host else pathSegments.firstOrNull().orEmpty()
            } else {
                pathSegments.firstOrNull().orEmpty()
            }

            val destination = BarqNavDestination.fromString(primarySegment)
            if (destination != null) {
                var uuid: String? = uri.getQueryParameter("uuid")
                if (uuid.isNullOrBlank()) {
                    if (uri.scheme == "barq" && pathSegments.isNotEmpty()) {
                        uuid = pathSegments.firstOrNull()
                    } else if (uri.scheme == "https" && pathSegments.size > 1) {
                        uuid = pathSegments[1]
                    }
                }
                if (uuid in listOf("me", "my", "mine", "self")) {
                    uuid = null
                }
                return BarqDestinationRequest(destination, profileUuid = uuid)
            }
        }

        return null
    }
}

