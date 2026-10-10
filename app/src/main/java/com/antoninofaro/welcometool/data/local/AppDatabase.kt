package com.antoninofaro.welcometool.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.antoninofaro.welcometool.data.entity.UserAreaActivityEntity
import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.local.dao.UserDao

import com.antoninofaro.welcometool.data.entity.ChangesetEntity
import com.antoninofaro.welcometool.data.local.dao.ChangesetDao

@Database(
    entities = [UserEntity::class, UserAreaActivityEntity::class, ChangesetEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun changesetDao(): ChangesetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "osm_welcome_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}