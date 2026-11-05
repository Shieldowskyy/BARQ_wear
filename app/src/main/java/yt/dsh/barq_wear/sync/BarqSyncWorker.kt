package yt.dsh.barq_wear.sync

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import yt.dsh.barq_wear.complication.BarqComplicationService
import yt.dsh.barq_wear.data.BarqApiClient
import yt.dsh.barq_wear.data.BarqAuthManager
import yt.dsh.barq_wear.data.BarqSearchFilters
import yt.dsh.barq_wear.tile.BarqCompactTileService
import yt.dsh.barq_wear.tile.BarqFullTileService

/**
 * Background worker that periodically syncs nearby furs, updates local cache,
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
        val savedPlace = authManager.getSavedPlace()
        val isAd = authManager.isAdModeEnabled()

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

