package com.antoninofaro.welcometool.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.antoninofaro.welcometool.data.entity.AreaSyncStateEntity

@Dao
interface AreaSyncStateDao {
    @Query("SELECT * FROM area_sync_state WHERE bbox = :bbox")
    suspend fun getSyncState(bbox: String): AreaSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: AreaSyncStateEntity)
    
    @Query("UPDATE area_sync_state SET isHistoricalExhausted = :exhausted WHERE bbox = :bbox")
    suspend fun updateIsHistoricalExhausted(bbox: String, exhausted: Boolean)
}
