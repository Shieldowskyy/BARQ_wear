package app.barq.wear.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.barq.wear.R
import app.barq.wear.data.BarqApiClient
import app.barq.wear.data.BarqAuthManager
import app.barq.wear.data.BarqSearchFilters
import app.barq.wear.data.CurrentUser
import app.barq.wear.data.OverviewProfile
import app.barq.wear.data.Place
import app.barq.wear.data.ProfileDetail
import app.barq.wear.data.QrAuthSession
import app.barq.wear.data.QrPollResult
import app.barq.wear.data.Sona
import app.barq.wear.tile.BarqTileRenderer
import coil.Coil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class BarqUiState(
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val isLoggedIn: Boolean = false,
    val qrSession: QrAuthSession? = null,
    val isLoadingQr: Boolean = false,
    val qrStatusMessage: String = "",
    val authError: String? = null,

    val currentUser: CurrentUser? = null,
    val myProfileUuid: String? = null,
    val isLoadingCurrentUser: Boolean = false,
    val currentPlace: Place? = null,
    val profiles: List<OverviewProfile> = emptyList(),
    val isLoadingProfiles: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasReachedEnd: Boolean = false,
    val profilesError: String? = null,
    val paginationOffset: Int = 0,

    val filters: BarqSearchFilters = BarqSearchFilters(),
    val isAdMode: Boolean = false,
    val showNsfw: Boolean = false,
    val needsAdNsfwOnboarding: Boolean = false,

    val gpsStatusMessage: String? = null,
    val isUpdatingGps: Boolean = false,
    val gpsUnavailable: Boolean = false,

    val placeSearchQuery: String = "",
    val placeSearchResults: List<Place> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val placeSearchError: String? = null,

    val imageCacheSizeBytes: Long = 0L,
    val isClearingCache: Boolean = false,

    val selectedProfile: ProfileDetail? = null,
    val isLoadingProfileDetail: Boolean = false,
    val profileDetailError: String? = null,
    val isLikingProfile: Boolean = false,
    val pendingTileProfileUuid: String? = null,

    val selectedSona: Sona? = null
) {
    val searchQuery: String
        get() = filters.displayName

    val requireProfileImage: Boolean
        get() = filters.requireProfileImage

    fun formattedCacheSize(): String {
        val bytes = imageCacheSizeBytes
        return when {
            bytes <= 0L -> "0 B"
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

class BarqViewModel(application: Application) : AndroidViewModel(application) {

    val authManager = BarqAuthManager(application)
    val apiClient = BarqApiClient(authManager)

    private val _uiState = MutableStateFlow(
        BarqUiState(
            appLanguage = AppLanguage.fromCode(authManager.getAppLanguageCode()),
            isLoggedIn = authManager.isLoggedIn(),
            currentPlace = authManager.getSavedPlace(),
            myProfileUuid = authManager.getMyProfileUuid(),
            isAdMode = authManager.isAdModeEnabled(),
            showNsfw = authManager.isNsfwEnabled(),
            needsAdNsfwOnboarding = authManager.isLoggedIn() && !authManager.hasAskedAdNsfwOnboarding()
        )
    )
    val uiState: StateFlow<BarqUiState> = _uiState.asStateFlow()

    private var qrPollJob: Job? = null
    private var searchJob: Job? = null

    private fun str(@StringRes resId: Int, vararg args: Any): String {
        val ctx = LocaleHelper.localizedContext(getApplication(), _uiState.value.appLanguage)
        return if (args.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *args)
    }

    fun setAppLanguage(language: AppLanguage) {
        authManager.saveAppLanguageCode(language.code)
        _uiState.update { it.copy(appLanguage = language) }
        BarqTileRenderer.notifyTilesUpdated(getApplication())
    }

    fun requestOpenProfileFromTile(uuid: String) {
        if (uuid.isBlank()) return
        loadProfileDetail(uuid)
        _uiState.update { it.copy(pendingTileProfileUuid = uuid) }
    }

    fun consumePendingTileProfileUuid() {
        _uiState.update { it.copy(pendingTileProfileUuid = null) }
    }

    init {
        refreshImageCacheSize()
        if (authManager.isLoggedIn()) {
            loadInitialData()
        } else {
            startQrLoginSession()
        }
    }

    fun loadInitialData() {
        qrPollJob?.cancel()
        val savedPlace = authManager.getSavedPlace()
        val savedUuid = authManager.getMyProfileUuid()
        _uiState.update {
            it.copy(
                isLoggedIn = true,
                authError = null,
                currentPlace = savedPlace ?: it.currentPlace,
                myProfileUuid = savedUuid ?: it.myProfileUuid,
                isAdMode = authManager.isAdModeEnabled(),
                showNsfw = authManager.isNsfwEnabled(),
                needsAdNsfwOnboarding = !authManager.hasAskedAdNsfwOnboarding()
            )
        }
        viewModelScope.launch {
            apiClient.fetchCurrentUser().onSuccess { user ->
                val userPlace = user.profile?.location?.place
                val userUuid = user.profile?.uuid
                if (!userUuid.isNullOrBlank()) {
                    authManager.saveMyProfileUuid(userUuid)
                }
                val effectivePlace = _uiState.value.currentPlace ?: savedPlace ?: userPlace
                if (effectivePlace != null && savedPlace == null) {
                    authManager.saveSelectedPlace(effectivePlace)
                }
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        myProfileUuid = userUuid ?: it.myProfileUuid,
                        currentPlace = effectivePlace
                    )
                }
            }
            refreshProfiles()
        }
    }

    fun startQrLoginSession() {
        qrPollJob?.cancel()
        _uiState.update {
            it.copy(
                isLoadingQr = true,
                qrSession = null,
                authError = null,
                qrStatusMessage = str(R.string.qr_status_generating)
            )
        }

        qrPollJob = viewModelScope.launch {
            val res = authManager.startQrAuthSession()
            res.onSuccess { session ->
                _uiState.update {
                    it.copy(
                        isLoadingQr = false,
                        qrSession = session,
                        qrStatusMessage = str(R.string.qr_status_scan_prompt)
                    )
                }
                while (isActive && !_uiState.value.isLoggedIn) {
                    delay(1800L)
                    when (val poll = authManager.pollQrApproval(session)) {
                        is QrPollResult.Pending -> Unit
                        is QrPollResult.ApprovedAndLoggedIn -> {
                            _uiState.update {
                                it.copy(
                                    qrStatusMessage = str(R.string.qr_status_approved),
                                    isLoggedIn = true
                                )
                            }
                            loadInitialData()
                            break
                        }
                        is QrPollResult.Rejected -> {
                            _uiState.update {
                                it.copy(
                                    authError = str(R.string.auth_err_rejected),
                                    qrStatusMessage = str(R.string.qr_status_rejected_hint)
                                )
                            }
                            break
                        }
                        is QrPollResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    authError = poll.message,
                                    qrStatusMessage = str(R.string.qr_status_error)
                                )
                            }
                            break
                        }
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingQr = false,
                        authError = err.message ?: str(R.string.auth_err_connection),
                        qrStatusMessage = str(R.string.qr_status_fetch_failed)
                    )
                }
            }
        }
    }

    fun loginWithManualToken(rawToken: String) {
        if (rawToken.isBlank()) return
        qrPollJob?.cancel()
        _uiState.update {
            it.copy(
                isLoadingQr = true,
                authError = null,
                qrStatusMessage = str(R.string.qr_status_verifying_token)
            )
        }
        viewModelScope.launch {
            authManager.loginWithManualToken(rawToken).onSuccess {
                // Verify token works by fetching current user or profiles
                val userRes = apiClient.fetchCurrentUser()
                if (userRes.isSuccess) {
                    val user = userRes.getOrNull()
                    val userUuid = user?.profile?.uuid
                    if (!userUuid.isNullOrBlank()) {
                        authManager.saveMyProfileUuid(userUuid)
                    }
                    _uiState.update { state ->
                        state.copy(
                            isLoadingQr = false,
                            isLoggedIn = true,
                            currentUser = user,
                            myProfileUuid = userUuid ?: state.myProfileUuid,
                            currentPlace = user?.profile?.location?.place ?: state.currentPlace,
                            isAdMode = authManager.isAdModeEnabled(),
                            showNsfw = authManager.isNsfwEnabled(),
                            needsAdNsfwOnboarding = !authManager.hasAskedAdNsfwOnboarding(),
                            authError = null
                        )
                    }
                    refreshProfiles()
                } else {
                    authManager.logout()
                    _uiState.update { state ->
                        state.copy(
                            isLoadingQr = false,
                            isLoggedIn = false,
                            authError = str(
                                R.string.auth_err_token_invalid,
                                userRes.exceptionOrNull()?.message.orEmpty()
                            )
                        )
                    }
                    startQrLoginSession()
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isLoadingQr = false,
                        authError = err.message ?: str(R.string.auth_err_token_format)
                    )
                }
            }
        }
    }

    fun logout() {
        qrPollJob?.cancel()
        searchJob?.cancel()
        authManager.logout()
        _uiState.value = BarqUiState(
            appLanguage = AppLanguage.fromCode(authManager.getAppLanguageCode()),
            isLoggedIn = false,
            currentPlace = authManager.getSavedPlace()
        )
        startQrLoginSession()
    }

    fun refreshProfiles() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                isLoadingProfiles = true,
                isLoadingMore = false,
                profilesError = null,
                paginationOffset = 0,
                hasReachedEnd = false
            )
        }
        searchJob = viewModelScope.launch {
            val state = _uiState.value
            val place = state.currentPlace ?: state.currentUser?.profile?.location?.place
            val limit = 60
            apiClient.searchProfiles(
                searchFilters = state.filters,
                isAd = state.isAdMode,
                latitude = place?.latitude,
                longitude = place?.longitude,
                cursor = "",
                limit = limit
            ).onSuccess { list ->
                val sorted = list.sortedBy { it.distanceKmForSort }
                authManager.saveCachedNearbyProfiles(sorted)
                BarqTileRenderer.notifyTilesUpdated(getApplication())
                _uiState.update {
                    it.copy(
                        isLoadingProfiles = false,
                        profiles = sorted,
                        paginationOffset = list.size,
                        hasReachedEnd = list.size < limit,
                        profilesError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingProfiles = false,
                        profilesError = err.message ?: str(R.string.nearby_error_fetch_profiles)
                    )
                }
            }
        }
    }

    fun loadMoreProfiles() {
        val state = _uiState.value
        if (state.isLoadingProfiles || state.isLoadingMore || state.hasReachedEnd) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val place = state.currentPlace ?: state.currentUser?.profile?.location?.place
            val limit = 30
            apiClient.searchProfiles(
                searchFilters = state.filters,
                isAd = state.isAdMode,
                latitude = place?.latitude,
                longitude = place?.longitude,
                cursor = state.paginationOffset.toString(),
                limit = limit
            ).onSuccess { more ->
                _uiState.update { prev ->
                    val existingUuids = prev.profiles.map { p -> p.uuid }.toSet()
                    val merged = (prev.profiles + more.filterNot { p -> p.uuid in existingUuids })
                        .sortedBy { it.distanceKmForSort }
                    prev.copy(
                        isLoadingMore = false,
                        profiles = merged,
                        paginationOffset = prev.paginationOffset + limit,
                        hasReachedEnd = more.size < limit
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun updateFilters(
        newFilters: BarqSearchFilters,
        isAd: Boolean = _uiState.value.isAdMode
    ) {
        val prevAd = _uiState.value.isAdMode
        authManager.setAdModeEnabled(isAd)
        _uiState.update {
            it.copy(
                filters = newFilters,
                isAdMode = isAd
            )
        }
        if (prevAd != isAd) {
            viewModelScope.launch {
                apiClient.setAdModeUnlocked(isAd)
            }
        }
        refreshProfiles()
    }

    fun completeAdNsfwOnboarding(enableAdAndNsfw: Boolean) {
        authManager.setAskedAdNsfwOnboarding(true)
        authManager.setAdModeEnabled(enableAdAndNsfw)
        authManager.setNsfwEnabled(enableAdAndNsfw)
        _uiState.update {
            it.copy(
                needsAdNsfwOnboarding = false,
                isAdMode = enableAdAndNsfw,
                showNsfw = enableAdAndNsfw
            )
        }
        viewModelScope.launch {
            if (enableAdAndNsfw) {
                apiClient.setAdModeUnlocked(true)
            }
            refreshProfiles()
        }
    }

    fun setAdMode(enabled: Boolean) {
        authManager.setAdModeEnabled(enabled)
        _uiState.update { it.copy(isAdMode = enabled) }
        viewModelScope.launch {
            apiClient.setAdModeUnlocked(enabled)
        }
        refreshProfiles()
    }

    fun setShowNsfw(enabled: Boolean) {
        authManager.setNsfwEnabled(enabled)
        _uiState.update { it.copy(showNsfw = enabled) }
    }

    fun clearSearchFilters() {
        _uiState.update {
            it.copy(filters = BarqSearchFilters())
        }
        refreshProfiles()
    }

    fun updateSearchFilters(
        query: String = _uiState.value.searchQuery,
        requireImage: Boolean = _uiState.value.requireProfileImage,
        isAd: Boolean = _uiState.value.isAdMode
    ) {
        _uiState.update {
            it.copy(
                filters = it.filters.copy(
                    displayName = query,
                    requireProfileImage = requireImage
                ),
                isAdMode = isAd
            )
        }
        refreshProfiles()
    }

    @SuppressLint("MissingPermission")
    fun updateGpsLocationFromWatch(context: Context, onGpsMissing: () -> Unit = {}) {
        if (_uiState.value.isUpdatingGps) return
        _uiState.update {
            it.copy(
                isUpdatingGps = true,
                gpsUnavailable = false,
                gpsStatusMessage = str(R.string.gps_fetching)
            )
        }
        viewModelScope.launch {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = lm.getProviders(true)
                var bestLocation: Location? = null
                for (provider in providers) {
                    val loc = lm.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                        bestLocation = loc
                    }
                }
                if (bestLocation == null) {
                    _uiState.update {
                        it.copy(
                            isUpdatingGps = false,
                            gpsUnavailable = true,
                            gpsStatusMessage = str(R.string.gps_missing_manual)
                        )
                    }
                    onGpsMissing()
                    return@launch
                }

                apiClient.updateGpsLocation(bestLocation.latitude, bestLocation.longitude)
                    .onSuccess { place ->
                        val resolvedPlace = place ?: Place(
                            latitude = bestLocation.latitude,
                            longitude = bestLocation.longitude
                        )
                        authManager.saveSelectedPlace(resolvedPlace)
                        val placeText = place?.formattedShort(str(R.string.location_unknown))
                            ?: "${bestLocation.latitude}, ${bestLocation.longitude}"
                        _uiState.update {
                            it.copy(
                                isUpdatingGps = false,
                                gpsUnavailable = false,
                                currentPlace = resolvedPlace,
                                gpsStatusMessage = str(R.string.gps_status_fmt, placeText)
                            )
                        }
                        refreshProfiles()
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isUpdatingGps = false,
                                gpsUnavailable = true,
                                gpsStatusMessage = str(R.string.gps_api_error_fmt, err.message.orEmpty())
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUpdatingGps = false,
                        gpsUnavailable = true,
                        gpsStatusMessage = str(R.string.gps_error_fmt, e.message.orEmpty())
                    )
                }
                onGpsMissing()
            }
        }
    }

    fun searchPlaces(query: String) {
        val trimmed = query.trim()
        _uiState.update {
            it.copy(
                placeSearchQuery = trimmed,
                isSearchingPlaces = trimmed.isNotBlank(),
                placeSearchError = null
            )
        }
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(placeSearchResults = emptyList(), isSearchingPlaces = false) }
            return
        }
        viewModelScope.launch {
            apiClient.searchPlaces(trimmed).onSuccess { list ->
                _uiState.update {
                    it.copy(
                        isSearchingPlaces = false,
                        placeSearchResults = list,
                        placeSearchError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSearchingPlaces = false,
                        placeSearchError = err.message ?: str(R.string.location_search_error)
                    )
                }
            }
        }
    }

    fun selectManualPlace(place: Place) {
        authManager.saveSelectedPlace(place)
        val placeLabel = place.formattedShort(str(R.string.location_unknown))
        _uiState.update {
            it.copy(
                currentPlace = place,
                gpsUnavailable = false,
                gpsStatusMessage = str(R.string.city_status_fmt, placeLabel)
            )
        }
        refreshProfiles()
        val lat = place.latitude
        val lon = place.longitude
        if (lat != null && lon != null) {
            viewModelScope.launch {
                apiClient.updateManualLocation(lat, lon)
            }
        }
    }

    fun refreshCurrentUser() {
        if (_uiState.value.isLoadingCurrentUser) return
        _uiState.update { it.copy(isLoadingCurrentUser = true) }
        viewModelScope.launch {
            apiClient.fetchCurrentUser().onSuccess { user ->
                val userPlace = user.profile?.location?.place
                val userUuid = user.profile?.uuid
                if (!userUuid.isNullOrBlank()) {
                    authManager.saveMyProfileUuid(userUuid)
                }
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        myProfileUuid = userUuid ?: it.myProfileUuid,
                        isLoadingCurrentUser = false,
                        currentPlace = it.currentPlace ?: userPlace
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingCurrentUser = false) }
            }
        }
    }

    fun openMyOwnProfile(onNavigate: (String) -> Unit) {
        val knownUuid = _uiState.value.currentUser?.profile?.uuid
            ?.takeIf { it.isNotBlank() }
            ?: _uiState.value.myProfileUuid?.takeIf { it.isNotBlank() }
            ?: authManager.getMyProfileUuid()

        if (!knownUuid.isNullOrBlank()) {
            loadProfileDetail(knownUuid)
            onNavigate(knownUuid)
            return
        }

        _uiState.update { it.copy(isLoadingCurrentUser = true) }
        viewModelScope.launch {
            apiClient.fetchCurrentUser().onSuccess { user ->
                val myUuid = user.profile?.uuid
                val userPlace = user.profile?.location?.place
                if (!myUuid.isNullOrBlank()) {
                    authManager.saveMyProfileUuid(myUuid)
                }
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        myProfileUuid = myUuid ?: it.myProfileUuid,
                        isLoadingCurrentUser = false,
                        currentPlace = it.currentPlace ?: userPlace
                    )
                }
                if (!myUuid.isNullOrBlank()) {
                    loadProfileDetail(myUuid)
                    onNavigate(myUuid)
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingCurrentUser = false) }
            }
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun refreshImageCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                val diskCacheSize = runCatching { Coil.imageLoader(app).diskCache?.size ?: 0L }.getOrDefault(0L)
                val cacheDir = File(app.cacheDir, "barq_image_cache")
                val dirSize = if (cacheDir.exists()) {
                    cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                } else 0L
                maxOf(diskCacheSize, dirSize)
            }
            _uiState.update { it.copy(imageCacheSizeBytes = size) }
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearImageCache() {
        if (_uiState.value.isClearingCache) return
        _uiState.update { it.copy(isClearingCache = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                runCatching {
                    val loader = Coil.imageLoader(app)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                }
                runCatching {
                    val cacheDir = File(app.cacheDir, "barq_image_cache")
                    if (cacheDir.exists()) {
                        cacheDir.deleteRecursively()
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isClearingCache = false,
                    imageCacheSizeBytes = 0L
                )
            }
        }
    }

    fun loadProfileDetail(uuid: String) {
        _uiState.update {
            it.copy(
                selectedProfile = null,
                isLoadingProfileDetail = true,
                profileDetailError = null
            )
        }
        viewModelScope.launch {
            apiClient.fetchProfileDetail(uuid).onSuccess { detail ->
                _uiState.update {
                    it.copy(
                        selectedProfile = detail,
                        isLoadingProfileDetail = false,
                        profileDetailError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingProfileDetail = false,
                        profileDetailError = err.message ?: str(R.string.profile_error_fetch)
                    )
                }
            }
        }
    }

    fun toggleLikeCurrentProfile() {
        val detail = _uiState.value.selectedProfile ?: return
        if (_uiState.value.isLikingProfile) return
        _uiState.update { it.copy(isLikingProfile = true) }
        viewModelScope.launch {
            val currentlyLiked = detail.isLikedOrMutual
            apiClient.toggleLikeProfile(detail.uuid, currentlyLiked).onSuccess { newRelation ->
                _uiState.update { state ->
                    val updatedDetail = state.selectedProfile?.copy(relationType = newRelation)
                    val updatedList = state.profiles.map { item ->
                        if (item.uuid == detail.uuid) item.copy(relationType = newRelation) else item
                    }
                    state.copy(
                        isLikingProfile = false,
                        selectedProfile = updatedDetail,
                        profiles = updatedList
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLikingProfile = false) }
            }
        }
    }

    fun toggleLikeProfileImage(imageId: String, currentlyLiked: Boolean) {
        viewModelScope.launch {
            val targetLiked = !currentlyLiked
            apiClient.toggleLikeImage(imageId, targetLiked).onSuccess { newCount ->
                _uiState.update { state ->
                    val currentDetail = state.selectedProfile ?: return@update state
                    val updatedImages = currentDetail.images?.map { img ->
                        if (img.id == imageId) {
                            img.copy(hasLiked = targetLiked, likeCount = newCount)
                        } else img
                    }
                    state.copy(selectedProfile = currentDetail.copy(images = updatedImages))
                }
            }
        }
    }

    fun selectSona(sona: Sona) {
        _uiState.update { it.copy(selectedSona = sona) }
    }
}

