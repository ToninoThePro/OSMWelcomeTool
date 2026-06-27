package com.antoninofaro.welcometool.worker

import android.content.Context
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
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
import org.mockito.kotlin.anyOrNull
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
    fun `periodic scan with last known found in first batch succeeds with one fetch`() = runTest {
        settingsFlow.value = AppSettings(lastKnownChangesetDate = "2026-06-15T12:00:00Z")
        val batch = listOf(
            cs(102, "2026-06-16T00:00:00Z"),
            cs(101, "2026-06-15T12:00:00Z"),
            cs(100, "2026-06-15T10:00:00Z")
        )
        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.Success(batch))

        val result = doWorkSync()

        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.success())
        verify(osmRepository, times(1)).fetchRecentChangesets(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `periodic scan not found triggers deep scan up to 4 windows`() = runTest {
        settingsFlow.value = AppSettings(lastKnownChangesetDate = "2026-05-20T00:00:00Z")

        val batch1 = listOf(cs(104, "2026-06-01T00:00:00Z"), cs(103, "2026-05-25T00:00:00Z"))
        val batch2 = listOf(cs(102, "2026-05-24T00:00:00Z"), cs(101, "2026-05-22T00:00:00Z"))
        val batch3 = listOf(cs(100, "2026-05-21T12:00:00Z"))
        val batch4 = listOf(cs(99, "2026-05-20T01:00:00Z")) // close, not exact match

        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(
                Result.Success(batch1),
                Result.Success(batch2),
                Result.Success(batch3),
                Result.Success(batch4)
            )

        val result = doWorkSync()

        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.success())
        verify(osmRepository, times(4)).fetchRecentChangesets(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `deep scan stops early when last known found in window 2`() = runTest {
        settingsFlow.value = AppSettings(lastKnownChangesetDate = "2026-06-10T00:00:00Z")

        val batch1 = listOf(cs(102, "2026-06-12T00:00:00Z"), cs(101, "2026-06-11T00:00:00Z"))
        val batch2 = listOf(cs(100, "2026-06-10T00:00:00Z"), cs(99, "2026-06-09T00:00:00Z"))

        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.Success(batch1), Result.Success(batch2))

        val result = doWorkSync()

        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.success())
        verify(osmRepository, times(2)).fetchRecentChangesets(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `deep scan stops early when empty batch returned`() = runTest {
        settingsFlow.value = AppSettings(lastKnownChangesetDate = "2026-05-01T00:00:00Z")

        val batch1 = listOf(cs(100, "2026-05-10T00:00:00Z"))

        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.Success(batch1), Result.Success(emptyList()))

        val result = doWorkSync()

        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.success())
        verify(osmRepository, times(2)).fetchRecentChangesets(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `fetch returns null Result triggers retry`() = runTest {
        settingsFlow.value = AppSettings(lastKnownChangesetDate = "2026-06-15T00:00:00Z")
        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.Error(RuntimeException("network error")) as Result<List<OsmChangeset>>)

        val result = doWorkSync()

        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.retry())
    }

    @Test
    fun `first batch empty returns success without further calls`() = runTest {
        settingsFlow.value = AppSettings(lastKnownChangesetDate = "")
        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.Success(emptyList()))

        val result = doWorkSync()

        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.success())
        verify(osmRepository, times(1)).fetchRecentChangesets(any(), anyOrNull(), anyOrNull())
    }

    @Test(expected = CancellationException::class)
    fun `CancellationException propagates`() = runTest {
        settingsFlow.value = AppSettings()
        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenThrow(CancellationException("worker cancelled"))

        doWorkSync()
    }

    @Test
    fun `exception in doWork returns retry`() = runTest {
        settingsFlow.value = AppSettings(lastKnownChangesetDate = "2026-06-15T00:00:00Z")
        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.Success(listOf(cs(1, "2026-06-15T00:00:00Z"))))
        whenever(settingsRepository.updateLastKnownChangesetId(any()))
            .thenThrow(RuntimeException("db error"))

        val result = doWorkSync()

        assertThat(result).isEqualTo(androidx.work.ListenableWorker.Result.retry())
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
        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
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
        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.Success(batch))

        doWorkSync()

        verify(settingsRepository, never()).updateLastKnownChangesetId(any())
    }

    @Test
    fun `checkNewChangesets sends notification when enabled`() = runTest {
        settingsFlow.value = AppSettings(
            lastKnownChangesetDate = "2026-06-10T00:00:00Z",
            lastKnownChangesetId = 50L,
            showNewChangesetNotifications = true
        )
        val batch = listOf(cs(200, "2026-06-20T00:00:00Z"))
        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.Success(batch))

        doWorkSync()

        verify(notificationHelper).createNotificationChannel()
        verify(notificationHelper).sendNewChangesetsNotification(any())
    }

    @Test
    fun `checkNewChangesets skips notification when lastKnownId is 0 (initial scan)`() = runTest {
        settingsFlow.value = AppSettings(
            lastKnownChangesetDate = "2026-06-10T00:00:00Z",
            lastKnownChangesetId = 0L,
            showNewChangesetNotifications = true
        )
        val batch = listOf(cs(200, "2026-06-20T00:00:00Z"))
        whenever(osmRepository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.Success(batch))

        doWorkSync()

        verify(notificationHelper, never()).createNotificationChannel()
        verify(notificationHelper, never()).sendNewChangesetsNotification(any())
    }

    private suspend fun doWorkSync(): androidx.work.ListenableWorker.Result {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication() as Context
        val worker = OsmSyncWorker(
            appContext = ctx,
            workerParams = mock(),
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
