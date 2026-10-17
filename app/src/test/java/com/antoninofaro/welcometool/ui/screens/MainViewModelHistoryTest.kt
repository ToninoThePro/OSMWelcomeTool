package com.antoninofaro.welcometool.ui.screens

import android.app.Application
import com.antoninofaro.welcometool.data.entity.AreaSyncStateEntity
import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.local.dao.AreaSyncStateDao
import com.antoninofaro.welcometool.data.local.dao.ChangesetDao
import com.antoninofaro.welcometool.data.local.dao.UserDao
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.OsmChaRepository
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.data.repository.WelcomedUserStorage
import com.antoninofaro.welcometool.domain.ChangesetSyncManager
import com.antoninofaro.welcometool.utils.ConnectivityObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelHistoryTest {

    private val application = mock<Application>()
    private val repository = mock<OsmRepository>()
    private val syncManager = mock<ChangesetSyncManager>()
    private val welcomedUserStorage = mock<WelcomedUserStorage>()
    private val osmChaRepository = mock<OsmChaRepository>()
    private val userDao = mock<UserDao>()
    private val changesetDao = mock<ChangesetDao>()
    private val areaSyncStateDao = mock<AreaSyncStateDao>()
    private val settingsRepository = mock<SettingsRepository>()
    private val connectivityObserver = mock<ConnectivityObserver>()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel
    private val settingsFlow = MutableStateFlow(AppSettings(isLoaded = true, defaultBBox = "10.0,10.0,20.0,20.0"))

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        whenever(settingsRepository.settingsFlow).thenReturn(settingsFlow)
        whenever(connectivityObserver.isOnline).thenReturn(MutableStateFlow(true))
        whenever(application.getString(any())).thenReturn("Test")
        whenever(application.getString(any(), any())).thenReturn("Test")
        kotlinx.coroutines.runBlocking {
            whenever(userDao.getUsersForBBoxFiltered(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(emptyList())
        }

        viewModel = MainViewModel(
            application = application,
            repository = repository,
            syncManager = syncManager,
            areaSyncStateDao = areaSyncStateDao,
            welcomedUserStorage = welcomedUserStorage,
            osmChaRepository = osmChaRepository,
            userDao = userDao,
            changesetDao = changesetDao,
            settingsRepository = settingsRepository,
            connectivityObserver = connectivityObserver
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createChangeset(id: Long, createdAt: String, uid: Long): OsmChangeset {
        return OsmChangeset(
            id = id,
            createdAt = createdAt,
            uid = uid,
            user = "User_$uid",
            minLat = 15.0, minLon = 15.0, maxLat = 16.0, maxLon = 16.0
        )
    }

    private fun createUserEntity(id: Long): UserEntity {
        return UserEntity(
            id = id,
            displayName = "User_$id",
            accountCreated = "2020-01-01T00:00:00Z",
            description = null,
            accountAge = 1000,
            isNewcomer = false,
            isReturning = false,
            isPowerUser = false,
            totalEdits = 100,
            firstChangesetDate = null,
            lastActiveDate = "2026-03-29T09:00:00Z",
            osmchaLikes = 0,
            osmchaDislikes = 0,
            lastUpdated = System.currentTimeMillis()
        )
    }

    @Test
    fun `test two areas with independent cursors and flags`() = runTest {
        val areaA = "10.0,10.0,20.0,20.0"
        val areaB = "30.0,30.0,40.0,40.0"

        whenever(areaSyncStateDao.getSyncState(areaA)).thenReturn(AreaSyncStateEntity(areaA, backwardCursorDate = "2026-03-29T10:00:00Z", isHistoricalExhausted = false))
        whenever(areaSyncStateDao.getSyncState(areaB)).thenReturn(AreaSyncStateEntity(areaB, backwardCursorDate = "2020-01-01T00:00:00Z", isHistoricalExhausted = true))

        // Switch to Area B
        viewModel.updateBBox(areaB)
        advanceUntilIdle()

        // Attempt backward fetch on Area B which is exhausted
        viewModel.fetchNextWindowFromNetwork()
        advanceUntilIdle()

        // Area B query should be skipped because Area B is exhausted
        verify(repository, never()).fetchRecentChangesets(eq(areaB), anyOrNull(), anyOrNull())
    }

    @Test
    fun `MIN createdAt filtered by fetchSource history is not contaminated by user changesets`() = runTest {
        val bbox = "10.0,10.0,20.0,20.0"
        whenever(areaSyncStateDao.getSyncState(bbox)).thenReturn(AreaSyncStateEntity(bbox, backwardCursorDate = null))
        // getOldestChangesetDateForBBox queries WHERE bbox = :bbox AND fetchSource = 'history'
        whenever(changesetDao.getOldestChangesetDateForBBox(bbox)).thenReturn("2026-03-01T00:00:00Z")

        whenever(syncManager.executePhaseA(any(), any(), any(), any(), any())).thenReturn(Result.Success(emptyList()))
        whenever(repository.fetchRecentChangesets(eq(bbox), eq("2004-01-01T00:00:00Z,2026-03-01T00:00:00Z"), eq(100)))
            .thenReturn(Result.Success(emptyList()))

        viewModel.fetchNextWindowFromNetwork()
        advanceUntilIdle()

        verify(changesetDao).getOldestChangesetDateForBBox(bbox)
        verify(repository).fetchRecentChangesets(eq(bbox), eq("2004-01-01T00:00:00Z,2026-03-01T00:00:00Z"), eq(100))
    }

    @Test
    fun `page of 100 raw changesets totally filtered out does NOT set isHistoricalExhausted`() = runTest {
        val bbox = "10.0,10.0,20.0,20.0"
        whenever(areaSyncStateDao.getSyncState(bbox)).thenReturn(AreaSyncStateEntity(bbox))
        whenever(changesetDao.getOldestChangesetDateForBBox(bbox)).thenReturn("2026-03-29T10:00:00Z")

        // 100 raw changesets that lie OUTSIDE the bbox (e.g. minLat/minLon 80.0)
        val rawList = (1L..100L).map {
            OsmChangeset(id = it, createdAt = "2026-03-29T09:00:00Z", uid = it, user = "u$it", minLat = 80.0, minLon = 80.0, maxLat = 81.0, maxLon = 81.0)
        }
        whenever(repository.fetchRecentChangesets(eq(bbox), anyOrNull(), eq(100))).thenReturn(Result.Success(rawList))

        viewModel.fetchNextWindowFromNetwork()
        advanceUntilIdle()

        // Raw size is 100, so isHistoricalExhausted must NOT be set to true
        verify(areaSyncStateDao).insertOrUpdate(argThat { !isHistoricalExhausted })
    }

    @Test
    fun `100 IDs already in Room does not set isHistoricalExhausted`() = runTest {
        val bbox = "10.0,10.0,20.0,20.0"
        whenever(areaSyncStateDao.getSyncState(bbox)).thenReturn(AreaSyncStateEntity(bbox))
        whenever(changesetDao.getOldestChangesetDateForBBox(bbox)).thenReturn("2026-03-29T10:00:00Z")

        val rawList = (1L..100L).map { createChangeset(it, "2026-03-29T09:00:00Z", it) }
        whenever(repository.fetchRecentChangesets(eq(bbox), anyOrNull(), eq(100))).thenReturn(Result.Success(rawList))

        // All 100 users are known and fresh in Room
        val knownUsers = (1L..100L).map { createUserEntity(it) }
        whenever(userDao.getUsersByIds(any())).thenReturn(knownUsers)

        viewModel.fetchNextWindowFromNetwork()
        advanceUntilIdle()

        verify(areaSyncStateDao).insertOrUpdate(argThat { !isHistoricalExhausted })
        verify(repository, never()).fetchUsersDetails(any())
    }

    @Test
    fun `guaranteed backward cursor movement shifts by 1 second`() = runTest {
        val bbox = "10.0,10.0,20.0,20.0"
        whenever(areaSyncStateDao.getSyncState(bbox)).thenReturn(AreaSyncStateEntity(bbox, backwardCursorDate = "2026-03-29T10:00:00Z"))

        val batch = (1L..100L).map { createChangeset(it, "2026-03-29T09:00:00Z", it) }
        whenever(repository.fetchRecentChangesets(eq(bbox), eq("2004-01-01T00:00:00Z,2026-03-29T10:00:00Z"), eq(100)))
            .thenReturn(Result.Success(batch))

        viewModel.fetchNextWindowFromNetwork()
        advanceUntilIdle()

        // Cursor should shift to 2026-03-29T09:00:01Z (oldest + 1 second)
        verify(areaSyncStateDao).insertOrUpdate(argThat { backwardCursorDate == "2026-03-29T09:00:01Z" })
    }

    @Test
    fun `no details requested for already known fresh users`() = runTest {
        val bbox = "10.0,10.0,20.0,20.0"
        whenever(areaSyncStateDao.getSyncState(bbox)).thenReturn(AreaSyncStateEntity(bbox))

        val batch = listOf(createChangeset(1L, "2026-03-29T09:00:00Z", 100L))
        whenever(repository.fetchRecentChangesets(eq(bbox), anyOrNull(), eq(100))).thenReturn(Result.Success(batch))

        val knownUser = createUserEntity(100L)
        whenever(userDao.getUsersByIds(setOf(100L))).thenReturn(listOf(knownUser))

        viewModel.fetchNextWindowFromNetwork()
        advanceUntilIdle()

        verify(repository, never()).fetchUsersDetails(any())
    }

    @Test
    fun `updateBBox resets isHistoricalExhausted flag for the new area`() = runTest {
        val newBBox = "5.0,5.0,8.0,8.0"
        whenever(userDao.getUsersForBBoxFiltered(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(emptyList())

        viewModel.updateBBox(newBBox)
        advanceUntilIdle()

        verify(areaSyncStateDao).updateIsHistoricalExhausted(newBBox, false)
    }
}
