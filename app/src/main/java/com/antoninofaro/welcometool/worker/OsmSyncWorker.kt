package com.antoninofaro.welcometool.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.domain.UserAnalyzer
import com.antoninofaro.welcometool.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import com.antoninofaro.welcometool.data.model.Result as ApiResult

@HiltWorker
class OsmSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val osmRepository: OsmRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper,
    private val notifiedUserStorage: NotifiedUserStorage
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val PERIODIC_SCAN_WINDOWS = 1
        private const val MAX_DEEP_SCAN_WINDOWS = 4
        private const val SEMAPHORE_LIMIT = 6
        private const val CHANGESET_LIMIT = 100
        private const val OSM_EPOCH_START = "2005-01-01T00:00:00Z"
    }

    override suspend fun doWork(): Result {
        Timber.d("OsmSyncWorker started")

        return try {
            val settings = settingsRepository.settingsFlow.first()

            val allChangesets = mutableListOf<OsmChangeset>()
            val lastKnownDate = settings.lastKnownChangesetDate
            var deepScanCursor: String? = null

            // Phase 1: initial window
            val timeParam = if (lastKnownDate.isNotBlank()) lastKnownDate else null
            val firstBatch = fetchBatch(timeParam)
            if (firstBatch == null) return Result.retry()
            if (firstBatch.isEmpty()) return Result.success()
            allChangesets.addAll(firstBatch)

            // Phase 2: deep scan if last-known changeset not in batch
            if (lastKnownDate.isNotBlank() && !batchContainsChangeset(firstBatch, lastKnownDate)) {
                Timber.d("Last known changeset not in first batch, deep scanning...")
                deepScanCursor = computeDeepCursor(firstBatch)

                for (i in 0 until (MAX_DEEP_SCAN_WINDOWS - PERIODIC_SCAN_WINDOWS)) {
                    if (deepScanCursor == null) break

                    val timeRange = "$lastKnownDate,$deepScanCursor"
                    val batch = fetchBatch(timeRange)
                    if (batch == null || batch.isEmpty()) break

                    allChangesets.addAll(batch)

                    if (batchContainsChangeset(batch, lastKnownDate)) {
                        Timber.d("Found last known changeset at deep scan window ${i + 1}")
                        break
                    }

                    deepScanCursor = computeDeepCursor(batch)
                }
            }

            checkNewChangesets(allChangesets, settings)
            checkNewMappers(allChangesets, settings)

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error during OsmSyncWorker execution")
            Result.retry()
        }
    }

    private suspend fun fetchBatch(timeRange: String?): List<OsmChangeset>? {
        val settings = settingsRepository.settingsFlow.first()
        val result = osmRepository.fetchRecentChangesets(
            bbox = settings.defaultBBox,
            timeRange = timeRange,
            limit = CHANGESET_LIMIT
        )
        return result.getOrNull()
    }

    private fun batchContainsChangeset(batch: List<OsmChangeset>, targetDate: String): Boolean {
        return batch.any { it.createdAt == targetDate }
    }

    private fun computeDeepCursor(batch: List<OsmChangeset>): String? {
        return batch.minByOrNull { it.createdAt }?.createdAt
    }

    private suspend fun checkNewChangesets(changesets: List<OsmChangeset>, settings: com.antoninofaro.welcometool.data.repository.AppSettings) {
        val lastKnownId = settings.lastKnownChangesetId
        val maxId = changesets.maxOfOrNull { it.id } ?: return

        if (maxId > lastKnownId) {
            val newChangesets = changesets.filter { it.id > lastKnownId }
            Timber.d("New changesets found: ${newChangesets.size}")

            if (lastKnownId != 0L && settings.showNewChangesetNotifications && newChangesets.isNotEmpty()) {
                notificationHelper.createNotificationChannel()
                notificationHelper.sendNewChangesetsNotification(newChangesets)
            }

            val newest = changesets.maxByOrNull { it.createdAt } ?: return
            settingsRepository.updateLastKnownChangesetId(maxId)
            settingsRepository.updateLastKnownChangesetDate(newest.createdAt)
        } else {
            Timber.d("No new changesets since last check.")
        }
    }

    private suspend fun checkNewMappers(changesets: List<OsmChangeset>, settings: com.antoninofaro.welcometool.data.repository.AppSettings) {
        if (!settings.showNotifications) {
            Timber.d("Notifications disabled in settings")
            return
        }

        val uniqueUids = changesets.map { it.uid }.distinct()
        val notifiedIds = notifiedUserStorage.getAllNotifiedIds()
        val changesetsByUid = changesets.reversed().associateBy { it.uid }
        val semaphore = Semaphore(SEMAPHORE_LIMIT)

        val (newlyNotified, newcomers) = coroutineScope {
            uniqueUids
                .filterNot { notifiedIds.contains(it.toString()) }
                .map { uid ->
                    async {
                        semaphore.withPermit {
                            processNewcomer(uid, changesetsByUid, settings)
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .fold(LinkedHashSet<Long>() to mutableListOf<Pair<String, String>>()) { (ids, names), result ->
                    ids.add(result.first)
                    names.add(result.second)
                    ids to names
                }
        }

        if (newcomers.isNotEmpty()) {
            notificationHelper.sendNewMapperNotification(newcomers)
        }

        notifiedUserStorage.markAsNotifiedBatch(newlyNotified)
    }

    private suspend fun processNewcomer(
        uid: Long,
        changesetsByUid: Map<Long, OsmChangeset>,
        settings: com.antoninofaro.welcometool.data.repository.AppSettings
    ): Pair<Long, Pair<String, String>>? = coroutineScope {
        val user = when (val r = osmRepository.fetchUserDetail(uid)) {
            is ApiResult.Success -> r.data
            else -> return@coroutineScope null
        }

        val recentCs = changesetsByUid[uid]
        val allChangesets = osmRepository.fetchUserChangesets(uid, CHANGESET_LIMIT).getOrNull().orEmpty()
        val analysis = UserAnalyzer.analyze(user, allChangesets, recentCs)

        if (analysis.isNewcomer) {
            Timber.d("New mapper detected: ${user.displayName}")
            uid to (user.displayName to settings.defaultAreaName)
        } else {
            null
        }
    }


}
