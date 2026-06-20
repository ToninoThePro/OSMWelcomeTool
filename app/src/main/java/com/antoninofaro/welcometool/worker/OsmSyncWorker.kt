package com.antoninofaro.welcometool.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * A background worker executed periodically via WorkManager.
 * Responsible for polling the OSM API for new changesets in the specified bounding box.
 * If new changesets are detected with IDs higher than the last known ID, it can trigger localized notifications.
 */
@HiltWorker
class OsmSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val osmRepository: OsmRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    /**
     * Performs the actual synchronization work in the background.
     * It reads current settings for bounding box and the last known changeset, fetches recent changesets,
     * filters for newly created ones, and dispatches a system notification if notifications are enabled.
     *
     * @return [Result.success] if the API poll completes smoothly, [Result.retry] on transient failures, or [Result.failure] on fatal errors.
     */
    override suspend fun doWork(): Result {
        Timber.d("OsmSyncWorker started")

        return try {
            val settings = settingsRepository.settingsFlow.first()
            val bbox = settings.defaultBBox
            val lastKnownId = settings.lastKnownChangesetId
            val showNotifications = settings.showNotifications

            // ponytail: only fetch 1 changeset to check if there are newer ones
            val result = osmRepository.fetchRecentChangesets(bbox = bbox, limit = 1)

            val changesets = result.getOrNull()
            if (changesets != null) {
                if (changesets.isNotEmpty()) {
                    val maxId = changesets.first().id

                    if (maxId > lastKnownId) {
                        Timber.d("New changesets found. Max ID: $maxId, Last Known: $lastKnownId")
                        if (lastKnownId == 0L) {
                            Timber.d("First run, saving maxId: $maxId without notification")
                            settingsRepository.updateLastKnownChangesetId(maxId)
                        } else {
                            val newCount = changesets.count { it.id > lastKnownId }

                            if (showNotifications && newCount > 0) {
                                notificationHelper.createNotificationChannel()
                                notificationHelper.sendNewChangesetsNotification(newCount)
                            }

                            settingsRepository.updateLastKnownChangesetId(maxId)
                        }
                    } else {
                        Timber.d("No new changesets since last check.")
                    }
                }
                Result.success()
            } else {
                Timber.e("Failed to fetch changesets: ${result.exceptionOrNull()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during OsmSyncWorker execution")
            Result.failure()
        }
    }
}
