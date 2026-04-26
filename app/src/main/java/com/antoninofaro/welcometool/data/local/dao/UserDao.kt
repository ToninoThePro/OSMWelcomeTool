package com.antoninofaro.welcometool.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.entity.UserAreaActivityEntity
import com.antoninofaro.welcometool.data.local.model.UserAreaActivityWithUser
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT userId FROM user_area_activity WHERE bbox = :bbox")
    suspend fun getUserIdsForBBox(bbox: String): List<Long>

    @Query("SELECT COUNT(*) FROM user_area_activity WHERE bbox = :bbox")
    suspend fun getUserCountForBBox(bbox: String): Int

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
        WHERE a.bbox = :bbox AND u.displayName LIKE :searchTerm
        ORDER BY a.lastChangesetDate DESC, a.lastChangesetId DESC, a.lastUpdated DESC
        """
    )
    suspend fun searchUsersForBBox(
        bbox: String,
        searchTerm: String
    ): List<UserAreaActivityWithUser>

    @Query("SELECT * FROM users ORDER BY lastUpdated DESC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY lastUpdated DESC")
    suspend fun getAllUsers(): List<UserEntity>

    @Query("SELECT * FROM users WHERE displayName LIKE :searchTerm")
    suspend fun searchUsers(searchTerm: String): List<UserEntity>

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("DELETE FROM user_area_activity")
    suspend fun deleteAllUserAreaActivities()

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("UPDATE users SET isWelcomed = :isWelcomed, lastUpdated = :updatedAt WHERE id = :userId")
    suspend fun updateWelcomedStatus(
        userId: Long,
        isWelcomed: Boolean,
        updatedAt: Long
    )
}