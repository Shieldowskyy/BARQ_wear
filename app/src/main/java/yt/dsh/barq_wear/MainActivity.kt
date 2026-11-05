package yt.dsh.barq_wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import yt.dsh.barq_wear.assistant.BarqNavDestination
import yt.dsh.barq_wear.assistant.BarqShortcutManager
import yt.dsh.barq_wear.data.BarqAuthManager
import yt.dsh.barq_wear.ui.ActivitiesScreen
import yt.dsh.barq_wear.ui.AdNsfwOnboardingScreen
import yt.dsh.barq_wear.ui.BarqViewModel
import yt.dsh.barq_wear.ui.BarqWearTheme
import yt.dsh.barq_wear.ui.LoginScreen
import yt.dsh.barq_wear.ui.ManualLocationScreen
import yt.dsh.barq_wear.ui.MyAccountScreen
import yt.dsh.barq_wear.ui.NearbyScreen
import yt.dsh.barq_wear.ui.NewsDetailScreen
import yt.dsh.barq_wear.ui.NewsListScreen
import yt.dsh.barq_wear.ui.ProfileDetailScreen
import yt.dsh.barq_wear.ui.ProvideAppLocale
import yt.dsh.barq_wear.ui.RelationProfilesScreen
import yt.dsh.barq_wear.ui.SearchFilterScreen
import yt.dsh.barq_wear.ui.SettingsScreen
import yt.dsh.barq_wear.ui.SonaDetailScreen
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: BarqViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while scanning QR code or browsing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Configure global Coil ImageLoader with Disk + Memory Cache and browser User-Agent for Cloudflare
        val coilOkHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", BarqAuthManager.USER_AGENT)
                    .header("Referer", "https://web.barq.app/")
                    .build()
                chain.proceed(req)
            }
            .build()

        val imageCacheDir = File(applicationContext.cacheDir, "barq_image_cache")
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .okHttpClient(coilOkHttp)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(imageCacheDir)
                        .maxSizeBytes(100L * 1024 * 1024) // 100 MB disk cache
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(true)
                .build()
        )

        // Publish dynamic shortcuts for Gemini and Google Assistant
        BarqShortcutManager.publishShortcuts(this)

        handleDeepLinkIntent(intent)

        // Schedule periodic WorkManager background sync for Nearby furs, Tiles, and Complications
        yt.dsh.barq_wear.sync.BarqSyncScheduler.schedulePeriodicSync(applicationContext)

        setContent {
            BarqWearTheme {
                BarqWearApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && (data.scheme == "barq" || data.host == "barq.app")) {
            val accessToken = data.getQueryParameter("access_token")
                ?: data.getQueryParameter("token")
            val refreshToken = data.getQueryParameter("refresh_token")
            if (!accessToken.isNullOrBlank()) {
                viewModel.authManager.saveTokens(accessToken, refreshToken)
                viewModel.loadInitialData()
                return
            } else if (!refreshToken.isNullOrBlank()) {
                viewModel.loginWithManualToken(refreshToken)
                return
            }
        }

        // Assistant / Gemini Voice Command or App Deeplink destination
        val destinationRequest = BarqShortcutManager.parseDestination(intent)
        if (destinationRequest != null) {
            viewModel.requestDestination(destinationRequest)
            return
        }

        val tileUuid = intent?.getStringExtra("tile_profile_uuid")
        if (!tileUuid.isNullOrBlank()) {
            viewModel.requestOpenProfileFromTile(tileUuid)
        }
    }
}

