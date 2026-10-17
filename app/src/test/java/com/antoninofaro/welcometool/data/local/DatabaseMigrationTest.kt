package com.antoninofaro.welcometool.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    @Test
    fun migrate1To2_preservesExistingDataAndCreatesNewTables() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config =
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name("test_migration.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER NOT NULL, `displayName` TEXT NOT NULL, `accountCreated` TEXT NOT NULL, `description` TEXT, `accountAge` INTEGER NOT NULL, `isNewcomer` INTEGER NOT NULL, `isReturning` INTEGER NOT NULL, `isPowerUser` INTEGER NOT NULL, `totalEdits` INTEGER NOT NULL, `firstChangesetDate` TEXT, `lastActiveDate` TEXT, `osmchaLikes` INTEGER NOT NULL, `osmchaDislikes` INTEGER NOT NULL, `osmchaLastChecked` INTEGER NOT NULL, `isWelcomed` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, `imgUrl` TEXT, PRIMARY KEY(`id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `user_area_activity` (`bbox` TEXT NOT NULL, `userId` INTEGER NOT NULL, `lastChangesetDate` TEXT NOT NULL, `lastChangesetId` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`bbox`, `userId`))"
                        )
                        db.execSQL(
                            "INSERT INTO users VALUES (123, 'TestUser', '2026-01-01', 'Desc', 10, 1, 0, 0, 5, NULL, NULL, 0, 0, 0, 0, 1000, NULL)"
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                    }
                })
                .build()

        val openHelper = factory.create(config)
        val db = openHelper.writableDatabase

        // Apply MIGRATION_1_2
        AppDatabase.MIGRATION_1_2.migrate(db)

        // Verify users data is preserved and new columns exist
        val cursor =
            db.query("SELECT id, displayName, etag, last_modified FROM users WHERE id = 123")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getLong(0)).isEqualTo(123L)
        assertThat(cursor.getString(1)).isEqualTo("TestUser")
        assertThat(cursor.isNull(2)).isTrue() // etag is null by default
        assertThat(cursor.isNull(3)).isTrue() // last_modified is null by default
        cursor.close()

        // Verify new tables changesets and area_sync_state exist
        val changesetsCursor = db.query("SELECT COUNT(*) FROM changesets")
        assertThat(changesetsCursor.moveToFirst()).isTrue()
        assertThat(changesetsCursor.getInt(0)).isEqualTo(0)
        changesetsCursor.close()

        val areaSyncCursor = db.query("SELECT COUNT(*) FROM area_sync_state")
        assertThat(areaSyncCursor.moveToFirst()).isTrue()
        assertThat(areaSyncCursor.getInt(0)).isEqualTo(0)
        areaSyncCursor.close()

        db.close()
    }
}
