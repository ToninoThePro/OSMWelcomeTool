package com.antoninofaro.welcometool.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.antoninofaro.welcometool.data.entity.UserAreaActivityEntity
import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.local.model.UserAreaActivityWithUser

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAreaActivity(activity: UserAreaActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAreaActivities(activities: List<UserAreaActivityEntity>)

    @Query("SELECT * FROM user_area_activity WHERE bbox = :bbox AND userId IN (:userIds)")
    suspend fun getUserAreaActivities(bbox: String, userIds: Set<Long>): List<UserAreaActivityEntity>

    @Transaction
    open suspend fun insertOrUpdateUserAreaActivities(activities: List<UserAreaActivityEntity>) {
        if (activities.isEmpty()) return
        
        // Group by bbox to handle cases where activities might belong to different bboxes
        activities.groupBy { it.bbox }.forEach { (bbox, bboxActivities) ->
            val userIds = bboxActivities.map { it.userId }.toSet()
            val existingMap = getUserAreaActivities(bbox, userIds).associateBy { it.userId }
            
            val toInsert = bboxActivities.filter { newActivity ->
                val existing = existingMap[newActivity.userId]
                if (existing != null) {
                    // Solo se la nuova attività è più recente (string comparison su date ISO8601 va bene)
                    newActivity.lastChangesetDate > existing.lastChangesetDate
                } else {
                    true
                }
            }
            if (toInsert.isNotEmpty()) {
                insertUserAreaActivities(toInsert)
            }
        }
    }

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): UserEntity?

    @Query("SELECT * FROM users WHERE id IN (:uids)")
    suspend fun getUsersByIds(uids: Set<Long>): List<UserEntity>

    @Query(
        """
        SELECT u.*, a.bbox AS bbox, a.lastChangesetDate AS lastChangesetDate,
               a.lastChangesetId AS lastChangesetId, a.lastUpdated AS areaLastUpdated
        FROM users u
        INNER JOIN user_area_activity a ON u.id = a.userId
        WHERE a.bbox = :bbox
        ORDER BY a.lastChangesetDate DESC, a.lastChangesetId DESC, a.lastUpdated DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getUsersForBBoxPage(
        bbox: String,
        limit: Int,
        offset: Int
    ): List<UserAreaActivityWithUser>

    @Query(
        """
        SELECT u.*, a.bbox AS bbox, a.lastChangesetDate AS lastChangesetDate,
               a.lastChangesetId AS lastChangesetId, a.lastUpdated AS areaLastUpdated
        FROM users u
        INNER JOIN user_area_activity a ON u.id = a.userId
        WHERE a.bbox = :bbox 
          AND (:searchTerm = '' OR u.displayName LIKE '%' || :searchTerm || '%' ESCAPE '\')
          AND u.totalEdits >= :minEdits
          AND (
            (:filterNewcomer = 0 AND :filterReturning = 0 AND :filterPowerUser = 0) OR
            (:filterNewcomer = 1 AND u.isNewcomer = 1) OR
            (:filterReturning = 1 AND u.isReturning = 1) OR
            (:filterPowerUser = 1 AND u.isPowerUser = 1)
          )
        ORDER BY a.lastChangesetDate DESC, a.lastChangesetId DESC, a.lastUpdated DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getUsersForBBoxFiltered(
        bbox: String,
        searchTerm: String,
        minEdits: Int,
        filterNewcomer: Boolean,
        filterReturning: Boolean,
        filterPowerUser: Boolean,
        limit: Int,
        offset: Int
    ): List<UserAreaActivityWithUser>

    @Query(
        """
        SELECT u.*, a.bbox AS bbox, a.lastChangesetDate AS lastChangesetDate,
               a.lastChangesetId AS lastChangesetId, a.lastUpdated AS areaLastUpdated
        FROM users u
        INNER JOIN user_area_activity a ON u.id = a.userId
        WHERE a.bbox = :bbox 
          AND (:searchTerm = '' OR u.displayName LIKE '%' || :searchTerm || '%' ESCAPE '\')
          AND u.totalEdits >= :minEdits
          AND (
            (:filterNewcomer = 0 AND :filterReturning = 0 AND :filterPowerUser = 0) OR
            (:filterNewcomer = 1 AND u.isNewcomer = 1) OR
            (:filterReturning = 1 AND u.isReturning = 1) OR
            (:filterPowerUser = 1 AND u.isPowerUser = 1)
          )
        ORDER BY a.lastChangesetDate DESC, a.lastChangesetId DESC, a.lastUpdated DESC
        """
    )
    abstract fun getUsersForBBoxPagingSource(
        bbox: String,
        searchTerm: String = "",
        minEdits: Int = 0,
        filterNewcomer: Boolean = false,
        filterReturning: Boolean = false,
        filterPowerUser: Boolean = false
    ): PagingSource<Int, UserAreaActivityWithUser>

    @Query(
        """
        SELECT u.*, a.bbox AS bbox, a.lastChangesetDate AS lastChangesetDate,
               a.lastChangesetId AS lastChangesetId, a.lastUpdated AS areaLastUpdated
        FROM users u
        INNER JOIN user_area_activity a ON u.id = a.userId
        WHERE a.bbox = :bbox AND u.displayName LIKE :searchTerm ESCAPE '\\'
        ORDER BY a.lastChangesetDate DESC, a.lastChangesetId DESC, a.lastUpdated DESC
        """
    )
    suspend fun searchUsersForBBox(
        bbox: String,
        searchTerm: String
    ): List<UserAreaActivityWithUser>

    @Query("UPDATE users SET isWelcomed = :isWelcomed, lastUpdated = :updatedAt WHERE id = :userId")
    suspend fun updateWelcomedStatus(
        userId: Long,
        isWelcomed: Boolean,
        updatedAt: Long
    )

    @Query("UPDATE users SET osmchaLikes = :likes, osmchaDislikes = :dislikes, osmchaLastChecked = :lastChecked, lastUpdated = :lastUpdated WHERE id = :userId")
    suspend fun updateOsmchaStats(
        userId: Long,
        likes: Int,
        dislikes: Int,
        lastChecked: Long,
        lastUpdated: Long
    )
}