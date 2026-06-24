package com.antoninofaro.welcometool.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.entity.UserAreaActivityEntity
import com.antoninofaro.welcometool.data.local.model.UserAreaActivityWithUser

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAreaActivities(activities: List<UserAreaActivityEntity>)

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): UserEntity?

    @Query("SELECT * FROM users WHERE id IN (:uids)")
    suspend fun getUsersByIds(uids: List<Long>): List<UserEntity>

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