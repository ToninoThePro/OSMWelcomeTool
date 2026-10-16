package com.antoninofaro.welcometool.utils

import com.antoninofaro.welcometool.data.entity.ChangesetEntity
import com.antoninofaro.welcometool.data.entity.UserAreaActivityEntity
import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.local.model.UserAreaActivityWithUser
import com.antoninofaro.welcometool.data.model.ChangesetUiModel
import com.antoninofaro.welcometool.data.model.CountWrapper
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.data.model.UserImage
import com.antoninofaro.welcometool.domain.UserAnalysis
import com.antoninofaro.welcometool.ui.screens.UserUiModel
import kotlinx.serialization.json.Json

fun ChangesetEntity.toUiModel(isOnline: Boolean): ChangesetUiModel {
    val reasonsList = try {
        if (!osmchaReasonsJson.isNullOrBlank()) {
            Json.decodeFromString<List<String>>(osmchaReasonsJson)
        } else emptyList()
    } catch (_: Exception) {
        emptyList()
    }
    return ChangesetUiModel(
        entity = this,
        reasons = reasonsList,
        isOnline = isOnline
    )
}

fun UserEntity.toOsmUser(): OsmUser {
    return OsmUser(
        id = id,
        displayName = displayName,
        accountCreated = accountCreated,
        description = description,
        img = imgUrl?.let { UserImage(it) },
        roles = emptyList(),
        changesets = CountWrapper(totalEdits),
        traces = null,
        etag = etag,
        lastModified = lastModified
    )
}

fun UserEntity.toAnalysis(lastActiveDateOverride: String? = lastActiveDate): UserAnalysis {
    return UserAnalysis(
        accountAge = accountAge,
        isNewcomer = isNewcomer,
        isReturning = isReturning,
        isPowerUser = isPowerUser,
        isRegular = !isNewcomer && !isReturning && !isPowerUser,
        totalEdits = totalEdits,
        firstChangesetDate = firstChangesetDate,
        lastActiveDate = lastActiveDateOverride,
        osmchaLikes = osmchaLikes,
        osmchaDislikes = osmchaDislikes,
        isWelcomed = isWelcomed
    )
}

fun UserEntity.toUiModel(): UserUiModel {
    return UserUiModel(this.toOsmUser(), this.toAnalysis(), this.lastUpdated, this.osmchaLastChecked)
}

fun UserAreaActivityWithUser.toUiModel(): UserUiModel {
    val analysis = user.toAnalysis(lastActiveDateOverride = lastChangesetDate)
    return UserUiModel(
        user = user.toOsmUser(),
        analysis = analysis,
        lastUpdated = areaLastUpdated,
        osmchaLastChecked = user.osmchaLastChecked
    )
}

fun OsmUser.toEntity(analysis: UserAnalysis): UserEntity {
    return UserEntity(
        id = id,
        displayName = displayName,
        accountCreated = accountCreated,
        description = description,
        accountAge = analysis.accountAge,
        isNewcomer = analysis.isNewcomer,
        isReturning = analysis.isReturning,
        isPowerUser = analysis.isPowerUser,
        totalEdits = analysis.totalEdits,
        firstChangesetDate = analysis.firstChangesetDate,
        lastActiveDate = analysis.lastActiveDate,
        osmchaLikes = analysis.osmchaLikes,
        osmchaDislikes = analysis.osmchaDislikes,
        isWelcomed = analysis.isWelcomed,
        imgUrl = img?.href,
        etag = null, // Set in repo or dao
        lastModified = null
    )
}
