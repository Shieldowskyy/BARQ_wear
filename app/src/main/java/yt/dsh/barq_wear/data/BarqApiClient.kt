package yt.dsh.barq_wear.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class BarqApiClient(private val authManager: BarqAuthManager) {

    companion object {
        private const val GRAPHQL_ENDPOINT = "https://api.barq.app/graphql"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val FRAGMENTS_SEARCH = """
            fragment UploadedImageFragment on UploadedImage {
              uuid
              url
              contentRating
              width
              height
              blurHash
            }
            fragment ProfileSafePrimaryImageFragment on Profile {
              primaryImage {
                ...UploadedImageFragment
              }
            }
            fragment ProfileExplicitPrimaryImageFragment on Profile {
              primaryImageAd {
                ...UploadedImageFragment
              }
            }
            fragment ProfilePrimaryImagesFragment on Profile {
              ...ProfileSafePrimaryImageFragment
              ...ProfileExplicitPrimaryImageFragment
            }
            fragment MinimalProfileFragment on Profile {
              uuid
              displayName
              ...ProfileSafePrimaryImageFragment
            }
            fragment OverviewProfileFragment on Profile {
              ...MinimalProfileFragment
              username
              roles
              relationType
              location {
                distance
                type
                place {
                  id
                }
                homePlace {
                  id
                }
              }
            }
        """

        private const val QUERY_PROFILE_SEARCH = """
            query ProfileSearch(${'$'}isAd: Boolean = false, ${'$'}filters: ProfileSearchFiltersInput! = {}, ${'$'}cursor: String = "", ${'$'}limit: Int = 30) {
              profileSearch(
                filters: ${'$'}filters
                isAd: ${'$'}isAd
                cursor: ${'$'}cursor
                limit: ${'$'}limit
                sort: distance
              ) {
                ...OverviewProfileFragment
                ...ProfilePrimaryImagesFragment
              }
            }
            $FRAGMENTS_SEARCH
        """

        private const val FRAGMENTS_USER = """
            fragment UploadedImageFragment on UploadedImage {
              uuid
              url
              contentRating
              width
              height
              blurHash
            }
            fragment ProfileSafePrimaryImageFragment on Profile {
              primaryImage {
                ...UploadedImageFragment
              }
            }
            fragment ProfileExplicitPrimaryImageFragment on Profile {
              primaryImageAd {
                ...UploadedImageFragment
              }
            }
            fragment ProfilePrimaryImagesFragment on Profile {
              ...ProfileSafePrimaryImageFragment
              ...ProfileExplicitPrimaryImageFragment
            }
            fragment ProfileSafeHeaderImageFragment on Profile {
              headerImage {
                ...UploadedImageFragment
              }
            }
            fragment ProfileExplicitHeaderImageFragment on Profile {
              headerImageAd {
                ...UploadedImageFragment
              }
            }
            fragment ProfileHeaderImagesFragment on Profile {
              ...ProfileSafeHeaderImageFragment
              ...ProfileExplicitHeaderImageFragment
            }
            fragment PlaceFragment on Place {
              id
              place
              region
              country
              countryCode
              longitude
              latitude
            }
            fragment ProfileLocationFragment on ProfileLocation {
              type
              homePlace {
                ...PlaceFragment
              }
              place {
                ...PlaceFragment
              }
            }
            fragment InterestFragment on Interest {
              interest
            }
            fragment ProfileBioFragment on ProfileBio {
              biography
              genders
              languages
              relationshipStatus
              sexualOrientation
              interests
              hobbies {
                ...InterestFragment
              }
            }
            fragment ProfileBioAdFragment on ProfileBioAd {
              biography
              sexPositions
              behaviour
              safeSex
              canHost
            }
            fragment ProfileImageFragment on ProfileImage {
              id
              image {
                ...UploadedImageFragment
              }
              accessPermission
              isAd
            }
            fragment ProfileSocialAccountFragment on ProfileSocialAccount {
              id
              socialNetwork
              isVerified
              url
              displayName
              value
              accessPermission
            }
            fragment SpeciesFragment on Species {
              id
              displayName
            }
            fragment SonaImageFragment on ProfileImage {
              id
              image {
                ...UploadedImageFragment
              }
              isAd
            }
            fragment SonaFragment on Sona {
              id
              displayName
              hasFursuit
              species {
                ...SpeciesFragment
              }
              images {
                ...SonaImageFragment
              }
            }
        """

        private const val FRAGMENTS_DETAIL = """
            $FRAGMENTS_USER
            fragment KinkFragment on Kink {
              id
              displayName
              categoryName
              isVerified
              isSinglePlayer
            }
            fragment ProfileKinkFragment on ProfileKink {
              pleasureGive
              pleasureReceive
              kink {
                ...KinkFragment
              }
            }
            fragment GroupFragment on Group {
              uuid
              displayName
              isAd
              isVerified
              image {
                ...UploadedImageFragment
              }
            }
            fragment ProfileGroupFragment on ProfileGroup {
              group {
                ...GroupFragment
              }
              threadCount
              replyCount
            }
            fragment EventFragment on Event {
              uuid
              displayName
              isAd
              eventBeginAt
              eventEndAt
              image {
                ...UploadedImageFragment
              }
            }
            fragment ProfileEventFragment on ProfileEvent {
              isWaitingList
              event {
                ...EventFragment
              }
            }
        """

        private const val QUERY_PROFILE_DETAIL = """
            query ProfileDetail(${'$'}uuid: String!) {
              profile(uuid: ${'$'}uuid) {
                id
                uuid
                displayName
                username
                relationType
                isAdOptIn
                isBirthday
                age
                ...ProfilePrimaryImagesFragment
                ...ProfileHeaderImagesFragment
                images {
                  ...ProfileImageFragment
                  likeCount
                  hasLiked
                }
                location {
                  ...ProfileLocationFragment
                  distance
                }
                bio {
                  ...ProfileBioFragment
                }
                socialAccounts {
                  ...ProfileSocialAccountFragment
                }
                bioAd {
                  ...ProfileBioAdFragment
                }
                kinks {
                  ...ProfileKinkFragment
                }
                sonas {
                  ...SonaFragment
                }
                groups {
                  ...ProfileGroupFragment
                }
                events {
                  ...ProfileEventFragment
                }
                roles
              }
            }
            $FRAGMENTS_DETAIL
        """

        private const val QUERY_CURRENT_USER = """
            query User {
              user {
                profile {
                  id
                  uuid
                  displayName
                  username
                  roles
                  age
                  ...ProfilePrimaryImagesFragment
                  ...ProfileHeaderImagesFragment
                  location {
                    ...ProfileLocationFragment
                    precision
                  }
                  images {
                    ...ProfileImageFragment
                  }
                  bio {
                    ...ProfileBioFragment
                  }
                  socialAccounts {
                    ...ProfileSocialAccountFragment
                  }
                  bioAd {
                    ...ProfileBioAdFragment
                  }
                  sonas {
                    ...SonaFragment
                  }
                }
                isAdOptIn
                isExplicitContentOptIn
                likeCount
                mutualCount
                activitySummary {
                  unreadActivitiesCount
                  chatActivity {
                    unreadMessageCount
                  }
                }
              }
            }
            $FRAGMENTS_USER
        """

        private const val MUTATION_LIKE_PROFILE = """
            mutation LikeProfile(${'$'}uuid: String!) {
              likeProfile(uuid: ${'$'}uuid)
            }
        """

        private const val MUTATION_UNLIKE_PROFILE = """
            mutation UnlikeProfile(${'$'}uuid: String!) {
              unlikeProfile(uuid: ${'$'}uuid)
            }
        """

        private const val MUTATION_LIKE_IMAGE = """
            mutation ProfileImageLike(${'$'}profileImageLikeId: ID!, ${'$'}liked: Boolean!) {
              profileImageLike(id: ${'$'}profileImageLikeId, liked: ${'$'}liked)
            }
        """

        private const val MUTATION_UNLOCK_AD_MODE = """
            mutation UserUnlockAdMode {
              userUnlockAdMode
            }
        """

        private const val QUERY_ACTIVITIES = """
            query UserActivities(${'$'}limit: Int) {
              user {
                activities(limit: ${'$'}limit) {
                  id
                  type
                  createdAt
                  readAt
                  payload
                  sourceProfile {
                    ...OverviewProfileFragment
                    ...ProfilePrimaryImagesFragment
                  }
                }
              }
            }
            $FRAGMENTS_SEARCH
        """

        private const val MUTATION_MARK_ACTIVITIES_READ = """
            mutation MarkActivitiesAsRead {
              markActivitiesAsRead
            }
        """

        private const val MUTATION_BOOP_PROFILE = """
            mutation BoopProfile(${'$'}profileUuid: String!) {
              boopProfile(profileUuid: ${'$'}profileUuid) {
                balance
              }
            }
        """

        private const val MUTATION_LOCK_AD_MODE = """
            mutation UserLockAdMode {
              userLockAdMode
            }
        """

        private const val MUTATION_GPS_UPDATE = """
            mutation UserLocationGpsUpdate(${'$'}longitude: Float!, ${'$'}latitude: Float!) {
              userLocationGpsUpdate(longitude: ${'$'}longitude, latitude: ${'$'}latitude) {
                place {
                  id
                  place
                  region
                  country
                  countryCode
                  longitude
                  latitude
                }
                type
                precision
              }
            }
        """

        private const val MUTATION_MANUAL_LOCATION_UPDATE = """
            mutation UserUpdateLocation(${'$'}input: UserUpdateLocationInput!) {
              userUpdateLocation(input: ${'$'}input) {
                place {
                  id
                  place
                  region
                  country
                  countryCode
                  longitude
                  latitude
                }
                type
                precision
              }
            }
        """

        private const val QUERY_PLACES = """
            query Places(${'$'}query: String!) {
              places(query: ${'$'}query) {
                id
                country
                ...PlaceFragment
                __typename
              }
            }

            fragment PlaceFragment on Place {
              id
              place
              region
              country
              countryCode
              longitude
              latitude
              __typename
            }
        """

        private const val QUERY_NEWS_ITEMS = """
            query NewsItems {
              newsItems {
                id
                title
                slug
                category
                audiences
                updatedAt
                publishedAt
                showOnNewsFeed
                showOnHomePage
              }
            }
        """

        private const val QUERY_NEWS_ITEM_DETAIL = """
            query NewsItem(${'$'}slug: String!) {
              newsItem(slug: ${'$'}slug) {
                id
                title
                slug
                category
                audiences
                updatedAt
                publishedAt
                showOnNewsFeed
                showOnHomePage
                content
              }
            }
        """
    }

    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private suspend fun executeGraphQl(
        operationName: String,
        query: String,
        variables: Map<String, Any?> = emptyMap()
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            var token = authManager.getValidAccessToken(forceRefresh = false)
                ?: return@withContext Result.failure(IllegalStateException("Brak aktywnej sesji BARQ! Zaloguj się ponownie."))

            val payload = JsonObject().apply {
                addProperty("operationName", operationName)
                addProperty("query", query)
                add("variables", gson.toJsonTree(variables))
            }
            val bodyString = gson.toJson(payload)

            var responseJson = executePostWithToken(bodyString, token)
            if (responseJson == null) {
                // Try refreshing token once on 401/403
                val refreshed = authManager.getValidAccessToken(forceRefresh = true)
                if (!refreshed.isNullOrBlank() && refreshed != token) {
                    token = refreshed
                    responseJson = executePostWithToken(bodyString, token)
                }
            }

            if (responseJson == null) {
                return@withContext Result.failure(IllegalStateException("Sesja wygasła lub błąd autoryzacji (401)."))
            }

            if (responseJson.has("errors") && !responseJson.get("errors").isJsonNull) {
                val errArr = responseJson.getAsJsonArray("errors")
                if (errArr.size() > 0) {
                    val msg = errArr[0].asJsonObject.get("message")?.asString ?: "Błąd GraphQL"
                    if (!responseJson.has("data") || responseJson.get("data").isJsonNull) {
                        return@withContext Result.failure(IllegalStateException(msg))
                    }
                }
            }

            val dataObj = responseJson.getAsJsonObject("data")
                ?: return@withContext Result.failure(IllegalStateException("Pusta odpowiedź z serwera API BARQ!"))

            Result.success(dataObj)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executePostWithToken(jsonBody: String, accessToken: String): JsonObject? {
        val request = Request.Builder()
            .url(GRAPHQL_ENDPOINT)
            .header("User-Agent", BarqAuthManager.USER_AGENT)
            .header("Authorization", "Bearer $accessToken")
            .header("Origin", "https://web.barq.app")
            .header("Referer", "https://web.barq.app/")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) {
                return null
            }
            val str = resp.body?.string().orEmpty()
            if (str.isNotBlank()) {
                val json = runCatching { JsonParser.parseString(str).asJsonObject }.getOrNull()
                if (json != null) {
                    return json
                }
            }
            if (!resp.isSuccessful || str.isBlank()) {
                throw IllegalStateException("Błąd HTTP ${resp.code}")
            }
            return JsonParser.parseString(str).asJsonObject
        }
    }

    private fun JsonObject.optObj(key: String): JsonObject? {
        val el = get(key) ?: return null
        return if (el.isJsonObject) el.asJsonObject else null
    }

    private fun JsonObject.optBool(key: String, default: Boolean = false): Boolean {
        val el = get(key) ?: return default
        return if (el.isJsonPrimitive) runCatching { el.asBoolean }.getOrDefault(default) else default
    }

    private fun JsonObject.optInt(key: String, default: Int = 0): Int {
        val el = get(key) ?: return default
        return if (el.isJsonPrimitive) runCatching { el.asInt }.getOrDefault(default) else default
    }

    suspend fun fetchCurrentUser(): Result<CurrentUser> {
        return executeGraphQl("User", QUERY_CURRENT_USER).mapCatching { data ->
            val userObj = data.optObj("user")
            val profileEl: JsonElement? = userObj?.get("profile")
            val profile = if (profileEl != null && !profileEl.isJsonNull) {
                gson.fromJson(profileEl, ProfileDetail::class.java)
            } else null

            if (profile != null && profile.uuid.isNotBlank()) {
                authManager.saveMyProfileUuid(profile.uuid)
            }

            val unreadChat = userObj?.optObj("activitySummary")
                ?.optObj("chatActivity")
                ?.optInt("unreadMessageCount") ?: 0

            val unreadActivities = userObj?.optObj("activitySummary")
                ?.optInt("unreadActivitiesCount") ?: 0

            CurrentUser(
                profile = profile,
                isAdOptIn = userObj?.optBool("isAdOptIn") ?: false,
                isExplicitContentOptIn = userObj?.optBool("isExplicitContentOptIn") ?: false,
                likeCount = userObj?.optInt("likeCount") ?: 0,
                mutualCount = userObj?.optInt("mutualCount") ?: 0,
                unreadMessagesCount = unreadChat,
                unreadActivitiesCount = unreadActivities
            )
        }
    }

    suspend fun searchProfiles(
        searchFilters: BarqSearchFilters = BarqSearchFilters(),
        isAd: Boolean = false,
        latitude: Double? = null,
        longitude: Double? = null,
        cursor: String = "",
        limit: Int = 60
    ): Result<List<OverviewProfile>> {
        val filters = mutableMapOf<String, Any>()

        if (searchFilters.displayName.isNotBlank()) {
            filters["displayName"] = searchFilters.displayName.trim()
        }

        val ageMap = mutableMapOf<String, Int>()
        searchFilters.minAge?.takeIf { it > 0 }?.let { ageMap["min"] = it }
        searchFilters.maxAge?.takeIf { it > 0 }?.let { ageMap["max"] = it }
        if (ageMap.isNotEmpty()) {
            filters["age"] = ageMap
        }

        if (searchFilters.genders.isNotEmpty()) {
            filters["genders"] = searchFilters.genders.toList()
        }

        if (searchFilters.relationshipStatus.isNotEmpty()) {
            filters["relationshipStatus"] = searchFilters.relationshipStatus.toList()
        }

        if (searchFilters.sexualOrientation.isNotEmpty()) {
            filters["sexualOrientation"] = searchFilters.sexualOrientation.toList()
        }

        if (searchFilters.requireProfileImage) {
            filters["requireProfileImage"] = true
        }

        if (searchFilters.sexPositions.isNotEmpty()) {
            filters["sexPositions"] = searchFilters.sexPositions.toList()
        }

        if (searchFilters.relationTypes.isNotEmpty()) {
            filters["relationTypes"] = searchFilters.relationTypes.toList()
        }

        if (latitude != null && longitude != null) {
            filters["location"] = mapOf(
                "type" to "distance",
                "latitude" to latitude,
                "longitude" to longitude
            )
        }

        val variables = mapOf(
            "isAd" to isAd,
            "filters" to filters,
            "cursor" to cursor,
            "limit" to limit
        )

        return executeGraphQl("ProfileSearch", QUERY_PROFILE_SEARCH, variables).mapCatching { data ->
            val listEl = data.get("profileSearch")
            if (listEl == null || listEl.isJsonNull) {
                emptyList()
            } else {
                val type = object : TypeToken<List<OverviewProfile>>() {}.type
                val parsed = gson.fromJson<List<OverviewProfile>>(listEl, type)
                    .filterNotNull()
                val filtered = if (searchFilters.relationTypes.isNotEmpty()) {
                    parsed.filter { p ->
                        val rel = p.relationType
                        rel in searchFilters.relationTypes ||
                            ("mutual" in searchFilters.relationTypes && rel == "friend")
                    }
                } else {
                    parsed
                }
                filtered.sortedBy { it.distanceKmForSort }
            }
        }
    }

    suspend fun fetchRelationProfiles(
        relationListType: RelationListType,
        isAd: Boolean = false,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<List<OverviewProfile>> {
        val filters = mutableMapOf<String, Any>()
        if (latitude != null && longitude != null) {
            filters["location"] = mapOf(
                "type" to "distance",
                "latitude" to latitude,
                "longitude" to longitude
            )
        }
        when (relationListType) {
            RelationListType.MUTUALS -> filters["relationTypes"] = listOf("mutual")
            RelationListType.FRIENDS -> filters["relationTypes"] = listOf("friend")
            RelationListType.LIKES -> {
                // Fetch nearby/recent profiles and filter to likedBy / liked / mutual
            }
        }
        val variables = mapOf(
            "isAd" to isAd,
            "filters" to filters,
            "cursor" to "",
            "limit" to 100
        )
        return executeGraphQl("ProfileSearch", QUERY_PROFILE_SEARCH, variables).mapCatching { data ->
            val listEl = data.get("profileSearch")
            val parsed = if (listEl == null || listEl.isJsonNull) {
                emptyList()
            } else {
                val type = object : TypeToken<List<OverviewProfile>>() {}.type
                gson.fromJson<List<OverviewProfile>>(listEl, type).filterNotNull()
            }
            when (relationListType) {
                RelationListType.MUTUALS -> parsed
                    .filter { it.relationType == "mutual" || it.relationType == "friend" }
                    .sortedBy { it.distanceKmForSort }
                RelationListType.FRIENDS -> parsed
                    .filter { it.relationType == "friend" }
                    .sortedBy { it.distanceKmForSort }
                RelationListType.LIKES -> parsed
                    .filter { it.relationType == "likedBy" || it.relationType == "liked" }
                    .sortedWith(
                        compareBy<OverviewProfile> {
                            when (it.relationType) {
                                "likedBy" -> 0
                                "liked" -> 1
                                else -> 2
                            }
                        }.thenBy { it.distanceKmForSort }
                    )
            }
        }
    }

    suspend fun fetchNewsItems(): Result<List<NewsItem>> {
        return executeGraphQl("NewsItems", QUERY_NEWS_ITEMS).mapCatching { data ->
            val listEl = data.get("newsItems")
            if (listEl == null || listEl.isJsonNull) {
                emptyList()
            } else {
                val type = object : TypeToken<List<NewsItem>>() {}.type
                gson.fromJson<List<NewsItem>>(listEl, type).filterNotNull()
            }
        }
    }

    suspend fun fetchNewsItemDetail(slug: String): Result<NewsItem> {
        return executeGraphQl("NewsItem", QUERY_NEWS_ITEM_DETAIL, mapOf("slug" to slug)).mapCatching { data ->
            val itemEl = data.get("newsItem")
            if (itemEl == null || itemEl.isJsonNull) {
                throw IllegalStateException("News item not found")
            }
            gson.fromJson(itemEl, NewsItem::class.java)
        }
    }

    suspend fun fetchProfileDetail(uuid: String): Result<ProfileDetail> {
        return executeGraphQl("ProfileDetail", QUERY_PROFILE_DETAIL, mapOf("uuid" to uuid)).mapCatching { data ->
            val profileEl = data.get("profile")
            if (profileEl == null || profileEl.isJsonNull) {
                throw IllegalStateException("Nie znaleziono profilu")
            }
            gson.fromJson(profileEl, ProfileDetail::class.java)
        }
    }

    suspend fun toggleLikeProfile(uuid: String, currentlyLikedOrMutual: Boolean): Result<String?> {
        val opName = if (currentlyLikedOrMutual) "UnlikeProfile" else "LikeProfile"
        val query = if (currentlyLikedOrMutual) MUTATION_UNLIKE_PROFILE else MUTATION_LIKE_PROFILE
        val fieldName = if (currentlyLikedOrMutual) "unlikeProfile" else "likeProfile"

        return executeGraphQl(opName, query, mapOf("uuid" to uuid)).mapCatching { data ->
            val el = data.get(fieldName)
            if (el == null || el.isJsonNull) null else el.asString
        }
    }

    suspend fun toggleLikeImage(imageId: String, liked: Boolean): Result<Int> {
        val vars = mapOf(
            "profileImageLikeId" to imageId,
            "liked" to liked
        )
        return executeGraphQl("ProfileImageLike", MUTATION_LIKE_IMAGE, vars).mapCatching { data ->
            data.get("profileImageLike")?.asInt ?: 0
        }
    }

    suspend fun updateGpsLocation(latitude: Double, longitude: Double): Result<Place?> {
        val roundedLat = Math.round(latitude * 1000.0) / 1000.0
        val roundedLon = Math.round(longitude * 1000.0) / 1000.0
        val vars = mapOf(
            "latitude" to roundedLat,
            "longitude" to roundedLon
        )
        return executeGraphQl("UserLocationGpsUpdate", MUTATION_GPS_UPDATE, vars).mapCatching { data ->
            val updateObj = data.optObj("userLocationGpsUpdate")
            val placeEl = updateObj?.get("place")
            if (placeEl != null && !placeEl.isJsonNull) {
                gson.fromJson(placeEl, Place::class.java)
            } else null
        }
    }

    suspend fun searchPlaces(query: String): Result<List<Place>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return Result.success(emptyList())
        return executeGraphQl("Places", QUERY_PLACES, mapOf("query" to trimmed)).mapCatching { data ->
            val listEl = data.get("places")
            if (listEl == null || listEl.isJsonNull) {
                emptyList()
            } else {
                val type = object : TypeToken<List<Place>>() {}.type
                gson.fromJson<List<Place>>(listEl, type)
                    .filterNotNull()
                    .distinctBy { it.id ?: "${it.place}_${it.region}_${it.country}" }
            }
        }
    }

    suspend fun updateManualLocation(latitude: Double, longitude: Double): Result<Place?> {
        val input = mapOf(
            "type" to "manual",
            "latitude" to latitude,
            "longitude" to longitude
        )
        return executeGraphQl("UserUpdateLocation", MUTATION_MANUAL_LOCATION_UPDATE, mapOf("input" to input)).mapCatching { data ->
            val updateObj = data.optObj("userUpdateLocation")
            val placeEl = updateObj?.get("place")
            if (placeEl != null && !placeEl.isJsonNull) {
                gson.fromJson(placeEl, Place::class.java)
            } else null
        }
    }

    suspend fun setAdModeUnlocked(unlocked: Boolean): Result<Boolean> {
        val opName = if (unlocked) "UserUnlockAdMode" else "UserLockAdMode"
        val query = if (unlocked) MUTATION_UNLOCK_AD_MODE else MUTATION_LOCK_AD_MODE
        val field = if (unlocked) "userUnlockAdMode" else "userLockAdMode"
        return executeGraphQl(opName, query).mapCatching { data ->
            data.optBool(field, unlocked)
        }
    }

    suspend fun fetchActivities(limit: Int = 30): Result<List<ActivityItem>> {
        val vars = mapOf("limit" to limit)
        return executeGraphQl("UserActivities", QUERY_ACTIVITIES, vars).mapCatching { data ->
            val userObj = data.optObj("user")
            val rawList = userObj?.getAsJsonArray("activities")
            if (rawList != null && !rawList.isJsonNull) {
                val listType = object : TypeToken<List<ActivityItem>>() {}.type
                gson.fromJson<List<ActivityItem>>(rawList, listType)
            } else {
                emptyList()
            }
        }
    }

    suspend fun markActivitiesAsRead(): Result<Boolean> {
        return executeGraphQl("MarkActivitiesAsRead", MUTATION_MARK_ACTIVITIES_READ).mapCatching { data ->
            data.optBool("markActivitiesAsRead", true)
        }
    }

    suspend fun boopProfile(profileUuid: String): Result<Int> {
        val vars = mapOf("profileUuid" to profileUuid.trim())
        return executeGraphQl("BoopProfile", MUTATION_BOOP_PROFILE, vars).mapCatching { data ->
            val boopObj = data.optObj("boopProfile")
            boopObj?.optInt("balance", 0) ?: 0
        }
    }
}

