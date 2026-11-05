package yt.dsh.barq_wear.sync

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import yt.dsh.barq_wear.complication.BarqComplicationService
import yt.dsh.barq_wear.data.BarqApiClient
import yt.dsh.barq_wear.data.BarqAuthManager
import yt.dsh.barq_wear.data.BarqSearchFilters
import yt.dsh.barq_wear.data.Place
import yt.dsh.barq_wear.tile.BarqCompactTileService
import yt.dsh.barq_wear.tile.BarqFullTileService

/**
 * Background worker that periodically syncs nearby furs, updates local cache,
 * checks and updates watch city/location (if enabled),
 * and refreshes Wear OS Tiles and Complications.
 */
class BarqSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val authManager = BarqAuthManager(applicationContext)
        if (!authManager.isLoggedIn()) {
            return Result.success()
        }

        if (authManager.getValidAccessToken() == null) {
            return Result.retry()
        }
        val apiClient = BarqApiClient(authManager)

        // Ultra energy-efficient background GPS location update (if enabled by user in settings)
        val gpsAutoSyncHours = authManager.getGpsAutoSyncHours()
        if (gpsAutoSyncHours > 0) {
            val lastGpsSync = authManager.getLastGpsSyncTimestamp()
            val now = System.currentTimeMillis()
            val intervalMillis = gpsAutoSyncHours * 60 * 60 * 1000L
            if (now - lastGpsSync >= intervalMillis) {
                runCatching {
                    val hasFine = ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasFine || hasCoarse) {
                        val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                        if (lm != null) {
                            val providers = lm.getProviders(true)
                            var bestLocation: Location? = null
                            for (provider in providers) {
                                val loc = lm.getLastKnownLocation(provider) ?: continue
                                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                                    bestLocation = loc
                                }
                            }
                            if (bestLocation != null) {
                                val updatedPlace = apiClient.updateGpsLocation(bestLocation.latitude, bestLocation.longitude).getOrNull()
                                val resolvedPlace = updatedPlace ?: Place(
                                    latitude = bestLocation.latitude,
                                    longitude = bestLocation.longitude
                                )
                                authManager.saveSelectedPlace(resolvedPlace)
                                authManager.setLastGpsSyncTimestamp(now)
                            }
                        }
                    }
                }
            }
        }

        val savedPlace = authManager.getSavedPlace()
        val isAd = authManager.isAdModeEnabled()

        // Sync background notifications for Boops, Likes & Matches (energy-efficient, piggybacked on periodic sync)
        runCatching {
            val activitiesResult = apiClient.fetchActivities(limit = 10)
            activitiesResult.getOrNull()?.let { activities ->
                val newest = activities.firstOrNull()
                if (newest != null && newest.readAt == null) {
                    val lastNotified = authManager.getLastNotifiedActivityId()
                    if (lastNotified != newest.id) {
                        BarqNotificationHelper.showActivityNotification(applicationContext, newest)
                        authManager.setLastNotifiedActivityId(newest.id)
                    }
                }
            }
        }

        val result = apiClient.searchProfiles(
            searchFilters = BarqSearchFilters(),
            isAd = isAd,
            latitude = savedPlace?.latitude,
            longitude = savedPlace?.longitude,
            limit = 20
        )

        return result.fold(
            onSuccess = { profiles ->
                if (profiles.isNotEmpty()) {
                    authManager.saveCachedNearbyProfiles(profiles)

                    // Refresh Wear OS Tiles
                    runCatching {
                        TileService.getUpdater(applicationContext)
                            .requestUpdate(BarqFullTileService::class.java)
                        TileService.getUpdater(applicationContext)
                            .requestUpdate(BarqCompactTileService::class.java)
                    }

                    // Refresh Wear OS Complications
                    runCatching {
                        ComplicationDataSourceUpdateRequester.create(
                            applicationContext,
                            ComponentName(applicationContext, BarqComplicationService::class.java)
                        ).requestUpdateAll()
                    }
                }
                Result.success()
            },
            onFailure = {
                Result.retry()
            }
        )
    }
}

