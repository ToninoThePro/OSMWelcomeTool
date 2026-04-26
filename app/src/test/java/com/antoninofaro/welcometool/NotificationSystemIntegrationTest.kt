package com.antoninofaro.welcometool

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import com.google.common.truth.Truth.assertThat
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.zip
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

/**
 * Test di integrazione per il sistema di notifiche
 * Simula scenari reali di utilizzo end-to-end
 */
@RunWith(RobolectricTestRunner::class)
class NotificationSystemIntegrationTest {

    private lateinit var context: Context
    private lateinit var notifiedStorage: NotifiedUserStorage

    private lateinit var testScope: TestScope
    private lateinit var notifiedDataStore: DataStore<Preferences>
    private lateinit var settingsDataStore: DataStore<Preferences>

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope(UnconfinedTestDispatcher() + Job())

        // Configurazione isolata per NotifiedUserStorage
        notifiedDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { context.preferencesDataStoreFile("test_notified_integration_${UUID.randomUUID()}") }
        )
        notifiedStorage = NotifiedUserStorage(context)

        // Configurazione isolata per SettingsRepository
        settingsDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { context.preferencesDataStoreFile("test_settings_integration_${UUID.randomUUID()}") }
        )
    }

    @After
    fun cleanup() = runTest {
        notifiedStorage.clearAll()
    }

    @Test
    fun testCompleteNotificationFlow() = runTest {
        // Scenario: Un nuovo mapper fa delle modifiche in Sicilia

        // 1. All'inizio, nessun utente è stato notificato
        assertThat(notifiedStorage.getAllNotifiedIds()).isEmpty()

        // 2. Arriva il primo mapper (ID: 1001)
        val mapper1Id = 1001L
        assertThat(notifiedStorage.isNotified(mapper1Id)).isFalse()

        // 3. Il sistema rileva il nuovo mapper e invia una notifica
        notifiedStorage.markAsNotified(mapper1Id)

        // 4. Il mapper è ora nella lista dei notificati
        assertThat(notifiedStorage.isNotified(mapper1Id)).isTrue()
        assertThat(notifiedStorage.getAllNotifiedIds()).containsExactly("1001")

        // 5. Il mapper fa altre modifiche (secondo changeset)
        // Il sistema NON dovrebbe inviare un'altra notifica
        val shouldNotify = !notifiedStorage.isNotified(mapper1Id)
        assertThat(shouldNotify).isFalse()

        // 6. Arriva un secondo mapper (ID: 1002)
        val mapper2Id = 1002L
        assertThat(notifiedStorage.isNotified(mapper2Id)).isFalse()
        notifiedStorage.markAsNotified(mapper2Id)

        // 7. Ora abbiamo due mapper notificati
        assertThat(notifiedStorage.getAllNotifiedIds()).hasSize(2)
        assertThat(notifiedStorage.getAllNotifiedIds()).containsExactly("1001", "1002")
    }

    @Test
    fun testMultipleDaysScenario() = runTest {
        // Scenario: Monitoriamo i mapper su più giorni

        // Giorno 1: Arrivano 3 nuovi mapper
        val day1Mappers = listOf(100L, 101L, 102L)
        day1Mappers.forEach { notifiedStorage.markAsNotified(it) }

        assertThat(notifiedStorage.getAllNotifiedIds()).hasSize(3)

        // Giorno 2: Gli stessi mapper fanno altre modifiche + 2 nuovi
        // I vecchi non devono essere notificati di nuovo
        val day2NewMappers = listOf(200L, 201L)

        for (mapperId in day1Mappers) {
            assertThat(notifiedStorage.isNotified(mapperId)).isTrue()
        }

        for (mapperId in day2NewMappers) {
            assertThat(notifiedStorage.isNotified(mapperId)).isFalse()
            notifiedStorage.markAsNotified(mapperId)
        }

        // Totale: 5 mapper notificati
        assertThat(notifiedStorage.getAllNotifiedIds()).hasSize(5)

        // Giorno 3: Verifichiamo che tutti siano ancora nella lista
        val allMappers = day1Mappers + day2NewMappers
        for (mapperId in allMappers) {
            assertThat(notifiedStorage.isNotified(mapperId)).isTrue()
        }
    }

    @Test
    fun testHighVolumeScenario() = runTest {
        // Scenario: Molti mapper in un breve periodo

        val mapperCount = 50
        val mapperIds = (1..mapperCount).map { it.toLong() }

        // Simuliamo l'arrivo di 50 mapper
        for (mapperId in mapperIds) {
            // Controlliamo prima se è già notificato
            if (!notifiedStorage.isNotified(mapperId)) {
                // Non notificato, inviamo notifica
                notifiedStorage.markAsNotified(mapperId)
            }
        }

        // Verifichiamo che tutti siano stati registrati
        assertThat(notifiedStorage.getAllNotifiedIds()).hasSize(mapperCount)

        // Simuliamo un secondo controllo (dovrebbe essere un no-op)
        var duplicateNotifications = 0
        for (mapperId in mapperIds) {
            if (!notifiedStorage.isNotified(mapperId)) {
                duplicateNotifications++
            }
        }

        // Non dovremmo avere notifiche duplicate
        assertThat(duplicateNotifications).isEqualTo(0)
    }

    @Test
    fun testResetScenario() = runTest {
        // Scenario: Reset del sistema (ad esempio, reinstallazione app)

        // Aggiungiamo alcuni mapper
        notifiedStorage.markAsNotified(1L)
        notifiedStorage.markAsNotified(2L)
        notifiedStorage.markAsNotified(3L)

        assertThat(notifiedStorage.getAllNotifiedIds()).hasSize(3)

        // L'utente decide di resettare il sistema
        notifiedStorage.clearAll()

        // Tutti i dati dovrebbero essere cancellati
        assertThat(notifiedStorage.getAllNotifiedIds()).isEmpty()
        assertThat(notifiedStorage.isNotified(1L)).isFalse()
        assertThat(notifiedStorage.isNotified(2L)).isFalse()
        assertThat(notifiedStorage.isNotified(3L)).isFalse()

        // Possiamo ricominciare da capo
        notifiedStorage.markAsNotified(100L)
        assertThat(notifiedStorage.getAllNotifiedIds()).containsExactly("100")
    }

    @Test
    fun testMixedUserTypesScenario() = runTest {
        // Scenario: Mapper con caratteristiche diverse

        // Mapper che dovrebbero essere notificati (newcomers)
        val newcomers = listOf(1L, 2L, 3L)

        // Mapper che NON dovrebbero essere notificati (power users, returning)
        // In questo test simuliamo solo la logica di storage
        val nonNotifiableUsers = listOf(100L, 101L, 102L)

        // Notifichiamo solo i newcomers
        newcomers.forEach { notifiedStorage.markAsNotified(it) }

        // Verifichiamo che solo i newcomers siano stati notificati
        assertThat(notifiedStorage.getAllNotifiedIds()).hasSize(newcomers.size)

        newcomers.forEach { id ->
            assertThat(notifiedStorage.isNotified(id)).isTrue()
        }

        nonNotifiableUsers.forEach { id ->
            assertThat(notifiedStorage.isNotified(id)).isFalse()
        }
    }

    @Test
    fun testConcurrentAccessScenario() = runTest {
        // Scenario: Accessi concorrenti (simulato)

        val mapperId = 999L

        // Primo accesso: verifica e notifica
        if (!notifiedStorage.isNotified(mapperId)) {
            notifiedStorage.markAsNotified(mapperId)
        }

        // Secondo accesso quasi simultaneo: dovrebbe vedere che è già notificato
        val alreadyNotified = notifiedStorage.isNotified(mapperId)
        assertThat(alreadyNotified).isTrue()

        // Non dovrebbe aggiungere di nuovo
        if (!alreadyNotified) {
            notifiedStorage.markAsNotified(mapperId)
        }

        // Verifichiamo che ci sia un solo record
        val count = notifiedStorage.getAllNotifiedIds().count { it == "999" }
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun testLongTermUsage() = runTest {
        // Scenario: Utilizzo a lungo termine con molti mapper

        val totalMappers = 200
        val batchSize = 50

        // Aggiungiamo mapper in batch (simulando settimane di utilizzo)
        for (batch in 0 until (totalMappers / batchSize)) {
            val startId = batch * batchSize + 1
            val endId = startId + batchSize - 1

            for (id in startId..endId) {
                notifiedStorage.markAsNotified(id.toLong())
            }

            // Dopo ogni batch, verifichiamo il conteggio
            val expectedCount = (batch + 1) * batchSize
            assertThat(notifiedStorage.getAllNotifiedIds()).hasSize(expectedCount)
        }

        // Verifica finale
        assertThat(notifiedStorage.getAllNotifiedIds()).hasSize(totalMappers)

        // Verifichiamo alcuni ID random
        assertThat(notifiedStorage.isNotified(1L)).isTrue()
        assertThat(notifiedStorage.isNotified(100L)).isTrue()
        assertThat(notifiedStorage.isNotified(200L)).isTrue()
        assertThat(notifiedStorage.isNotified(201L)).isFalse()
    }
}
