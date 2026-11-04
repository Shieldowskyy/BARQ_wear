package app.barq.wear.data

import android.content.Context
import androidx.annotation.StringRes
import app.barq.wear.R

data class UploadedImage(
    val uuid: String? = null,
    val url: String? = null,
    val contentRating: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val blurHash: String? = null
) {
    fun resolvedUrl(size: Int = 512): String? {
        if (!url.isNullOrBlank()) {
            return if (url.contains("?")) url else "$url?width=$size"
        }
        if (!uuid.isNullOrBlank()) {
            return "https://assets.barq.app/image/$uuid.jpeg?width=$size"
        }
        return null
    }
}

data class Place(
    val id: String? = null,
    val place: String? = null,
    val region: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val longitude: Double? = null,
    val latitude: Double? = null
) {
    fun formattedShort(fallback: String = "Unknown"): String {
        return listOfNotNull(
            place?.takeIf { it.isNotBlank() },
            countryCode?.takeIf { it.isNotBlank() } ?: country?.takeIf { it.isNotBlank() }
        ).joinToString(", ").ifBlank { fallback }
    }

    fun formattedFull(fallback: String = "Unknown"): String {
        return listOfNotNull(
            place?.takeIf { it.isNotBlank() },
            region?.takeIf { it.isNotBlank() },
            country?.takeIf { it.isNotBlank() } ?: countryCode?.takeIf { it.isNotBlank() }
        ).joinToString(", ").ifBlank { fallback }
    }
}

data class ProfileLocation(
    val distance: Double? = null,
    val type: String? = null,
    val precision: String? = null,
    val place: Place? = null,
    val homePlace: Place? = null
) {
    fun formattedDistanceKm(): String {
        val d = distance ?: return "— km"
        return "${Math.round(d)} km"
    }

    val isTraveling: Boolean
        get() = place?.id != null && homePlace?.id != null && place.id != homePlace.id
}

data class FilterOption(
    val id: String,
    @StringRes val labelRes: Int,
    val labelEn: String
)

object BarqFilterOptions {
    val GENDERS = listOf(
        FilterOption("Female", R.string.gender_female, "Female"),
        FilterOption("Male", R.string.gender_male, "Male"),
        FilterOption("Transgender Female", R.string.gender_trans_female, "Transgender Female"),
        FilterOption("Transgender Male", R.string.gender_trans_male, "Transgender Male"),
        FilterOption("Non-Binary", R.string.gender_non_binary, "Non-Binary")
    )

    val RELATIONSHIP_STATUSES = listOf(
        FilterOption("single", R.string.rel_single, "Single"),
        FilterOption("relationship", R.string.rel_relationship, "In a relationship"),
        FilterOption("open_relationship", R.string.rel_open_relationship, "In an open relationship"),
        FilterOption("engaged_married", R.string.rel_engaged_married, "Engaged / married"),
        FilterOption("domestic_partnership", R.string.rel_domestic_partnership, "Domestic partnership"),
        FilterOption("other", R.string.rel_other, "Other")
    )

    val SEXUAL_ORIENTATIONS = listOf(
        FilterOption("straight", R.string.orient_straight, "Straight"),
        FilterOption("bisexual", R.string.orient_bisexual, "Bisexual"),
        FilterOption("gay", R.string.orient_gay, "Gay"),
        FilterOption("lesbian", R.string.orient_lesbian, "Lesbian"),
        FilterOption("pansexual", R.string.orient_pansexual, "Pansexual"),
        FilterOption("asexual", R.string.orient_asexual, "Asexual"),
        FilterOption("questioning", R.string.orient_questioning, "Questioning"),
        FilterOption("other", R.string.orient_other, "Other")
    )

    val SEX_POSITIONS = listOf(
        FilterOption("top", R.string.pos_top, "Top"),
        FilterOption("bottom", R.string.pos_bottom, "Bottom")
    )

    val RELATION_TYPES = listOf(
        FilterOption("mutual", R.string.rel_type_mutual, "Mutuals ❤️"),
        FilterOption("friend", R.string.rel_type_friend, "Friends ⭐"),
        FilterOption("likedBy", R.string.rel_type_liked_by, "Liked you ✨"),
        FilterOption("liked", R.string.rel_type_liked, "Liked by me 🐾")
    )

