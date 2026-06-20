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
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.domain.UserAnalyzer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
            val changesetsByUid = changesets.reversed().associateBy { it.uid } // ponytail: reversed so associateBy keeps the newest per uid
            val semaphore = Semaphore(6)

            coroutineScope {
                val results = uniqueUids
                    .filterNot { notifiedIds.contains(it.toString()) }
                    .map { uid ->
                        async {
                            semaphore.withPermit {
                                processUser(uid, changesetsByUid, settings)
                            }
                        }
                    }
                results.awaitAll().filterNotNull().forEach { newlyNotified.add(it) }
            }

            notifiedUserStorage.markAsNotifiedBatch(newlyNotified)

            return androidx.work.ListenableWorker.Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Error in NewUserWorker")
            return androidx.work.ListenableWorker.Result.retry()
        }
    }

    private suspend fun processUser(
        uid: Long,
        changesetsByUid: Map<Long, OsmChangeset>,
        settings: AppSettings
    ): Long? = coroutineScope {
        val userDeferred = async { repository.fetchUserDetail(uid) }
        val historyDeferred = async { repository.fetchUserChangesets(uid) }

        val user = when (val r = userDeferred.await()) {
            is ApiResult.Success -> r.data
            else -> return@coroutineScope null
        }
        val userHistory = when (val r = historyDeferred.await()) {
            is ApiResult.Success -> r.data
            else -> emptyList()
        }

        val recentCs = changesetsByUid[uid]
        val analysis = UserAnalyzer.analyze(user, userHistory, recentCs)

        if (analysis.isNewcomer) {
            sendNotification(user.displayName, settings.defaultAreaName)
            Timber.d("Notification sent for new mapper: ${user.displayName}")
            uid
        } else {
            null
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
