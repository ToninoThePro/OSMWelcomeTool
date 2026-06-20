package com.antoninofaro.welcometool.domain

import com.antoninofaro.welcometool.data.model.OsmChangeset
import org.junit.Assert.assertEquals
import org.junit.Test

class ChangesetDedupTest {

    private fun cs(id: Long, uid: Long, createdAt: String) = OsmChangeset(
        id = id, uid = uid, createdAt = createdAt,
        closedAt = createdAt, open = false, user = "u$uid",
        minLat = 0.0, minLon = 0.0, maxLat = 0.0, maxLon = 0.0,
        numChanges = 1, commentsCount = 0, tags = null
    )

    @Test
    fun `reversed associateBy keeps newest changeset per uid`() {
        // API returns reverse-chronological (newest first)
        val changesets = listOf(
            cs(id = 3, uid = 1, createdAt = "2026-06-20T11:00:00Z"), // newest for uid 1
            cs(id = 2, uid = 1, createdAt = "2026-06-20T10:00:00Z"),
            cs(id = 1, uid = 1, createdAt = "2026-06-20T09:00:00Z"), // oldest for uid 1
            cs(id = 5, uid = 2, createdAt = "2026-06-20T10:30:00Z"), // newest for uid 2
            cs(id = 4, uid = 2, createdAt = "2026-06-20T08:00:00Z"), // oldest for uid 2
        )

        // BUG: associateBy without reversed keeps the OLDEST
        val bug = changesets.associateBy { it.uid }
        assertEquals(1L, bug[1]?.id)  // keeps oldest (id=1) — WRONG

        // FIX: reversed first keeps the NEWEST
        val fix = changesets.reversed().associateBy { it.uid }
        assertEquals(3L, fix[1]?.id)  // keeps newest (id=3) — CORRECT
        assertEquals(5L, fix[2]?.id)  // keeps newest (id=5) — CORRECT
    }
}
