package com.antoninofaro.welcometool.ui.screens

import android.app.Application
import com.antoninofaro.welcometool.data.local.dao.ChangesetDao
import com.antoninofaro.welcometool.data.local.dao.UserDao
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.OsmChaRepository
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.data.repository.WelcomedUserStorage
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val application = mock<Application>()
    private val repository = mock<OsmRepository>()
    private val syncManager = mock<com.antoninofaro.welcometool.domain.ChangesetSyncManager>()
    private val welcomedUserStorage = mock<WelcomedUserStorage>()
    private val osmChaRepository = mock<OsmChaRepository>()
    private val userDao = mock<UserDao>()
    private val changesetDao = mock<ChangesetDao>()
    private val settingsRepository = mock<SettingsRepository>()
    private val connectivityObserver = mock<ConnectivityObserver>()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        whenever(settingsRepository.settingsFlow).thenReturn(flowOf(AppSettings(isLoaded = true)))
        whenever(connectivityObserver.isOnline).thenReturn(MutableStateFlow(true))
        whenever(application.getString(any())).thenReturn("Test String")
        whenever(application.getString(any(), any())).thenReturn("Test String")
        runBlocking {
            whenever(userDao.getUsersForBBoxFiltered(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyList())
        }

        viewModel = MainViewModel(
            application = application,
            repository = repository,
            syncManager = syncManager,
            areaSyncStateDao = mock(),
            welcomedUserStorage = welcomedUserStorage,
            osmChaRepository = osmChaRepository,
            userDao = userDao,
            changesetDao = changesetDao,
            settingsRepository = settingsRepository,
            connectivityObserver = connectivityObserver
        )
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun `searchOsmUserOnline fetches from repository and inserts into userDao when not found locally`() = runTest {
        // Given
        val searchTerm = "NewOsmUser"
        val mockUser = OsmUser(
            id = 999L,
            displayName = searchTerm,
            accountCreated = "2025-01-01T00:00:00Z",
            description = "Test description",
            img = null,
            roles = emptyList(),
            changesets = null,
            traces = null
        )
        whenever(repository.searchUserByUsername(searchTerm)).thenReturn(Result.Success(mockUser))
        whenever(repository.fetchUserChangesets(org.mockito.kotlin.eq(999L), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.eq(1))).thenReturn(Result.Success(emptyList()))
        whenever(welcomedUserStorage.getAllWelcomedIds()).thenReturn(emptySet())

        // When
        viewModel.searchOsmUserOnline(searchTerm)
        advanceUntilIdle()

        // Then
        verify(repository).searchUserByUsername(searchTerm)
        verify(userDao).insertUser(any())
        verify(userDao).insertOrUpdateUserAreaActivities(any())
        assertThat(viewModel.uiState.value.searchTerm).isEqualTo(searchTerm)
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }
}
