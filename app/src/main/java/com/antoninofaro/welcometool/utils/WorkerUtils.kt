package com.antoninofaro.welcometool.utils

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.antoninofaro.welcometool.worker.OsmSyncWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

object WorkerUtils {
    private const val OSM_SYNC_WORK_NAME = "OsmSyncWork"
    const val MIN_WORKER_INTERVAL = 15
    const val WORKER_BACKOFF_SECONDS = 30L

    fun scheduleOsmSyncWorker(context: Context, intervalMinutes: Int, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)

        if (!enabled) {
            Timber.d("Workers disabled, cancelling existing work.")
            workManager.cancelUniqueWork(OSM_SYNC_WORK_NAME)
            return
        }

        val safeInterval = intervalMinutes.coerceAtLeast(MIN_WORKER_INTERVAL).toLong()

        Timber.d("Scheduling worker with interval: $safeInterval minutes (requested: $intervalMinutes)")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<OsmSyncWorker>(safeInterval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WORKER_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            OSM_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
