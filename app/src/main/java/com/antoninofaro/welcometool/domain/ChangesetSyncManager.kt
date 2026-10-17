package com.antoninofaro.welcometool.domain

import com.antoninofaro.welcometool.data.entity.ChangesetEntity
import com.antoninofaro.welcometool.data.local.dao.ChangesetDao
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChangesetSyncManager @Inject constructor(
    private val osmRepository: OsmRepository,
    private val changesetDao: ChangesetDao,
    private val areaSyncStateDao: com.antoninofaro.welcometool.data.local.dao.AreaSyncStateDao,
    private val settingsRepository: SettingsRepository,
    private val json: Json
) {
    companion object {
        const val PAGE_LIMIT = 100
        const val MAX_PHASE_A_PAGES = 10
        const val PHASE_B_CADENCE_MS = 30 * 60 * 1000L // 30 minutes
        const val MAX_TRACKING_AGE_HOURS = 48L
        const val INTER_PAGE_DELAY_MS = 1000L
    }

    /**
     * Executes Phase A sync: fetches new changesets for the specified area [bbox]
     * using backward cursor pagination without gaps.
     */
    suspend fun executePhaseA(
        bbox: String,
        syncStartTimeMs: Long = System.currentTimeMillis(),
        initialWindowHours: Long = 24L,
        maxPages: Int = MAX_PHASE_A_PAGES,
        delayBetweenPagesMs: Long = INTER_PAGE_DELAY_MS
    ): Result<List<OsmChangeset>> {
        var syncState = areaSyncStateDao.getSyncState(bbox) ?: com.antoninofaro.welcometool.data.entity.AreaSyncStateEntity(bbox)
        val lastSyncMs = syncState.lastSyncStartTimeMs

        val cursorStartInstant = if (lastSyncMs > 0L) {
            Instant.ofEpochMilli(lastSyncMs)
        } else {
            Instant.ofEpochMilli(syncStartTimeMs).minus(initialWindowHours, ChronoUnit.HOURS)
        }

        val t1Instant = cursorStartInstant.minus(5, ChronoUnit.MINUTES)
        val t1Str = t1Instant.toString()

        val allFetched = mutableListOf<OsmChangeset>()
        val seenIds = mutableSetOf<Long>()
        var currentT2Str: String? = null
        var pageCount = 0
        var allPagesSuccessful = true

        while (pageCount < maxPages) {
            pageCount++
            if (pageCount > 1 && delayBetweenPagesMs > 0) {
                delay(delayBetweenPagesMs)
            }

            val timeParam = if (currentT2Str == null) t1Str else "$t1Str,$currentT2Str"
            val pageResult = osmRepository.fetchRecentChangesets(
                bbox = bbox,
                timeRange = timeParam,
                limit = PAGE_LIMIT
            )

            if (pageResult !is Result.Success) {
                allPagesSuccessful = false
                val exception = pageResult.exceptionOrNull()
                if (exception is com.antoninofaro.welcometool.di.RateLimitException) {
                    throw exception
                }
                Timber.w("Phase A sync page %d failed: %s", pageCount, exception?.message)
                break
            }

            val pageList = pageResult.data
            if (pageList.isEmpty()) {
                break
            }

            val newItems = pageList.filter { seenIds.add(it.id) }
            if (newItems.isEmpty()) {
                Timber.d("Phase A anti-loop triggered at page %d (no new IDs)", pageCount)
                break
            }

            allFetched.addAll(newItems)

            if (pageList.size < PAGE_LIMIT) {
                break
            }

            val oldestInPage = pageList.minByOrNull { parseInstantToEpochMilli(it.createdAt) }
            if (oldestInPage == null) break

            val oldestInstant = Instant.ofEpochMilli(parseInstantToEpochMilli(oldestInPage.createdAt))
            currentT2Str = oldestInstant.plus(1, ChronoUnit.SECONDS).toString()
        }

        if (pageCount >= maxPages) {
            Timber.w("Reached max limit of %d pages during Phase A sync for bbox %s", maxPages, bbox)
        }

        if (allFetched.isNotEmpty()) {
            val entities = allFetched.map { it.toEntity(bbox, "recent") }
            changesetDao.insertOrUpdateChangesets(entities)
        }

        if (allPagesSuccessful) {
            syncState = syncState.copy(lastSyncStartTimeMs = syncStartTimeMs)
            val maxChangeset = allFetched.maxByOrNull { it.id }
            if (maxChangeset != null && maxChangeset.id > syncState.forwardCursorId) {
                syncState = syncState.copy(
                    forwardCursorId = maxChangeset.id,
                    forwardCursorDate = maxChangeset.createdAt
                )
            }
            areaSyncStateDao.insertOrUpdate(syncState)
            
            // Legacy fallbacks (optional but safe)
            settingsRepository.updateLastSyncStartTimeMs(syncStartTimeMs)
            allFetched.maxByOrNull { it.createdAt }?.let { newest ->
                settingsRepository.updateLastKnownChangesetId(newest.id)
                settingsRepository.updateLastKnownChangesetDate(newest.createdAt)
            }
        }

        return Result.Success(allFetched)
    }

    /**
     * Executes Phase B sync: updates status for changesets that are currently open in Room.
     * Only runs if open changesets exist and enough time has elapsed since last Phase B run.
     */
    suspend fun executePhaseB(
        nowMs: Long = System.currentTimeMillis(),
        force: Boolean = false,
        bbox: String
    ): Int {
        var syncState = areaSyncStateDao.getSyncState(bbox) ?: com.antoninofaro.welcometool.data.entity.AreaSyncStateEntity(bbox)
        val lastPhaseB = syncState.lastPhaseBTimeMs

        if (!force && (nowMs - lastPhaseB) < PHASE_B_CADENCE_MS) {
            Timber.d("Phase B skipped due to cadence interval (%d ms elapsed)", nowMs - lastPhaseB)
            return 0
        }

        val openEntities = changesetDao.getOpenChangesetsForTracking()
        if (openEntities.isEmpty()) {
            syncState = syncState.copy(lastPhaseBTimeMs = nowMs)
            areaSyncStateDao.insertOrUpdate(syncState)
            return 0
        }

        var updatedCount = 0

        openEntities.chunked(PAGE_LIMIT).forEach { chunk ->
            val ids = chunk.map { it.id }
            val result = osmRepository.fetchChangesetsByIds(ids)

            if (result is Result.Success) {
                val fetchedMap = result.data.associateBy { it.id }

                for (entity in chunk) {
                    val fetched = fetchedMap[entity.id]
                    val createdAtMs = parseInstantToEpochMilli(entity.createdAt)
                    val ageHours = (nowMs - createdAtMs) / (1000 * 3600)
                    val isOlderThan48h = ageHours >= MAX_TRACKING_AGE_HOURS

                    if (fetched != null) {
                        val isOpen = fetched.open
                        val stopTracking = !isOpen || isOlderThan48h

                        changesetDao.updatePhaseBStatus(
                            changesetId = entity.id,
                            open = isOpen,
                            closedAt = fetched.closedAt,
                            numChanges = fetched.totalChanges,
                            commentsCount = fetched.commentsCount,
                            stopTracking = stopTracking,
                            lastUpdated = nowMs
                        )
                        updatedCount++
                    } else if (isOlderThan48h) {
                        changesetDao.updatePhaseBStatus(
                            changesetId = entity.id,
                            open = entity.open,
                            closedAt = entity.closedAt,
                            numChanges = entity.numChanges,
                            commentsCount = entity.commentsCount,
                            stopTracking = true,
                            lastUpdated = nowMs
                        )
                    }
                }
            }
        }

        syncState = syncState.copy(lastPhaseBTimeMs = nowMs)
        areaSyncStateDao.insertOrUpdate(syncState)
        return updatedCount
    }

    private fun OsmChangeset.toEntity(bbox: String, fetchSource: String = "unknown"): ChangesetEntity {
        val tagsJsonStr = if (!tags.isNullOrEmpty()) {
            json.encodeToString(tags)
        } else null

        return ChangesetEntity(
            id = id,
            bbox = bbox,
            createdAt = createdAt,
            closedAt = closedAt,
            open = open,
            user = user,
            uid = uid,
            minLat = minLat,
            minLon = minLon,
            maxLat = maxLat,
            maxLon = maxLon,
            numChanges = totalChanges,
            commentsCount = commentsCount,
            tagsJson = tagsJsonStr,
            fetchSource = fetchSource,
            etag = etag,
            lastModified = lastModified
        )
    }

    private fun parseInstantToEpochMilli(isoString: String): Long {
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
