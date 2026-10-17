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
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun changesetDao(): ChangesetDao
    abstract fun areaSyncStateDao(): AreaSyncStateDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE changesets ADD COLUMN stopTracking INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE changesets ADD COLUMN fetchSource TEXT NOT NULL DEFAULT 'unknown'")
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
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