    fun formatSelected(context: Context, selectedIds: Set<String>, options: List<FilterOption>): String {
        if (selectedIds.isEmpty()) return context.getString(R.string.filters_none_selected)
        val map = options.associateBy { it.id }
        return selectedIds.mapNotNull { id ->
            map[id]?.let { context.getString(it.labelRes) } ?: id
        }.joinToString(", ")
    }
}

data class BarqSearchFilters(
    val displayName: String = "",
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val genders: Set<String> = emptySet(),
    val relationshipStatus: Set<String> = emptySet(),
    val sexualOrientation: Set<String> = emptySet(),
    val requireProfileImage: Boolean = false,
    val sexPositions: Set<String> = emptySet(),
    val relationTypes: Set<String> = emptySet()
) {
    val activeCount: Int
        get() {
            var count = 0
            if (displayName.isNotBlank()) count++
            if (minAge != null && minAge > 0) count++
            if (maxAge != null && maxAge > 0) count++
            if (genders.isNotEmpty()) count++
            if (relationshipStatus.isNotEmpty()) count++
            if (sexualOrientation.isNotEmpty()) count++
            if (requireProfileImage) count++
            if (sexPositions.isNotEmpty()) count++
            if (relationTypes.isNotEmpty()) count++
            return count
        }

    val hasActiveFilters: Boolean
        get() = activeCount > 0
}

data class OverviewProfile(
    val uuid: String,
    val displayName: String,
    val username: String? = null,
    val roles: List<String>? = null,
    val relationType: String? = null,
    val primaryImage: UploadedImage? = null,
    val primaryImageAd: UploadedImage? = null,
    val location: ProfileLocation? = null
) {
    val distanceKmForSort: Double
        get() = location?.distance ?: Double.MAX_VALUE

    fun avatarUrl(isAd: Boolean = false, size: Int = 300): String? {
        val img = if (isAd) (primaryImageAd ?: primaryImage) else primaryImage
        return img?.resolvedUrl(size)
    }

    val isLikedOrMutual: Boolean
        get() = relationType == "liked" || relationType == "mutual"

    fun relationBadgeLabel(): String? = when (relationType) {
        "mutual" -> "Dopasowanie (Mutual)"
        "liked" -> "Polubiono"
        "likedBy" -> "Lubi Cię! (Odaj lajka)"
        "friend" -> "Znajomy"
        else -> null
    }

    fun roleBadge(): String? {
        val r = roles ?: return null
        return when {
            r.contains("team") -> "TEAM"
            r.contains("supporter_vip") -> "VIP"
            r.contains("supporter") -> "SUPPORTER"
            else -> null
        }
    }
}

data class Species(
    val id: String? = null,
    val displayName: String? = null
)

data class SonaImage(
    val id: String? = null,
    val image: UploadedImage? = null,
    val isAd: Boolean? = null
)

data class Sona(
    val id: String,
    val displayName: String,
    val hasFursuit: Boolean = false,
    val species: Species? = null,
    val images: List<SonaImage>? = null
) {
    fun mainImageUrl(showNsfw: Boolean = false, size: Int = 400): String? {
        val list = images.orEmpty()
        val filtered = if (showNsfw) {
            list
        } else {
            list.filter { it.isAd != true && it.image?.contentRating != "explicit" }
        }
        return filtered.firstOrNull()?.image?.resolvedUrl(size)
    }
}

data class KinkInfo(
    val id: String? = null,
    val displayName: String? = null,
    val categoryName: String? = null,
    val isVerified: Boolean = false,
    val isSinglePlayer: Boolean = false
)

data class ProfileKink(
    val pleasureGive: Int? = null,
    val pleasureReceive: Int? = null,
    val kink: KinkInfo? = null
)

data class Interest(
    val interest: String? = null
)

data class ProfileBio(
    val biography: String? = null,
    val genders: List<String>? = null,
    val languages: List<String>? = null,
    val relationshipStatus: String? = null,
    val sexualOrientation: String? = null,
    val interests: List<String>? = null,
    val hobbies: List<Interest>? = null
)

