package com.antoninofaro.welcometool.data.cache

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.antoninofaro.welcometool.data.local.AppDatabase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class CacheSizeCalculatorTest {

    private lateinit var calculator: CacheSizeCalculator
    private lateinit var mockContext: Context
    private lateinit var mockAppDatabase: AppDatabase
    private lateinit var mockCacheRegistry: CacheRegistry
    private lateinit var mockOpenHelper: SupportSQLiteOpenHelper
    private lateinit var mockDatabase: SupportSQLiteDatabase

    @Before
    fun setup() {
        mockContext = mockk()
        mockAppDatabase = mockk()
        mockCacheRegistry = mockk()
        mockOpenHelper = mockk()
        mockDatabase = mockk()

        every { mockAppDatabase.openHelper } returns mockOpenHelper
        every { mockOpenHelper.readableDatabase } returns mockDatabase

        calculator = CacheSizeCalculator(mockContext, mockAppDatabase)
    }

    @Test
    fun `calculateAllSizes calculates correctly`() = runTest {
        // Setup mock files for datastore
        val tempDir = createTempDir("datastore_test")
        val datastoreDir = File(tempDir, "datastore").apply { mkdirs() }
        val welcomedFile = File(datastoreDir, "osm_welcomed_registry.preferences_pb").apply { writeText("welcomed data") }
        val notifiedFile = File(datastoreDir, "osm_notified_registry.preferences_pb").apply { writeText("notified data") }

        every { mockContext.filesDir } returns tempDir

        // Mock Cache Categories
        val cat1 = mockk<CacheCategory>()
        every { cat1.id } returns "osm_api"
        every { cat1.sizeBytes } returns 1000L

        val cat2 = mockk<CacheCategory>()
        every { cat2.id } returns "osmcha"
        every { cat2.sizeBytes } returns 2000L

        every { mockCacheRegistry.getCategories() } returns listOf(cat1, cat2)

        // Mock database queries for PRAGMA
        val cursorPageCount = mockk<android.database.Cursor>()
        every { mockDatabase.query("PRAGMA page_count;") } returns cursorPageCount
        every { cursorPageCount.moveToFirst() } returns true
        every { cursorPageCount.getLong(0) } returns 10L
        every { cursorPageCount.close() } returns Unit

        val cursorPageSize = mockk<android.database.Cursor>()
        every { mockDatabase.query("PRAGMA page_size;") } returns cursorPageSize
        every { cursorPageSize.moveToFirst() } returns true
        every { cursorPageSize.getLong(0) } returns 4096L
        every { cursorPageSize.close() } returns Unit

        // Execute
        val result = calculator.calculateAllSizes(mockCacheRegistry)

        // Verify
        assertEquals(3000L, result.totalHttpCacheSize)
        assertEquals(1000L, result.categorySizes["osm_api"])
        assertEquals(2000L, result.categorySizes["osmcha"])
        assertEquals(10L * 4096L, result.roomDatabaseSize)
        assertEquals(welcomedFile.length() + notifiedFile.length(), result.dataStoreSize)

        tempDir.deleteRecursively()
    }

    private fun createTempDir(prefix: String): File {
        return File.createTempFile(prefix, null).apply { delete(); mkdirs() }
    }
}