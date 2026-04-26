package com.antoninofaro.welcometool.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.domain.UserAnalyzer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import com.antoninofaro.welcometool.data.model.Result as ApiResult

@HiltWorker
class NewUserWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: OsmRepository,
    private val settingsRepository: SettingsRepository,
    private val notifiedUserStorage: NotifiedUserStorage
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        try {
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.showNotifications) {
                Timber.d("Notifications disabled in settings")
                return androidx.work.ListenableWorker.Result.success()
            }

            val changesetsResult = repository.fetchRecentChangesets(settings.defaultBBox)

            val changesets = when (changesetsResult) {
                is ApiResult.Success -> changesetsResult.data
                is ApiResult.Error -> {
                    Timber.e("Failed to fetch changesets")
                    return androidx.work.ListenableWorker.Result.retry()
                }

                is ApiResult.Loading -> {
                    return androidx.work.ListenableWorker.Result.retry()
                }
            }

            val uniqueUids = changesets.map { it.uid }.distinct()
            val notifiedIds = notifiedUserStorage.getAllNotifiedIds()
            val newlyNotified = LinkedHashSet<Long>()

            for (uid in uniqueUids) {
                if (notifiedIds.contains(uid.toString())) {
                    continue
                }

                val user = when (val userResult = repository.fetchUserDetail(uid)) {
                    is ApiResult.Success -> userResult.data
                    is ApiResult.Error -> continue
                    is ApiResult.Loading -> continue
                }

                val userHistory = when (val historyResult = repository.fetchUserChangesets(uid)) {
                    is ApiResult.Success -> historyResult.data
                    is ApiResult.Error -> emptyList()
                    is ApiResult.Loading -> emptyList()
                }

                val recentCs = changesets.find { it.uid == uid }
                val analysis = UserAnalyzer.analyze(user, userHistory, recentCs)

                if (analysis.isNewcomer) {
                    sendNotification(user.displayName, settings.defaultAreaName)
                    newlyNotified.add(uid)
                    Timber.d("Notification sent for new mapper")
                }
            }

            notifiedUserStorage.markAsNotifiedBatch(newlyNotified)

            return androidx.work.ListenableWorker.Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Error in NewUserWorker")
            return androidx.work.ListenableWorker.Result.retry()
        }
    }

    private fun sendNotification(username: String, areaName: String) {
        val builder = NotificationCompat.Builder(context, "NEW_MAPPER_CHANNEL")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Nuovo mappatore rilevato!")
            .setContentText("$username ha appena iniziato a mappare in $areaName.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(username.hashCode(), builder.build())
        }
    }
}
