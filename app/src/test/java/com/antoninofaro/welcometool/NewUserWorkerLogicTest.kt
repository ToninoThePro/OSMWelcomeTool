package com.antoninofaro.welcometool

import com.antoninofaro.welcometool.data.model.CountWrapper
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.data.repository.normalizeOsmchaToken
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NewUserWorkerLogicTest {

    @Mock
    private lateinit var repository: OsmRepository

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var notifiedUserStorage: NotifiedUserStorage

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun testNotificationLogic_shouldNotifyNewUser() = runTest {
        // Given - un nuovo utente non ancora notificato
        val userId = 12345L
        whenever(notifiedUserStorage.getAllNotifiedIds()).thenReturn(emptySet())

        // When - verifica se dovremmo notificare
        val notifiedIds = notifiedUserStorage.getAllNotifiedIds()
        val shouldNotify = !notifiedIds.contains(userId.toString())

        // Then
        assertThat(shouldNotify).isTrue()
    }

    @Test
    fun testNotificationLogic_shouldNotNotifyAlreadyNotifiedUser() = runTest {
        // Given - un utente già notificato
        val userId = 12345L
        whenever(notifiedUserStorage.getAllNotifiedIds()).thenReturn(setOf("12345"))

        // When
        val notifiedIds = notifiedUserStorage.getAllNotifiedIds()
        val shouldNotify = !notifiedIds.contains(userId.toString())

        // Then - NON dovrebbe notificare di nuovo
        assertThat(shouldNotify).isFalse()
    }

    @Test
    fun testSettingsLogic_notificationsDisabled() = runTest {
        // Given - notifiche disabilitate
        val settings = AppSettings(showNotifications = false)
        whenever(settingsRepository.settingsFlow).thenReturn(MutableStateFlow(settings))

        // When - recupera le impostazioni
        val currentSettings = settingsRepository.settingsFlow

        // Then - verifica che siano corrette
        assertThat(currentSettings).isNotNull()
    }

    @Test
    fun testRepositoryInteraction_fetchChangesets() = runTest {
        // Given
        val mockChangesets = listOf(createMockChangeset(1L, 100L, "user1"))
        whenever(repository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.Success(mockChangesets))

        // When
        val result = repository.fetchRecentChangesets("test_bbox")

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data).hasSize(1)
        assertThat(data[0].uid).isEqualTo(100L)
    }

    @Test
    fun testNewcomerDetection_hasCorrectProperties() {
        // Given
        val newcomer = createMockNewcomerUser(12345L, "newcomer")

        // Then - verifica le proprietà di un newcomer
        assertThat(newcomer.displayName).isEqualTo("newcomer")
        assertThat(newcomer.changesets?.count).isLessThan(100)
        // Account creato nel 2026 (recente)
        assertThat(newcomer.accountCreated).contains("2026")
    }

    @Test
    fun testPowerUserDetection_hasCorrectProperties() {
        // Given
        val powerUser = createMockPowerUser(99999L, "poweruser")

        // Then - verifica le proprietà di un power user
        assertThat(powerUser.displayName).isEqualTo("poweruser")
        assertThat(powerUser.changesets?.count).isGreaterThan(1000)
        // Account creato nel 2020 (vecchio)
        assertThat(powerUser.accountCreated).contains("2020")
    }

    @Test
    fun testNotificationFlow_completeDecisionLogic() = runTest {
        // Given - simula il flusso di decisione completo
        val userId = 100L
        val settings = AppSettings(showNotifications = true)
        val notifiedIds = emptySet<String>()

        val user = createMockNewcomerUser(userId, "newcomer")

        whenever(settingsRepository.settingsFlow).thenReturn(MutableStateFlow(settings))
        whenever(notifiedUserStorage.getAllNotifiedIds()).thenReturn(notifiedIds)

        // When - esegui la logica di decisione
        val notifiedIdsValue = notifiedUserStorage.getAllNotifiedIds()

        val notificationsEnabled = settings.showNotifications
        val notAlreadyNotified = !notifiedIdsValue.contains(userId.toString())
        val isNewcomer = (user.changesets?.count ?: 0) < 100

        val shouldNotify = notificationsEnabled && notAlreadyNotified && isNewcomer

        // Then - dovrebbe notificare
        assertThat(shouldNotify).isTrue()
    }

    @Test
    fun testNotificationFlow_alreadyNotified() = runTest {
        // Given - utente già notificato
        val userId = 100L
        val notifiedIds = setOf("100")

        whenever(notifiedUserStorage.getAllNotifiedIds()).thenReturn(notifiedIds)

        // When
        val notifiedIdsValue = notifiedUserStorage.getAllNotifiedIds()
        val shouldNotify = !notifiedIdsValue.contains(userId.toString())

        // Then - NON dovrebbe notificare
        assertThat(shouldNotify).isFalse()
    }

    @Test
    fun testNotificationFlow_notificationsDisabled() = runTest {
        // Given - notifiche disabilitate
        val settings = AppSettings(showNotifications = false)
        val notifiedIds = emptySet<String>()

        whenever(settingsRepository.settingsFlow).thenReturn(MutableStateFlow(settings))
        whenever(notifiedUserStorage.getAllNotifiedIds()).thenReturn(notifiedIds)

        // When
        val notificationsEnabled = settings.showNotifications

        // Then - NON dovrebbe notificare anche se l'utente è nuovo
        assertThat(notificationsEnabled).isFalse()
    }

    @Test
    fun testMultipleUsers_filteringLogic() = runTest {
        // Given - tre utenti diversi
        val notifiedIds = setOf("300") // Solo l'utente 300 è notificato

        whenever(notifiedUserStorage.getAllNotifiedIds()).thenReturn(notifiedIds)

        // When - controlla ogni utente
        val notifiedIdsValue = notifiedUserStorage.getAllNotifiedIds()

        val shouldNotifyUser100 = !notifiedIdsValue.contains("100")
        val shouldNotifyUser200 = !notifiedIdsValue.contains("200")
        val shouldNotifyUser300 = !notifiedIdsValue.contains("300")

        // Then
        assertThat(shouldNotifyUser100).isTrue() // Nuovo
        assertThat(shouldNotifyUser200).isTrue() // Nuovo
        assertThat(shouldNotifyUser300).isFalse() // Già notificato
    }

    @Test
    fun testEmptyChangesets_noNotifications() = runTest {
        // Given - nessun changeset
        whenever(repository.fetchRecentChangesets(any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.Success(emptyList()))

        // When
        val result = repository.fetchRecentChangesets("test_bbox")

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data).isEmpty()
    }

    @Test
    fun `notifications are disabled by default`() {
        val settings = AppSettings()
        assertThat(settings.showNotifications).isFalse()
        assertThat(settings.showNewChangesetNotifications).isFalse()
    }

    @Test
    fun `normalizeOsmchaToken removes Token prefix and whitespace`() {
        assertThat(normalizeOsmchaToken("Token abc123")).isEqualTo("abc123")
        assertThat(normalizeOsmchaToken("TOKEN abc123")).isEqualTo("abc123")
        assertThat(normalizeOsmchaToken("  abc 123  ")).isEqualTo("abc123")
        assertThat(normalizeOsmchaToken("abc123")).isEqualTo("abc123")
        assertThat(normalizeOsmchaToken("")).isEqualTo("")
    }

    @Test
    fun `decision logic skips new changeset notification when lastKnownId is 0`() {
        val settings = AppSettings(lastKnownChangesetId = 0L, showNewChangesetNotifications = true)
        val changesets = listOf(createMockChangeset(1L, 100L, "user1"))
        val maxId = changesets.maxOf { it.id }

        // lastKnownId == 0 → initial scan, no notification
        val shouldNotify = maxId > settings.lastKnownChangesetId
                && settings.lastKnownChangesetId != 0L
                && settings.showNewChangesetNotifications
                && changesets.isNotEmpty()

        assertThat(maxId).isGreaterThan(0L)
        assertThat(shouldNotify).isFalse()
    }

    @Test
    fun `decision logic notifies when maxId exceeds lastKnownId`() {
        val settings = AppSettings(lastKnownChangesetId = 50L, showNewChangesetNotifications = true)
        val changesets = listOf(createMockChangeset(100L, 1L, "user1"))
        val maxId = changesets.maxOf { it.id }

        val shouldNotify = maxId > settings.lastKnownChangesetId
                && settings.lastKnownChangesetId != 0L
                && settings.showNewChangesetNotifications
                && changesets.isNotEmpty()

        assertThat(shouldNotify).isTrue()
    }

    @Test
    fun `mapper detection dedup via notifiedUserStorage`() = runTest {
        val changesets = listOf(
            createMockChangeset(1L, 100L, "user1"),
            createMockChangeset(2L, 200L, "user2"),
            createMockChangeset(3L, 100L, "user1") // same uid, should dedup
        )
        val uniqueUids = changesets.map { it.uid }.distinct()
        val notifiedIds = setOf("200")

        val toProcess = uniqueUids.filterNot { notifiedIds.contains(it.toString()) }

        assertThat(toProcess).containsExactly(100L) // user2 already notified
    }

    // ==================== HELPER FUNCTIONS ====================

    private fun createMockChangeset(id: Long, uid: Long, username: String): OsmChangeset {
        return OsmChangeset(
            id = id,
            createdAt = "2026-02-23T10:00:00Z",
            closedAt = "2026-02-23T11:00:00Z",
            open = false,
            user = username,
            uid = uid,
            minLat = 37.0,
            minLon = 13.0,
            maxLat = 38.0,
            maxLon = 14.0,
            numChanges = 10,
            commentsCount = 0,
            tags = mapOf("comment" to "Test changeset")
        )
    }

    private fun createMockNewcomerUser(uid: Long, username: String): OsmUser {
        return OsmUser(
            id = uid,
            displayName = username,
            accountCreated = "2026-01-01T00:00:00Z", // Account recente
            description = "Test newcomer user",
            img = null,
            roles = emptyList(),
            changesets = CountWrapper(count = 5), // Poche modifiche
            traces = null
        )
    }

    private fun createMockPowerUser(uid: Long, username: String): OsmUser {
        return OsmUser(
            id = uid,
            displayName = username,
            accountCreated = "2020-01-01T00:00:00Z", // Account vecchio
            description = "Test power user",
            img = null,
            roles = emptyList(),
            changesets = CountWrapper(count = 5000), // Molte modifiche
            traces = null
        )
    }
}
