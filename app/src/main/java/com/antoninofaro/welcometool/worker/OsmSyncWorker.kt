package com.antoninofaro.welcometool.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.domain.ChangesetSyncManager
import com.antoninofaro.welcometool.domain.UserAnalyzer
import com.antoninofaro.welcometool.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import com.antoninofaro.welcometool.data.model.Result as ApiResult

@HiltWorker
class OsmSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: ChangesetSyncManager,
    private val osmRepository: OsmRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper,
    private val notifiedUserStorage: NotifiedUserStorage
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val SEMAPHORE_LIMIT = 6
    }

    override suspend fun doWork(): Result {
        Timber.d("OsmSyncWorker started")

        return try {
            val settings = settingsRepository.settingsFlow.first()

            val syncResult = syncManager.executePhaseA(bbox = settings.defaultBBox)
            if (syncResult !is ApiResult.Success) {
                return Result.retry()
            }

            val allChangesets = syncResult.data

            syncManager.executePhaseB(bbox = settings.defaultBBox)

            val hasNewChangesets = checkNewChangesets(allChangesets, settings)
            checkNewMappers(allChangesets, settings)

            Timber.i("Scan metrics: changesets=%d result=success", allChangesets.size)

            if (hasNewChangesets) {
                com.antoninofaro.welcometool.utils.WorkerUtils.scheduleOsmSyncWorker(
                    applicationContext,
                    settings.autoRefreshInterval,
                    settings.autoRefresh
                )
            } else {
                val slowInterval = 120 // 2 hours
                if (settings.autoRefreshInterval < slowInterval) {
                    Timber.d("No new changesets, applying dynamic backoff to %d minutes", slowInterval)
                    com.antoninofaro.welcometool.utils.WorkerUtils.scheduleOsmSyncWorker(
                        applicationContext,
                        slowInterval,
                        settings.autoRefresh
                    )
                }
            }

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.antoninofaro.welcometool.di.RateLimitException) {
            Timber.w("RateLimitException in OsmSyncWorker, returning Result.retry(). Retry after: ${e.retryAfterSeconds}s")
            Result.retry()
        } catch (e: Exception) {
            Timber.e(e, "Error during OsmSyncWorker execution")
            Result.retry()
        }
    }

    private suspend fun checkNewChangesets(
        changesets: List<OsmChangeset>,
        settings: com.antoninofaro.welcometool.data.repository.AppSettings
    ): Boolean {
        val lastKnownId = settings.lastKnownChangesetId
        val maxId = changesets.maxOfOrNull { it.id } ?: return false

        if (maxId > lastKnownId) {
            val newChangesets = changesets.filter { it.id > lastKnownId }
            Timber.d("New changesets found: ${newChangesets.size}")

            if (lastKnownId != 0L && settings.showNewChangesetNotifications && newChangesets.isNotEmpty()) {
                notificationHelper.createNotificationChannel()
                notificationHelper.sendNewChangesetsNotification(newChangesets, settings.individualChangesetNotifications, settings.notificationAutoDismissHours)
            }

            val newest = changesets.maxByOrNull { it.createdAt } ?: return true
            settingsRepository.updateLastKnownChangesetId(maxId)
            settingsRepository.updateLastKnownChangesetDate(newest.createdAt)
            return true
        } else {
            Timber.d("No new changesets since last check.")
            return false
        }
    }

    private suspend fun checkNewMappers(
        changesets: List<OsmChangeset>,
        settings: com.antoninofaro.welcometool.data.repository.AppSettings
    ) {
        if (!settings.showNotifications) {
            Timber.d("Notifications disabled in settings")
            return
        }

        val uniqueUids = changesets.mapTo(mutableSetOf()) { it.uid }
        val notifiedIds = notifiedUserStorage.getAllNotifiedIds()
        val changesetsByUid = changesets.reversed().associateBy { it.uid }
        val semaphore = Semaphore(SEMAPHORE_LIMIT)

        val (newlyNotified, newcomers) = coroutineScope {
            uniqueUids
                .filterNot { notifiedIds.contains(it.toString()) }
                .map { uid ->
                    async {
                        semaphore.withPermit {
                            processNewcomer(uid, changesets, changesetsByUid, settings)
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .fold(LinkedHashSet<Long>() to mutableListOf<Triple<Long, String, String>>()) { (ids, names), result ->
                    ids.add(result.first)
                    names.add(result.second)
                    ids to names
                }
        }

        if (newcomers.isNotEmpty()) {
            notificationHelper.sendNewMapperNotification(newcomers, settings.notificationAutoDismissHours)
        }

        notifiedUserStorage.markAsNotifiedBatch(newlyNotified)
    }

    private suspend fun processNewcomer(
        uid: Long,
        allChangesets: List<OsmChangeset>,
        changesetsByUid: Map<Long, OsmChangeset>,
        settings: com.antoninofaro.welcometool.data.repository.AppSettings
    ): Pair<Long, Triple<Long, String, String>>? = coroutineScope {
        val userResult = osmRepository.fetchUserDetail(uid)
        if (userResult.isError && userResult.exceptionOrNull() is com.antoninofaro.welcometool.di.RateLimitException) {
            throw userResult.exceptionOrNull() as com.antoninofaro.welcometool.di.RateLimitException
        }
        val user = when (userResult) {
            is ApiResult.Success -> userResult.data
            else -> return@coroutineScope null
        }

        val recentCs = changesetsByUid[uid]
        val userChangesets = allChangesets.filter { it.uid == uid }
        val analysis = UserAnalyzer.analyze(user, userChangesets, recentCs)

        if (analysis.isNewcomer) {
            Timber.d("New mapper detected: ${user.displayName}")
            uid to Triple(uid, user.displayName, settings.defaultAreaName)
        } else {
            null
        }
    }
}
