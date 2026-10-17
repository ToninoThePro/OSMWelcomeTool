package com.antoninofaro.welcometool.ui.screens

import com.antoninofaro.welcometool.data.local.dao.ChangesetDao
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.OsmChaRepository
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.domain.ChangesetSyncManager
import com.antoninofaro.welcometool.utils.ConnectivityObserver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ChangesetsViewModelTest {

    private val changesetDao = mock<ChangesetDao>()
    private val osmRepository = mock<OsmRepository>()
    private val syncManager = mock<ChangesetSyncManager>()
    private val osmChaRepository = mock<OsmChaRepository>()
    private val settingsRepository = mock<SettingsRepository>()
    private val connectivityObserver = mock<ConnectivityObserver>()

    private val testDispatcher = StandardTestDispatcher()
    private val isOnlineFlow = MutableStateFlow(true)
    private val appSettings = AppSettings(isLoaded = true, defaultBBox = "10.0,40.0,11.0,41.0", defaultAreaName = "Palermo")

    private lateinit var viewModel: ChangesetsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        whenever(settingsRepository.settingsFlow).thenReturn(flowOf(appSettings))
        whenever(connectivityObserver.isOnline).thenReturn(isOnlineFlow)

        runBlocking {
            whenever(changesetDao.getChangesetsForBBoxPage(any(), any(), any())).thenReturn(emptyList())
            whenever(changesetDao.getChangesetsForBBoxFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(emptyList())
        }

        viewModel = ChangesetsViewModel(
            changesetDao = changesetDao,
            osmRepository = osmRepository,
            syncManager = syncManager,
            osmChaRepository = osmChaRepository,
            settingsRepository = settingsRepository,
            connectivityObserver = connectivityObserver
        )
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateUserFilter sets filter and resets includeOutsideBBox if user filter removed`() = runTest {
        // Given user filter active
        viewModel.updateUserFilter("Mario")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.userFilter).isEqualTo("Mario")

        // Toggle include outside bbox
        viewModel.toggleIncludeOutsideBBox()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.includeOutsideBBox).isTrue()

        // Clear user filter
        viewModel.updateUserFilter(null)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.userFilter).isEmpty()
        assertThat(viewModel.uiState.value.includeOutsideBBox).isFalse()
    }

    @Test
    fun `loadMoreFromNetwork targets user when userFilter is active`() = runTest {
        // Given user filter active
        viewModel.updateUserFilter("Mario")
        advanceUntilIdle()

        whenever(
            osmRepository.fetchUserChangesets(
                userId = eq(null),
                bbox = eq("10.0,40.0,11.0,41.0"),
                username = eq("Mario"),
                timeRange = anyOrNull(),
                limit = eq(100)
            )
        ).thenReturn(Result.Success(emptyList()))

        // When
        viewModel.loadMoreFromNetwork()
        advanceUntilIdle()

        // Then
        verify(osmRepository).fetchUserChangesets(
            userId = eq(null),
            bbox = eq("10.0,40.0,11.0,41.0"),
            username = eq("Mario"),
            timeRange = anyOrNull(),
            limit = eq(100)
        )
        verify(osmRepository, never()).fetchRecentChangesets(any(), any(), any())
    }

    @Test
    fun `loadMoreFromNetwork includes outside bbox when includeOutsideBBox is true`() = runTest {
        viewModel.updateUserFilter("Mario")
        viewModel.toggleIncludeOutsideBBox()
        advanceUntilIdle()

        whenever(
            osmRepository.fetchUserChangesets(
                userId = eq(null),
                bbox = eq(null),
                username = eq("Mario"),
                timeRange = anyOrNull(),
                limit = eq(100)
            )
        ).thenReturn(Result.Success(emptyList()))

        viewModel.loadMoreFromNetwork()
        advanceUntilIdle()

        verify(osmRepository).fetchUserChangesets(
            userId = eq(null),
            bbox = eq(null),
            username = eq("Mario"),
            timeRange = anyOrNull(),
            limit = eq(100)
        )
    }
}
