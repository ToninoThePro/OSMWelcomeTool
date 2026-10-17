package com.antoninofaro.welcometool.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.antoninofaro.welcometool.data.entity.AreaSyncStateEntity
import com.antoninofaro.welcometool.data.entity.ChangesetEntity
import com.antoninofaro.welcometool.data.entity.UserAreaActivityEntity
import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.local.dao.AreaSyncStateDao
import com.antoninofaro.welcometool.data.local.dao.ChangesetDao
import com.antoninofaro.welcometool.data.local.dao.UserDao

@Database(
    entities = [UserEntity::class, UserAreaActivityEntity::class, ChangesetEntity::class, AreaSyncStateEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun changesetDao(): ChangesetDao
    abstract fun areaSyncStateDao(): AreaSyncStateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN etag TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN last_modified TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `changesets` (`id` INTEGER NOT NULL, `bbox` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `closedAt` TEXT, `open` INTEGER NOT NULL, `user` TEXT NOT NULL, `uid` INTEGER NOT NULL, `minLat` REAL, `minLon` REAL, `maxLat` REAL, `maxLon` REAL, `numChanges` INTEGER NOT NULL, `commentsCount` INTEGER NOT NULL, `tagsJson` TEXT, `osmchaScore` INTEGER, `osmchaIsSuspect` INTEGER NOT NULL, `osmchaHarmful` INTEGER, `osmchaReasonsJson` TEXT, `createCount` INTEGER NOT NULL, `modifyCount` INTEGER NOT NULL, `deleteCount` INTEGER NOT NULL, `isOsmchaChecked` INTEGER NOT NULL, `stopTracking` INTEGER NOT NULL, `fetchSource` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL, `etag` TEXT, `last_modified` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `area_sync_state` (`bbox` TEXT NOT NULL, `forwardCursorId` INTEGER NOT NULL, `forwardCursorDate` TEXT NOT NULL, `backwardCursorDate` TEXT, `isHistoricalExhausted` INTEGER NOT NULL, `lastPhaseBTimeMs` INTEGER NOT NULL, `lastSyncStartTimeMs` INTEGER NOT NULL, PRIMARY KEY(`bbox`))")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "osm_welcome_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
