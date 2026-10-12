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
    private val osmRepository: OsmRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper,
    private val notifiedUserStorage: NotifiedUserStorage,
    private val changesetDao: com.antoninofaro.welcometool.data.local.dao.ChangesetDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val PERIODIC_SCAN_WINDOWS = 1
        private const val MAX_DEEP_SCAN_WINDOWS = 4
        private const val SEMAPHORE_LIMIT = 6
        private const val CHANGESET_LIMIT = 100
    }

    override suspend fun doWork(): Result {
        Timber.d("OsmSyncWorker started")

        return try {
            val settings = settingsRepository.settingsFlow.first()
            val lastKnownDate = settings.lastKnownChangesetDate

            val allChangesets = mutableListOf<OsmChangeset>()
            var deepScanCursor: String? = null
            var windowsFetched = 0
            var deepScanTriggered = false
            var lastKnownFound = false

            val timeParam = if (lastKnownDate.isNotBlank()) lastKnownDate else null
            val firstBatch = fetchBatch(timeParam)
            windowsFetched++
            if (firstBatch == null) {
                Timber.w(
                    "Scan metrics: windows=%d changesets=%d deepScan=%s found=%s result=retry(null batch)",
                    windowsFetched, 0, deepScanTriggered, lastKnownFound
                )
                return Result.retry()
            }
            if (firstBatch.isEmpty()) {
                Timber.i(
                    "Scan metrics: windows=%d changesets=%d deepScan=%s found=%s result=success(empty)",
                    windowsFetched, 0, deepScanTriggered, lastKnownFound
                )
                return Result.success()
            }
            allChangesets.addAll(firstBatch)

            if (lastKnownDate.isNotBlank() && !batchContainsChangeset(firstBatch, lastKnownDate)) {
                deepScanTriggered = true
                Timber.d("Last known changeset not in first batch, deep scanning...")
                deepScanCursor = computeDeepCursor(firstBatch)

                for (i in 0 until (MAX_DEEP_SCAN_WINDOWS - PERIODIC_SCAN_WINDOWS)) {
                    if (deepScanCursor == null) break

                    val timeRange = "$lastKnownDate,$deepScanCursor"
                    val batch = fetchBatch(timeRange)
                    windowsFetched++
                    if (batch == null || batch.isEmpty()) break

                    allChangesets.addAll(batch)

                    if (batchContainsChangeset(batch, lastKnownDate)) {
                        lastKnownFound = true
                        Timber.d("Found last known changeset at deep scan window ${i + 1}")
                        break
                    }

                    deepScanCursor = computeDeepCursor(batch)
                }
            } else if (lastKnownDate.isNotBlank()) {
                lastKnownFound = true
            }

            checkNewChangesets(allChangesets, settings)
            checkNewMappers(allChangesets, settings)

            Timber.i(
                "Scan metrics: windows=%d changesets=%d deepScan=%s found=%s result=success",
                windowsFetched, allChangesets.size, deepScanTriggered, lastKnownFound
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.antoninofaro.welcometool.di.RateLimitException) {
            Timber.w("RateLimitException in OsmSyncWorker, returning Result.retry(). Retry after: ${e.retryAfterSeconds}s")
            Result.retry()
        } catch (e: Exception) {
            Timber.e(e, "Error during OsmSyncWorker execution")
            Timber.w("Scan metrics: result=retry(exception=%s)", e.javaClass.simpleName)
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
        if (result.isError && result.exceptionOrNull() is com.antoninofaro.welcometool.di.RateLimitException) { throw result.exceptionOrNull() as com.antoninofaro.welcometool.di.RateLimitException }
        val list = result.getOrNull()
        if (!list.isNullOrEmpty()) {
            changesetDao.insertOrUpdateChangesets(list.map {
                val tagsJson = if (!it.tags.isNullOrEmpty()) {
                    kotlinx.serialization.json.Json.encodeToString(it.tags)
                } else null
                com.antoninofaro.welcometool.data.entity.ChangesetEntity(
                    id = it.id,
                    bbox = settings.defaultBBox,
                    createdAt = it.createdAt,
                    closedAt = it.closedAt,
                    open = it.open,
                    user = it.user,
                    uid = it.uid,
                    minLat = it.minLat,
                    minLon = it.minLon,
                    maxLat = it.maxLat,
                    maxLon = it.maxLon,
                    numChanges = it.totalChanges,
                    commentsCount = it.commentsCount,
                    tagsJson = tagsJson
                )
            })
        }
        return list
    }

    private fun batchContainsChangeset(batch: List<OsmChangeset>, targetDate: String): Boolean {
        return batch.any { it.createdAt == targetDate }
    }

    private fun computeDeepCursor(batch: List<OsmChangeset>): String? {
        return batch.minByOrNull { 
            try {
                java.time.Instant.parse(it.createdAt).toEpochMilli()
            } catch (e: Exception) {
                Long.MAX_VALUE
            }
        }?.createdAt
    }

    private suspend fun checkNewChangesets(
        changesets: List<OsmChangeset>,
        settings: com.antoninofaro.welcometool.data.repository.AppSettings
    ) {
        val lastKnownId = settings.lastKnownChangesetId
        val maxId = changesets.maxOfOrNull { it.id } ?: return

        if (maxId > lastKnownId) {
            val newChangesets = changesets.filter { it.id > lastKnownId }
            Timber.d("New changesets found: ${newChangesets.size}")

            if (lastKnownId != 0L && settings.showNewChangesetNotifications && newChangesets.isNotEmpty()) {
                notificationHelper.createNotificationChannel()
                notificationHelper.sendNewChangesetsNotification(newChangesets, settings.individualChangesetNotifications, settings.notificationAutoDismissHours)
            }

            val newest = changesets.maxByOrNull { it.createdAt } ?: return
            settingsRepository.updateLastKnownChangesetId(maxId)
            settingsRepository.updateLastKnownChangesetDate(newest.createdAt)
        } else {
            Timber.d("No new changesets since last check.")
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
        // Use changesets from the scan instead of fetching again
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
