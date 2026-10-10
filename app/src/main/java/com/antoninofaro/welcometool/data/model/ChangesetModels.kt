package com.antoninofaro.welcometool.data.model

import com.antoninofaro.welcometool.data.entity.ChangesetEntity

data class ChangesetUiModel(
    val entity: ChangesetEntity,
    val reasons: List<String> = emptyList(),
    val isOnline: Boolean = true
)

data class ChangedElement(
    val type: String, // node, way, relation
    val id: Long,
    val action: String, // create, modify, delete
    val tags: Map<String, String>
)

data class ChangedElementGroup(
    val creates: List<ChangedElement> = emptyList(),
    val modifies: List<ChangedElement> = emptyList(),
    val deletes: List<ChangedElement> = emptyList()
)
