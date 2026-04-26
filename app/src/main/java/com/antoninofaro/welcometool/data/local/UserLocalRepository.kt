package com.antoninofaro.welcometool.data.local

import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.entity.UserAreaActivityEntity
import com.antoninofaro.welcometool.data.local.dao.UserDao
import com.antoninofaro.welcometool.data.local.model.UserAreaActivityWithUser
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class UserLocalRepository @Inject constructor(
    private val userDao: UserDao
) {
    suspend fun saveUsers(users: List<UserEntity>) = userDao.insertUsers(users)

    suspend fun saveUserAreaActivities(activities: List<UserAreaActivityEntity>) =
        userDao.insertUserAreaActivities(activities)

    suspend fun saveUser(user: UserEntity) = userDao.insertUser(user)

    suspend fun getUserById(userId: Long): UserEntity? = userDao.getUserById(userId)

    suspend fun getUserIdsForBBox(bbox: String): List<Long> = userDao.getUserIdsForBBox(bbox)

    suspend fun getUserCountForBBox(bbox: String): Int = userDao.getUserCountForBBox(bbox)

    suspend fun getUsersForBBoxPage(
        bbox: String,
        limit: Int,
        offset: Int
    ): List<UserAreaActivityWithUser> = userDao.getUsersForBBoxPage(bbox, limit, offset)

    suspend fun searchUsersForBBox(
        bbox: String,
        searchTerm: String
    ): List<UserAreaActivityWithUser> = userDao.searchUsersForBBox(bbox, searchTerm)

    fun getAllUsers(): Flow<List<UserEntity>> = userDao.observeAllUsers()

    suspend fun getAllUsersSnapshot(): List<UserEntity> = userDao.getAllUsers()

    suspend fun searchUsersByName(searchTerm: String): List<UserEntity> =
        userDao.searchUsers("%$searchTerm%")

    suspend fun deleteAllUsers() = userDao.deleteAllUsers()

    suspend fun getUserCount(): Int = userDao.getUserCount()

    suspend fun updateWelcomedStatus(userId: Long, isWelcomed: Boolean) =
        userDao.updateWelcomedStatus(userId, isWelcomed, System.currentTimeMillis())
}
