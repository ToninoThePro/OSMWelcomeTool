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
    private lateinit var testScope: TestScope
    private lateinit var storage: NotifiedUserStorage

    @Before
    fun setup() {
        testContext = ApplicationProvider.getApplicationContext()
        testScope = TestScope(UnconfinedTestDispatcher() + Job())

        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testContext.preferencesDataStoreFile("benchmark_notified_registry") }
        )

        storage = NotifiedUserStorage(testContext)
    }

    @After
    fun cleanup() = runTest {
        storage.clearAll()
    }

    @Ignore("Benchmark test; run manually to avoid unit test flakiness")
    @Test
    fun benchmarkBatchVsSequential() = runTest {
        val userCount = 500
        val warmupIterations = 2
        val measuredIterations = 6
        val userIds = (1..userCount).map { it.toLong() }

        repeat(warmupIterations) {
            runSequentialOnce(userIds)
            runBatchOnce(userIds)
        }

        val sequentialSamples = mutableListOf<Long>()
        val batchSamples = mutableListOf<Long>()

        repeat(measuredIterations) {
            sequentialSamples.add(runSequentialOnce(userIds))
            batchSamples.add(runBatchOnce(userIds))
        }

        val sequentialStats = statsOf(sequentialSamples)
        val batchStats = statsOf(batchSamples)
        val speedup = sequentialStats.avgMs / batchStats.avgMs

        println("NotifiedUserStorage benchmark")
        println("users=$userCount, warmup=$warmupIterations, iterations=$measuredIterations")
        println("sequential: ${sequentialStats.format()}")
        println("batch: ${batchStats.format()}")
        println("speedup(avg)=${String.format("%.2f", speedup)}x")
    }

    private suspend fun runSequentialOnce(userIds: List<Long>): Long {
        storage.clearAll()
        val startNs = System.nanoTime()
        for (id in userIds) {
            storage.markAsNotified(id)
        }
        return nanosToMillis(System.nanoTime() - startNs)
    }

    private suspend fun runBatchOnce(userIds: List<Long>): Long {
        storage.clearAll()
        val startNs = System.nanoTime()
        storage.markAsNotifiedBatch(userIds)
        return nanosToMillis(System.nanoTime() - startNs)
    }

    private fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000

    private fun statsOf(samplesMs: List<Long>): Stats {
        val sorted = samplesMs.sorted()
        val avg = samplesMs.average()
        val min = sorted.first()
        val max = sorted.last()
        val p50 = percentile(sorted, 0.50)
        val p95 = percentile(sorted, 0.95)
        val std = stdDev(samplesMs, avg)
        return Stats(avgMs = avg, minMs = min, maxMs = max, p50Ms = p50, p95Ms = p95, stdDevMs = std)
    }

    private fun percentile(sortedSamples: List<Long>, p: Double): Long {
        if (sortedSamples.isEmpty()) return 0L
        val index = ((sortedSamples.size - 1) * p).toInt()
        return sortedSamples[index]
    }

    private fun stdDev(samplesMs: List<Long>, avg: Double): Double {
        if (samplesMs.size < 2) return 0.0
        val variance = samplesMs.sumOf { val diff = it - avg; diff * diff } / samplesMs.size
        return sqrt(variance)
    }

    private data class Stats(
        val avgMs: Double,
        val minMs: Long,
        val maxMs: Long,
        val p50Ms: Long,
        val p95Ms: Long,
        val stdDevMs: Double
    ) {
        fun format(): String {
            return "avg=${String.format("%.2f", avgMs)}ms, " +
                "min=${minMs}ms, max=${maxMs}ms, " +
                "p50=${p50Ms}ms, p95=${p95Ms}ms, " +
                "std=${String.format("%.2f", stdDevMs)}"
        }
    }
}
