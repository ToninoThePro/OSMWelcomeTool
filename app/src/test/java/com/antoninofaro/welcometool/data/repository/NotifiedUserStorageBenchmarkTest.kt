package com.antoninofaro.welcometool.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.math.sqrt
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotifiedUserStorageBenchmarkTest {

    private lateinit var testContext: Context
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var scope: TestScope
    private lateinit var storage: NotifiedUserStorage

    @Before
    fun setup() {
        testContext = ApplicationProvider.getApplicationContext()
        scope = TestScope(UnconfinedTestDispatcher() + Job())
        testDataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { testContext.preferencesDataStoreFile("benchmark_notified_registry") }
        )
        storage = NotifiedUserStorage(testDataStore)
    }

    @After
    fun cleanup() = scope.runTest { storage.clearAll() }

    @Ignore("Benchmark test; run manually to avoid unit test flakiness")
    @Test
    fun benchmarkBatchVsSequential() = scope.runTest {
        val userCount = 500
        val warmup = 2
        val iterations = 6
        val ids = (1..userCount).map { it.toLong() }

        repeat(warmup) { runSequentialOnce(ids); runBatchOnce(ids) }

        val seq = mutableListOf<Long>()
        val bat = mutableListOf<Long>()
        repeat(iterations) { seq.add(runSequentialOnce(ids)); bat.add(runBatchOnce(ids)) }

        val s = statsOf(seq)
        val b = statsOf(bat)
        println("NotifiedUserStorage benchmark: users=$userCount, seq=${s.format()}, batch=${b.format()}, speedup=${String.format("%.2f", s.avgMs / b.avgMs)}x")
    }

    private suspend fun runSequentialOnce(ids: List<Long>): Long {
        storage.clearAll()
        val t = System.nanoTime()
        for (id in ids) storage.markAsNotified(id)
        return (System.nanoTime() - t) / 1_000_000
    }

    private suspend fun runBatchOnce(ids: List<Long>): Long {
        storage.clearAll()
        val t = System.nanoTime()
        storage.markAsNotifiedBatch(ids)
        return (System.nanoTime() - t) / 1_000_000
    }

    private fun statsOf(s: List<Long>) = s.sorted().let { sorted ->
        val avg = s.average()
        val variance = if (s.size >= 2) s.sumOf { (it - avg) * (it - avg) } / s.size else 0.0
        Stats(avg, sorted.first(), sorted.last(), percentile(sorted, 0.50), percentile(sorted, 0.95), sqrt(variance))
    }

    private fun percentile(s: List<Long>, p: Double) = if (s.isEmpty()) 0L else s[((s.size - 1) * p).toInt()]

    private data class Stats(val avgMs: Double, val minMs: Long, val maxMs: Long, val p50Ms: Long, val p95Ms: Long, val stdDevMs: Double) {
        fun format() = "avg=${String.format("%.2f", avgMs)}ms, min=${minMs}ms, max=${maxMs}ms, p50=${p50Ms}ms, p95=${p95Ms}ms, std=${String.format("%.2f", stdDevMs)}"
    }
}
