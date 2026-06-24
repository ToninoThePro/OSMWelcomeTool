package com.antoninofaro.welcometool.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.entity.UserAreaActivityEntity
import com.antoninofaro.welcometool.data.local.dao.UserDao

@Database(
    entities = [UserEntity::class, UserAreaActivityEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

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