package com.antoninofaro.welcometool.domain

import com.antoninofaro.welcometool.data.entity.ChangesetEntity
import com.antoninofaro.welcometool.data.local.dao.ChangesetDao
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChangesetSyncManagerTest {

    @Mock
    private lateinit var osmRepository: OsmRepository
    @Mock
    private lateinit var changesetDao: ChangesetDao
    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var areaSyncStateDao: com.antoninofaro.welcometool.data.local.dao.AreaSyncStateDao

    private lateinit var syncManager: ChangesetSyncManager
    private lateinit var settingsFlow: MutableStateFlow<AppSettings>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        settingsFlow = MutableStateFlow(AppSettings())
        whenever(settingsRepository.settingsFlow).thenReturn(settingsFlow)

        syncManager = ChangesetSyncManager(
            osmRepository = osmRepository,
            changesetDao = changesetDao,
            areaSyncStateDao = areaSyncStateDao,
            settingsRepository = settingsRepository,
            json = Json { ignoreUnknownKeys = true }
        )
    }

    private fun createChangeset(id: Long, createdAt: String, open: Boolean = false): OsmChangeset {
        return OsmChangeset(
            id = id,
            createdAt = createdAt,
            closedAt = if (open) null else createdAt,
            open = open,
            user = "User_$id",
            uid = id,
            minLat = 37.5,
            minLon = 15.0,
            maxLat = 37.6,
            maxLon = 15.1,
            numChanges = 5,
            commentsCount = 0
        )
    }

    @Test
    fun `Phase A pagination fetches 100 + 100 + 37 results successfully`() = runTest {
        val bbox = "15.0,37.5,15.1,37.6"
        val page1 = (1L..100L).map { createChangeset(it, "2026-03-29T10:00:00Z") }
        val page2 = (101L..200L).map { createChangeset(it, "2026-03-29T09:00:00Z") }
        val page3 = (201L..237L).map { createChangeset(it, "2026-03-29T08:00:00Z") }

        whenever(osmRepository.fetchRecentChangesets(eq(bbox), anyOrNull(), eq(100)))
            .thenReturn(Result.Success(page1))
            .thenReturn(Result.Success(page2))
            .thenReturn(Result.Success(page3))

        val result = syncManager.executePhaseA(bbox = bbox, delayBetweenPagesMs = 0L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val fetched = (result as Result.Success).data
        assertThat(fetched).hasSize(237)

        verify(osmRepository, times(3)).fetchRecentChangesets(eq(bbox), anyOrNull(), eq(100))
        verify(changesetDao, times(1)).insertOrUpdateChangesets(any())
        verify(settingsRepository, times(1)).updateLastSyncStartTimeMs(any())
    }

    @Test
    fun `Phase A anti-loop stops pagination when no new IDs are returned`() = runTest {
        val bbox = "15.0,37.5,15.1,37.6"
        val page1 = (1L..100L).map { createChangeset(it, "2026-03-29T10:00:00Z") }
        val page2 = page1

        whenever(osmRepository.fetchRecentChangesets(eq(bbox), anyOrNull(), eq(100)))
            .thenReturn(Result.Success(page1))
            .thenReturn(Result.Success(page2))

        val result = syncManager.executePhaseA(bbox = bbox, delayBetweenPagesMs = 0L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val fetched = (result as Result.Success).data
        assertThat(fetched).hasSize(100)

        verify(osmRepository, times(2)).fetchRecentChangesets(eq(bbox), anyOrNull(), eq(100))
    }

    @Test
    fun `Phase A stops at 10 pages maximum limit`() = runTest {
        val bbox = "15.0,37.5,15.1,37.6"
        var currentId = 1L

        whenever(osmRepository.fetchRecentChangesets(eq(bbox), anyOrNull(), eq(100))).thenAnswer {
            val list = (currentId until (currentId + 100L)).map { createChangeset(it, "2026-03-29T10:00:00Z") }
            currentId += 100L
            Result.Success(list)
        }

        val result = syncManager.executePhaseA(bbox = bbox, delayBetweenPagesMs = 0L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val fetched = (result as Result.Success).data
        assertThat(fetched).hasSize(1000)

        verify(osmRepository, times(10)).fetchRecentChangesets(eq(bbox), anyOrNull(), eq(100))
    }

    @Test
    fun `Phase A cursor does not update if a page fails`() = runTest {
        val bbox = "15.0,37.5,15.1,37.6"
        val page1 = (1L..100L).map { createChangeset(it, "2026-03-29T10:00:00Z") }

        whenever(osmRepository.fetchRecentChangesets(eq(bbox), anyOrNull(), eq(100)))
            .thenReturn(Result.Success(page1))
            .thenReturn(Result.Error(Exception("Network error")))

        syncManager.executePhaseA(bbox = bbox, delayBetweenPagesMs = 0L)

        verify(settingsRepository, never()).updateLastSyncStartTimeMs(any())
    }

    @Test
    fun `Phase B batches up to 100 IDs and applies 48 hour ceiling with stopTracking`() = runTest {
        val nowMs = System.currentTimeMillis()
        val recentDate = Instant.ofEpochMilli(nowMs - 5 * 3600 * 1000L).toString()
        val oldDate = Instant.ofEpochMilli(nowMs - 50 * 3600 * 1000L).toString()

        val entities = (1L..105L).map { id ->
            val date = if (id == 105L) oldDate else recentDate
            ChangesetEntity(
                id = id,
                bbox = "15.0,37.5,15.1,37.6",
                createdAt = date,
                closedAt = null,
                open = true,
                user = "User_$id",
                uid = id,
                minLat = 37.5,
                minLon = 15.0,
                maxLat = 37.6,
                maxLon = 15.1,
                numChanges = 5,
                commentsCount = 0,
                tagsJson = null,
                stopTracking = false
            )
        }

        whenever(changesetDao.getOpenChangesetsForTracking()).thenReturn(entities)

        whenever(osmRepository.fetchChangesetsByIds(any())).thenAnswer { invocation ->
            val ids = invocation.getArgument<List<Long>>(0)
            val changesets = ids.map { id ->
                val date = if (id == 105L) oldDate else recentDate
                createChangeset(id, date, open = true)
            }
            Result.Success(changesets)
        }

        val updated = syncManager.executePhaseB(nowMs = nowMs, force = true, bbox = "15.0,37.5,15.1,37.6")

        assertThat(updated).isEqualTo(105)
        verify(osmRepository, times(2)).fetchChangesetsByIds(any())

        verify(changesetDao).updatePhaseBStatus(
            changesetId = eq(105L),
            open = eq(true),
            closedAt = anyOrNull(),
            numChanges = eq(5),
            commentsCount = eq(0),
            stopTracking = eq(true),
            lastUpdated = eq(nowMs)
        )
    }

    @Test
    fun `verify no area changeset queries are performed without bbox`() {
        val serviceMethods = com.antoninofaro.welcometool.data.network.OsmApiService::class.java.methods
        
        val getRecent = serviceMethods.firstOrNull { it.name == "getRecentChangesets" }
        assertThat(getRecent).isNotNull()
        val recentBboxParam = getRecent!!.parameterTypes.firstOrNull { it == String::class.java }
        assertThat(recentBboxParam).isNotNull()

        val getUser = serviceMethods.firstOrNull { it.name == "getUserChangesets" }
        assertThat(getUser).isNotNull()
        val userBboxParam = getUser!!.parameterTypes.firstOrNull { it == String::class.java }
        assertThat(userBboxParam).isNotNull()
    }
}
