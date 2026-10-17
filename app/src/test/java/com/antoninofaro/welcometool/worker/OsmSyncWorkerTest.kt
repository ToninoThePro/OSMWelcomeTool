package com.antoninofaro.welcometool.worker

import android.content.Context
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.domain.ChangesetSyncManager
import com.antoninofaro.welcometool.utils.NotificationHelper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OsmSyncWorkerTest {

    @Mock
    private lateinit var syncManager: ChangesetSyncManager
    @Mock
    private lateinit var osmRepository: OsmRepository
    @Mock
    private lateinit var settingsRepository: SettingsRepository
    @Mock
    private lateinit var notificationHelper: NotificationHelper
    @Mock
    private lateinit var notifiedUserStorage: NotifiedUserStorage

    private lateinit var settingsFlow: MutableStateFlow<AppSettings>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        settingsFlow = MutableStateFlow(AppSettings())
        whenever(settingsRepository.settingsFlow).thenReturn(settingsFlow)
    }

    @Test
    fun `worker executes Phase A and Phase B sync successfully`() = runTest {
        settingsFlow.value = AppSettings(lastKnownChangesetDate = "2026-06-15T12:00:00Z")
        val batch = listOf(
            cs(102, "2026-06-16T00:00:00Z"),
            cs(101, "2026-06-15T12:00:00Z"),
            cs(100, "2026-06-15T10:00:00Z")
        )
        whenever(syncManager.executePhaseA(any(), any(), any(), any(), any()))
            .thenReturn(Result.Success(batch))

        val result = doWorkSync()

        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.success())
        verify(syncManager, times(1)).executePhaseA(any(), any(), any(), any(), any())
        verify(syncManager, times(1)).executePhaseB(any(), eq(false), any())
    }

    @Test
    fun `fetch returns Error Result triggers retry`() = runTest {
        whenever(syncManager.executePhaseA(any(), any(), any(), any(), any()))
            .thenReturn(Result.Error(RuntimeException("network error")))

        val result = doWorkSync()

        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.retry())
    }

    @Test(expected = CancellationException::class)
    fun `CancellationException propagates`() = runTest {
        whenever(syncManager.executePhaseA(any(), any(), any(), any(), any()))
            .thenThrow(CancellationException("worker cancelled"))

        doWorkSync()
    }

    @Test
    fun `checkNewChangesets updates last known when maxId increases`() = runTest {
        settingsFlow.value = AppSettings(
            lastKnownChangesetDate = "2026-06-10T00:00:00Z",
            lastKnownChangesetId = 50L,
            showNewChangesetNotifications = false
        )
        val batch = listOf(
            cs(200, "2026-06-20T00:00:00Z"),
            cs(150, "2026-06-15T00:00:00Z")
        )
        whenever(syncManager.executePhaseA(any(), any(), any(), any(), any()))
            .thenReturn(Result.Success(batch))

        doWorkSync()

        verify(settingsRepository).updateLastKnownChangesetId(200L)
        verify(settingsRepository).updateLastKnownChangesetDate("2026-06-20T00:00:00Z")
    }

    @Test
    fun `checkNewChangesets does nothing when maxId not increased`() = runTest {
        settingsFlow.value = AppSettings(
            lastKnownChangesetDate = "2026-06-20T00:00:00Z",
            lastKnownChangesetId = 200L,
            showNewChangesetNotifications = false
        )
        val batch = listOf(cs(150, "2026-06-15T00:00:00Z"), cs(100, "2026-06-10T00:00:00Z"))
        whenever(syncManager.executePhaseA(any(), any(), any(), any(), any()))
            .thenReturn(Result.Success(batch))

        doWorkSync()

        verify(settingsRepository, never()).updateLastKnownChangesetId(any())
    }

    @Test
    fun `checkNewChangesets sends notification when enabled`() = runTest {
        settingsFlow.value = AppSettings(
            lastKnownChangesetDate = "2026-06-10T00:00:00Z",
            lastKnownChangesetId = 50L,
            showNewChangesetNotifications = true,
            individualChangesetNotifications = true
        )
        val batch = listOf(cs(200, "2026-06-20T00:00:00Z"))
        whenever(syncManager.executePhaseA(any(), any(), any(), any(), any()))
            .thenReturn(Result.Success(batch))

        doWorkSync()

        verify(notificationHelper).createNotificationChannel()
        verify(notificationHelper).sendNewChangesetsNotification(any(), eq(true), eq(0))
    }

    @Test
    fun `checkNewChangesets skips notification when lastKnownId is 0 (initial scan)`() = runTest {
        settingsFlow.value = AppSettings(
            lastKnownChangesetDate = "2026-06-10T00:00:00Z",
            lastKnownChangesetId = 0L,
            showNewChangesetNotifications = true
        )
        val batch = listOf(cs(200, "2026-06-20T00:00:00Z"))
        whenever(syncManager.executePhaseA(any(), any(), any(), any(), any()))
            .thenReturn(Result.Success(batch))

        doWorkSync()

        verify(notificationHelper, never()).createNotificationChannel()
        verify(notificationHelper, never()).sendNewChangesetsNotification(any(), any(), any())
    }

    private suspend fun doWorkSync(): androidx.work.ListenableWorker.Result {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication() as Context
        val worker = OsmSyncWorker(
            appContext = ctx,
            workerParams = mock(),
            syncManager = syncManager,
            osmRepository = osmRepository,
            settingsRepository = settingsRepository,
            notificationHelper = notificationHelper,
            notifiedUserStorage = notifiedUserStorage
        )
        return worker.doWork()
    }

    private fun cs(id: Long, createdAt: String, uid: Long = 1L): OsmChangeset =
        OsmChangeset(id = id, createdAt = createdAt, uid = uid, user = "user$uid")
}
