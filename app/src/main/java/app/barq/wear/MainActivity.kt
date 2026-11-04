package app.barq.wear

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.barq.wear.data.BarqAuthManager
import app.barq.wear.ui.BarqViewModel
import app.barq.wear.ui.BarqWearTheme
import app.barq.wear.ui.LoginScreen
import app.barq.wear.ui.ManualLocationScreen
import app.barq.wear.ui.MyAccountScreen
import app.barq.wear.ui.NearbyScreen
import app.barq.wear.ui.ProfileDetailScreen
import app.barq.wear.ui.ProvideAppLocale
import app.barq.wear.ui.SearchFilterScreen
import app.barq.wear.ui.SettingsScreen
import app.barq.wear.ui.SonaDetailScreen
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

        handleDeepLinkIntent(intent)

        setContent {
            BarqWearTheme {
                BarqWearApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "barq") {
            val accessToken = data.getQueryParameter("access_token")
                ?: data.getQueryParameter("token")
            val refreshToken = data.getQueryParameter("refresh_token")
            if (!accessToken.isNullOrBlank()) {
                viewModel.authManager.saveTokens(accessToken, refreshToken)
                viewModel.loadInitialData()
            } else if (!refreshToken.isNullOrBlank()) {
                viewModel.loginWithManualToken(refreshToken)
            }
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

        val navController = rememberSwipeDismissableNavController()

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
                    onOpenSettings = {
                        viewModel.refreshImageCacheSize()
                        navController.navigate("settings")
                    },
                    onLogout = {
                        viewModel.logout()
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    uiState = uiState,
                    onSelectLanguage = { lang ->
                        viewModel.setAppLanguage(lang)
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
                    }
                )
            }

            composable("sona") {
                SonaDetailScreen(sona = uiState.selectedSona)
            }
        }
    }
}