@Composable
fun BarqWearApp(viewModel: BarqViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    ProvideAppLocale(language = uiState.appLanguage) {
        val context = LocalContext.current

        if (!uiState.isLoggedIn) {
            LoginScreen(
                uiState = uiState,
                onRefreshQr = { viewModel.startQrLoginSession() },
                onSubmitManualToken = { token -> viewModel.loginWithManualToken(token) }
            )
            return@ProvideAppLocale
        }

        if (uiState.needsAdNsfwOnboarding) {
            AdNsfwOnboardingScreen(
                onComplete = { enableAdAndNsfw ->
                    viewModel.completeAdNsfwOnboarding(enableAdAndNsfw)
                }
            )
            return@ProvideAppLocale
        }

        val navController = rememberSwipeDismissableNavController()

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* permission handled */ }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        // Handle navigation requested via Gemini / Google Assistant voice commands, Deeplinks or Tiles
        LaunchedEffect(uiState.pendingDestination) {
            val request = uiState.pendingDestination ?: return@LaunchedEffect
            viewModel.consumePendingDestination()

            when (request.destination) {
                BarqNavDestination.NEARBY -> {
                    navController.popBackStack("nearby", inclusive = false)
                }
                BarqNavDestination.PROFILE -> {
                    val targetUuid = request.profileUuid
                    if (!targetUuid.isNullOrBlank()) {
                        viewModel.loadProfileDetail(targetUuid)
                        navController.navigate("profile/$targetUuid")
                    } else {
                        viewModel.openMyOwnProfile { myUuid ->
                            navController.navigate("profile/$myUuid")
                        }
                    }
                }
                BarqNavDestination.ACCOUNT -> {
                    navController.navigate("account")
                }
                BarqNavDestination.ACTIVITIES -> {
                    viewModel.loadActivities()
                    navController.navigate("activities")
                }
                BarqNavDestination.NEWS -> {
                    viewModel.loadNewsItems()
                    navController.navigate("news")
                }
                BarqNavDestination.RELATIONS -> {
                    viewModel.loadRelationProfiles(uiState.relationListType)
                    navController.navigate("relations")
                }
                BarqNavDestination.FILTERS -> {
                    navController.navigate("filters")
                }
                BarqNavDestination.LOCATION -> {
                    navController.navigate("location")
                }
                BarqNavDestination.SETTINGS -> {
                    viewModel.refreshImageCacheSize()
                    navController.navigate("settings")
                }
            }
        }

        LaunchedEffect(uiState.pendingTileProfileUuid) {
            val targetUuid = uiState.pendingTileProfileUuid
            if (!targetUuid.isNullOrBlank()) {
                viewModel.consumePendingTileProfileUuid()
                navController.navigate("profile/$targetUuid")
            }
        }

        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "nearby"
        ) {
            composable("nearby") {
                NearbyScreen(
                    uiState = uiState,
                    onProfileClick = { uuid ->
                        viewModel.loadProfileDetail(uuid)
                        navController.navigate("profile/$uuid")
                    },
                    onOpenFilters = {
                        navController.navigate("filters")
                    },
                    onOpenManualLocation = {
                        navController.navigate("location")
                    },
                    onOpenMyAccount = {
                        navController.navigate("account")
                    },
                    onRefresh = {
                        viewModel.refreshProfiles()
                    },
                    onUpdateGps = {
                        viewModel.updateGpsLocationFromWatch(
                            context = context,
                            onGpsMissing = {
                                navController.navigate("location")
                            }
                        )
                    },
                    onLoadMore = {
                        viewModel.loadMoreProfiles()
                    },
                    onClearSearch = {
                        viewModel.clearSearchFilters()
                    },
                    onOpenActivities = {
                        navController.navigate("activities")
                    }
                )
            }

            composable("filters") {
                SearchFilterScreen(
                    uiState = uiState,
                    onUpdateFilters = { newFilters, isAd ->
                        viewModel.updateFilters(newFilters, isAd)
                    },
                    onClearFilters = {
                        viewModel.clearSearchFilters()
                    },
                    onOpenManualLocation = {
                        navController.navigate("location")
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable("location") {
                ManualLocationScreen(
                    uiState = uiState,
                    onSearchPlaces = { query ->
                        viewModel.searchPlaces(query)
                    },
                    onSelectPlace = { place ->
                        viewModel.selectManualPlace(place)
                    },
                    onRetryGps = {
                        viewModel.updateGpsLocationFromWatch(context)
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable("account") {
                LaunchedEffect(Unit) {
                    viewModel.refreshImageCacheSize()
                }
                MyAccountScreen(
                    uiState = uiState,
                    onEnsureUserLoaded = {
                        viewModel.refreshCurrentUser()
                    },
                    onOpenMyFullProfile = {
                        viewModel.openMyOwnProfile { myUuid ->
                            navController.navigate("profile/$myUuid")
                        }
                    },
                    onOpenRelationList = { type ->
                        viewModel.loadRelationProfiles(type)
                        navController.navigate("relations")
                    },
                    onOpenActivities = {
                        navController.navigate("activities")
                    },
                    onOpenNews = {
                        viewModel.loadNewsItems()
                        navController.navigate("news")
                    },
                    onOpenSettings = {
                        viewModel.refreshImageCacheSize()
                        navController.navigate("settings")
                    },
                    onLogout = {
                        viewModel.logout()
                    }
                )
            }

            composable("relations") {
                RelationProfilesScreen(
                    uiState = uiState,
                    onSelectType = { type ->
                        viewModel.loadRelationProfiles(type)
                    },
                    onProfileClick = { uuid ->
                        viewModel.loadProfileDetail(uuid)
                        navController.navigate("profile/$uuid")
                    },
                    onRefresh = {
                        viewModel.loadRelationProfiles(uiState.relationListType)
                    }
                )
            }

            composable("news") {
                NewsListScreen(
                    uiState = uiState,
                    onNewsClick = { newsItem ->
                        viewModel.openNewsItem(newsItem)
                        navController.navigate("news_detail")
                    },
                    onRefresh = {
                        viewModel.loadNewsItems()
                    }
                )
            }

            composable("news_detail") {
                NewsDetailScreen(uiState = uiState)
            }

            composable("activities") {
                ActivitiesScreen(
                    uiState = uiState,
                    onLoadActivities = {
                        viewModel.loadActivities()
                    },
                    onMarkActivitiesRead = {
                        viewModel.markActivitiesAsRead()
                    },
                    onProfileClick = { uuid ->
                        viewModel.loadProfileDetail(uuid)
                        navController.navigate("profile/$uuid")
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    uiState = uiState,
                    onToggleAdMode = { enabled ->
                        viewModel.setAdMode(enabled)
                    },
                    onToggleNsfw = { enabled ->
                        viewModel.setShowNsfw(enabled)
                    },
                    onSelectLanguage = { lang ->
                        viewModel.setAppLanguage(lang)
                    },
                    onSetGpsAutoSyncHours = { hours ->
                        viewModel.setGpsAutoSyncHours(hours, context)
                    },
                    onRefreshCacheSize = {
                        viewModel.refreshImageCacheSize()
                    },
                    onClearCache = {
                        viewModel.clearImageCache()
                    },
                    onOpenManualLocation = {
                        navController.navigate("location")
                    }
                )
            }

            composable("profile/{uuid}") { backStackEntry ->
                val uuid = backStackEntry.arguments?.getString("uuid").orEmpty()
                LaunchedEffect(uuid) {
                    if (uuid.isNotBlank() && uiState.selectedProfile?.uuid != uuid && !uiState.isLoadingProfileDetail) {
                        viewModel.loadProfileDetail(uuid)
                    }
                }
                ProfileDetailScreen(
                    uiState = uiState,
                    onRetry = {
                        if (uuid.isNotBlank()) viewModel.loadProfileDetail(uuid)
                    },
                    onToggleLikeProfile = {
                        viewModel.toggleLikeCurrentProfile()
                    },
                    onToggleLikeImage = { imageId, currentlyLiked ->
                        viewModel.toggleLikeProfileImage(imageId, currentlyLiked)
                    },
                    onSonaClick = { sona ->
                        viewModel.selectSona(sona)
                        navController.navigate("sona")
                    },
                    onBoopProfile = { targetUuid, targetName ->
                        viewModel.boopProfile(targetUuid, targetName)
                    }
                )
            }

            composable("sona") {
                SonaDetailScreen(
                    sona = uiState.selectedSona,
                    showNsfw = uiState.showNsfw || uiState.isAdMode,
                    profileImages = uiState.selectedProfile?.images
                )
            }
        }
    }
}

