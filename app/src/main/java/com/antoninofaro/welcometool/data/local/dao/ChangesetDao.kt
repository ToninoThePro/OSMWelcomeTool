package com.antoninofaro.welcometool.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.antoninofaro.welcometool.data.entity.ChangesetEntity

@Dao
abstract class ChangesetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertChangeset(changeset: ChangesetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertChangesets(changesets: List<ChangesetEntity>)

    @Transaction
    open suspend fun insertOrUpdateChangesets(changesets: List<ChangesetEntity>) {
        changesets.forEach { entity ->
            val existing = getChangesetById(entity.id)
            if (existing != null) {
                val merged = entity.copy(
                    createCount = if (existing.createCount > 0) existing.createCount else entity.createCount,
                    modifyCount = if (existing.modifyCount > 0) existing.modifyCount else entity.modifyCount,
                    deleteCount = if (existing.deleteCount > 0) existing.deleteCount else entity.deleteCount,
                    isOsmchaChecked = existing.isOsmchaChecked || entity.isOsmchaChecked,
                    osmchaScore = existing.osmchaScore ?: entity.osmchaScore,
                    osmchaIsSuspect = existing.osmchaIsSuspect || entity.osmchaIsSuspect,
                    osmchaHarmful = existing.osmchaHarmful ?: entity.osmchaHarmful,
                    osmchaReasonsJson = existing.osmchaReasonsJson ?: entity.osmchaReasonsJson
                )
                insertChangeset(merged)
            } else {
                insertChangeset(entity)
            }
        }
    }

    @Query("SELECT * FROM changesets WHERE id = :changesetId")
    abstract suspend fun getChangesetById(changesetId: Long): ChangesetEntity?

    @Query(
        """
        SELECT * FROM changesets 
        WHERE bbox = :bbox 
        ORDER BY createdAt DESC 
        LIMIT :limit OFFSET :offset
        """
    )
    abstract suspend fun getChangesetsForBBoxPage(bbox: String, limit: Int, offset: Int): List<ChangesetEntity>

    @Query(
        """
        SELECT * FROM changesets 
        WHERE bbox = :bbox 
          AND (:searchTerm = '' OR tagsJson LIKE '%' || :searchTerm || '%' ESCAPE '\' OR user LIKE '%' || :searchTerm || '%' ESCAPE '\' OR id LIKE '%' || :searchTerm || '%' ESCAPE '\')
          AND (:filterReviewRequested = 0 OR tagsJson LIKE '%"review_requested":"yes"%')
          AND (:filterAnomalies = 0 OR osmchaIsSuspect = 1 OR osmchaHarmful = 1)
        ORDER BY createdAt DESC 
        LIMIT :limit OFFSET :offset
        """
    )
    abstract suspend fun getChangesetsForBBoxFiltered(
        bbox: String,
        searchTerm: String,
        filterReviewRequested: Boolean,
        filterAnomalies: Boolean,
        limit: Int,
        offset: Int
    ): List<ChangesetEntity>

    @Query(
        """
        UPDATE changesets 
        SET osmchaScore = :score, osmchaIsSuspect = :isSuspect, osmchaHarmful = :harmful, 
            osmchaReasonsJson = :reasonsJson, createCount = :createCount, 
            modifyCount = :modifyCount, deleteCount = :deleteCount, isOsmchaChecked = 1,
            lastUpdated = :lastUpdated
        WHERE id = :changesetId
        """
    )
    abstract suspend fun updateOsmchaDetails(
        changesetId: Long,
        score: Int?,
        isSuspect: Boolean,
        harmful: Boolean?,
        reasonsJson: String?,
        createCount: Int,
        modifyCount: Int,
        deleteCount: Int,
        lastUpdated: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE changesets 
        SET createCount = :createCount, modifyCount = :modifyCount, deleteCount = :deleteCount
        WHERE id = :changesetId
        """
    )
    abstract suspend fun updateDiffCounts(
        changesetId: Long,
        createCount: Int,
        modifyCount: Int,
        deleteCount: Int
    )
}
