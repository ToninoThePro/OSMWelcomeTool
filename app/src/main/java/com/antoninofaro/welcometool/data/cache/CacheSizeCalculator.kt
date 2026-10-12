package com.antoninofaro.welcometool.data.cache

import android.content.Context
import android.os.Process
import com.antoninofaro.welcometool.data.local.AppDatabase
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.WelcomedUserStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CacheSizesResult(
    val categorySizes: Map<String, Long>,
    val totalHttpCacheSize: Long,
    val roomDatabaseSize: Long,
    val dataStoreSize: Long,
    val userCount: Int = 0,
    val changesetCount: Int = 0,
    val userAreaActivityCount: Int = 0,
    val welcomedCount: Int = 0,
    val notifiedCount: Int = 0
)

@Singleton
class CacheSizeCalculator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val welcomedUserStorage: WelcomedUserStorage? = null,
    private val notifiedUserStorage: NotifiedUserStorage? = null
) {
    suspend fun calculateAllSizes(
        cacheRegistry: CacheRegistry
    ): CacheSizesResult = withContext(Dispatchers.IO.limitedParallelism(1)) {
        var originalPriority = -1
        try {
            originalPriority = Process.getThreadPriority(Process.myTid())
            Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
        } catch (e: Exception) {
            // Ignore: not allowed in some environments (e.g., tests)
            originalPriority = -1
        }

        try {
            val categorySizes = mutableMapOf<String, Long>()
            var totalHttpCacheSize = 0L

            cacheRegistry.getCategories().forEach { category ->
                val size = category.sizeBytes
                categorySizes[category.id] = size
                totalHttpCacheSize += size
            }

            // Room Database size estimation using PRAGMA page_count * page_size
            var roomDatabaseSize = 0L
            try {
                val cursor = appDatabase.openHelper.readableDatabase.query("PRAGMA page_count;")
                var pageCount = 0L
                if (cursor.moveToFirst()) {
                    pageCount = cursor.getLong(0)
                }
                cursor.close()

                val cursor2 = appDatabase.openHelper.readableDatabase.query("PRAGMA page_size;")
                var pageSize = 0L
                if (cursor2.moveToFirst()) {
                    pageSize = cursor2.getLong(0)
                }
                cursor2.close()

                roomDatabaseSize = pageCount * pageSize
            } catch (e: Exception) {
                // Fallback: check file size
                roomDatabaseSize = context.getDatabasePath("osm_welcome_database").length()
            }

            // Room entity counts
            var userCount = 0
            var changesetCount = 0
            var userAreaActivityCount = 0

            try {
                val cursorUsers = appDatabase.openHelper.readableDatabase.query("SELECT COUNT(*) FROM users")
                if (cursorUsers.moveToFirst()) {
                    userCount = cursorUsers.getInt(0)
                }
                cursorUsers.close()
            } catch (e: Exception) {
                // Ignore
            }

            try {
                val cursorChangesets = appDatabase.openHelper.readableDatabase.query("SELECT COUNT(*) FROM changesets")
                if (cursorChangesets.moveToFirst()) {
                    changesetCount = cursorChangesets.getInt(0)
                }
                cursorChangesets.close()
            } catch (e: Exception) {
                // Ignore
            }

            try {
                val cursorActivity = appDatabase.openHelper.readableDatabase.query("SELECT COUNT(*) FROM user_area_activity")
                if (cursorActivity.moveToFirst()) {
                    userAreaActivityCount = cursorActivity.getInt(0)
                }
                cursorActivity.close()
            } catch (e: Exception) {
                // Ignore
            }

            // DataStore size
            var dataStoreSize = 0L
            val dataStoreDir = File(context.filesDir, "datastore")
            val welcomedFile = File(dataStoreDir, "osm_welcomed_registry.preferences_pb")
            val notifiedFile = File(dataStoreDir, "osm_notified_registry.preferences_pb")

            if (welcomedFile.exists()) {
                dataStoreSize += welcomedFile.length()
            }
            if (notifiedFile.exists()) {
                dataStoreSize += notifiedFile.length()
            }

            // DataStore counts
            var welcomedCount = 0
            var notifiedCount = 0

            try {
                welcomedCount = welcomedUserStorage?.getAllWelcomedIds()?.size ?: 0
            } catch (e: Exception) {
                // Ignore
            }

            try {
                notifiedCount = notifiedUserStorage?.getAllNotifiedIds()?.size ?: 0
            } catch (e: Exception) {
                // Ignore
            }

            CacheSizesResult(
                categorySizes = categorySizes,
                totalHttpCacheSize = totalHttpCacheSize,
                roomDatabaseSize = roomDatabaseSize,
                dataStoreSize = dataStoreSize,
                userCount = userCount,
                changesetCount = changesetCount,
                userAreaActivityCount = userAreaActivityCount,
                welcomedCount = welcomedCount,
                notifiedCount = notifiedCount
            )
        } finally {
            if (originalPriority != -1) {
                try {
                    Process.setThreadPriority(originalPriority)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
}