data class ProfileBioAd(
    val biography: String? = null,
    val sexPositions: List<String>? = null,
    val behaviour: List<String>? = null,
    val safeSex: String? = null,
    val canHost: Boolean? = null
) {
    val hasAnyContent: Boolean
        get() = !biography.isNullOrBlank() ||
            !sexPositions.isNullOrEmpty() ||
            !behaviour.isNullOrEmpty() ||
            !safeSex.isNullOrBlank() ||
            canHost != null
}

data class ProfileImageItem(
    val id: String,
    val image: UploadedImage? = null,
    val accessPermission: String? = null,
    val isAd: Boolean = false,
    val likeCount: Int = 0,
    val hasLiked: Boolean = false
)

data class ProfileSocialAccount(
    val id: String? = null,
    val socialNetwork: String? = null,
    val isVerified: Boolean = false,
    val url: String? = null,
    val displayName: String? = null,
    val value: String? = null
)

data class GroupInfo(
    val uuid: String,
    val displayName: String,
    val isAd: Boolean = false,
    val isVerified: Boolean = false,
    val image: UploadedImage? = null
)

data class ProfileGroup(
    val group: GroupInfo? = null,
    val threadCount: Int = 0,
    val replyCount: Int = 0
)

data class EventInfo(
    val uuid: String,
    val displayName: String,
    val isAd: Boolean = false,
    val eventBeginAt: String? = null,
    val eventEndAt: String? = null,
    val image: UploadedImage? = null
)

data class ProfileEvent(
    val isWaitingList: Boolean = false,
    val event: EventInfo? = null
)

data class ProfileDetail(
    val id: String? = null,
    val uuid: String,
    val displayName: String,
    val username: String? = null,
    val relationType: String? = null,
    val isAdOptIn: Boolean = false,
    val isBirthday: Boolean = false,
    val age: Int? = null,
    val primaryImage: UploadedImage? = null,
    val primaryImageAd: UploadedImage? = null,
    val headerImage: UploadedImage? = null,
    val headerImageAd: UploadedImage? = null,
    val images: List<ProfileImageItem>? = null,
    val location: ProfileLocation? = null,
    val bio: ProfileBio? = null,
    val bioAd: ProfileBioAd? = null,
    val kinks: List<ProfileKink>? = null,
    val socialAccounts: List<ProfileSocialAccount>? = null,
    val sonas: List<Sona>? = null,
    val groups: List<ProfileGroup>? = null,
    val events: List<ProfileEvent>? = null,
    val roles: List<String>? = null
) {
    fun avatarUrl(isAd: Boolean = false, size: Int = 400): String? {
        val img = if (isAd) (primaryImageAd ?: primaryImage) else primaryImage
        return img?.resolvedUrl(size)
    }

    fun bannerUrl(isAd: Boolean = false, size: Int = 600): String? {
        val img = if (isAd) (headerImageAd ?: headerImage) else headerImage
        return img?.resolvedUrl(size)
    }

    val isLikedOrMutual: Boolean
        get() = relationType == "liked" || relationType == "mutual"

    @StringRes
    fun relationButtonTextRes(): Int = when (relationType) {
        "mutual" -> R.string.relation_btn_mutual
        "liked" -> R.string.relation_btn_liked
        "likedBy" -> R.string.relation_btn_liked_by
        "friend" -> R.string.relation_btn_friend
        else -> R.string.relation_btn_like
    }
}

data class CurrentUser(
    val profile: ProfileDetail? = null,
    val isAdOptIn: Boolean = false,
    val isExplicitContentOptIn: Boolean = false,
    val likeCount: Int = 0,
    val mutualCount: Int = 0,
    val unreadMessagesCount: Int = 0
)

enum class RelationListType {
    MUTUALS,
    LIKES,
    FRIENDS
}

data class NewsItem(
    val id: String? = null,
    val title: String = "",
    val slug: String = "",
    val category: String? = null,
    val publishedAt: String? = null,
    val updatedAt: String? = null,
    val content: String? = null
) {
    fun formattedDate(): String {
        val raw = publishedAt ?: updatedAt ?: return ""
        return raw.take(10)
    }
}


