package com.antoninofaro.welcometool.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.antoninofaro.welcometool.worker.OsmSyncWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

object WorkerUtils {
    private const val WORK_NAME = "OsmSyncWork"

    fun scheduleOsmSyncWorker(context: Context, intervalMinutes: Int, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)

        if (!enabled) {
            Timber.d("Worker disabled, cancelling existing work.")
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        // WorkManager minimum periodic interval is 15 minutes.
        val safeInterval = intervalMinutes.coerceAtLeast(15).toLong()

        Timber.d("Scheduling worker with interval: $safeInterval minutes (requested: $intervalMinutes)")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<OsmSyncWorker>(safeInterval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
