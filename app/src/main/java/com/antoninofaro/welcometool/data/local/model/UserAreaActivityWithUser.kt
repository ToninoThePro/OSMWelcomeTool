package com.antoninofaro.welcometool.data.local.model

import androidx.room.Embedded
import com.antoninofaro.welcometool.data.entity.UserEntity

data class UserAreaActivityWithUser(
	@Embedded val user: UserEntity,
	val bbox: String,
	val lastChangesetDate: String,
	val lastChangesetId: Long,
	val areaLastUpdated: Long
)

