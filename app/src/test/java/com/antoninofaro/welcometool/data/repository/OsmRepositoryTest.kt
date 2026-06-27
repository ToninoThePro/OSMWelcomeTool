package com.antoninofaro.welcometool.data.repository

import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.OsmChangesetWrapper
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.data.model.OsmUserWrapper
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.network.OsmApiService
import com.antoninofaro.welcometool.utils.Constants
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

/**
 * Unit test per OsmRepository
 * Dimostra come testare il repository con Result type
 */
class OsmRepositoryTest {

    @Mock
    private lateinit var apiService: OsmApiService

    private lateinit var repository: OsmRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = OsmRepository(apiService)
    }

    @Test
    fun `fetchRecentChangesets returns success with data`() = runTest {
        // Given
        val mockChangesets = listOf(
            OsmChangeset(
                id = 1L,
                createdAt = "2026-02-21T10:00:00Z",
                closedAt = "2026-02-21T11:00:00Z",
                open = false,
                user = "TestUser",
                uid = 123L,
                minLat = 37.0,
                minLon = 13.0,
                maxLat = 38.0,
                maxLon = 14.0,
                numChanges = 10,
                commentsCount = 2,
                tags = mapOf("comment" to "Test changeset")
            )
        )
        val wrapper = OsmChangesetWrapper(mockChangesets)
        `when`(apiService.getRecentChangesets(Constants.ITALY_BBOX, null, null)).thenReturn(wrapper)

        // When
        val result = repository.fetchRecentChangesets()

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).hasSize(1)
        assertThat(result.getOrNull()?.first()?.id).isEqualTo(1L)
    }

    @Test
    fun `fetchRecentChangesets returns error on exception`() = runTest {
        // Given
        val exception = RuntimeException("Network error")
        `when`(apiService.getRecentChangesets(Constants.ITALY_BBOX, null, null))
            .thenThrow(exception)

        // When
        val result = repository.fetchRecentChangesets()

        // Then
        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat(result.isError).isTrue()
        assertThat(result.exceptionOrNull()).isEqualTo(exception)
        assertThat(result.getOrNull()).isNull()
    }

    @Test
    fun `fetchUserDetail returns success with user`() = runTest {
        // Given
        val mockUser = OsmUser(
            id = 123L,
            displayName = "TestUser",
            accountCreated = "2020-01-01T00:00:00Z",
            description = "Test user description",
            img = null,
            roles = emptyList(),
            changesets = null,
            traces = null
        )
        val wrapper = OsmUserWrapper(mockUser)
        `when`(apiService.getUserDetail(123L)).thenReturn(wrapper)

        // When
        val result = repository.fetchUserDetail(123L)

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(result.getOrNull()?.id).isEqualTo(123L)
        assertThat(result.getOrNull()?.displayName).isEqualTo("TestUser")
    }

    @Test
    fun `fetchUserChangesets returns sorted changesets`() = runTest {
        // Given
        val changesets = listOf(
            createMockChangeset(1L, "2026-02-20T10:00:00Z"),
            createMockChangeset(2L, "2026-02-21T10:00:00Z"),
            createMockChangeset(3L, "2026-02-19T10:00:00Z")
        )
        val wrapper = OsmChangesetWrapper(changesets)
        `when`(apiService.getUserChangesets(123L)).thenReturn(wrapper)

        // When
        val result = repository.fetchUserChangesets(123L)

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val sortedChangesets = result.getOrNull()!!
        assertThat(sortedChangesets).hasSize(3)
        // Verifica ordine decrescente
        assertThat(sortedChangesets[0].id).isEqualTo(2L)
        assertThat(sortedChangesets[1].id).isEqualTo(1L)
        assertThat(sortedChangesets[2].id).isEqualTo(3L)
    }

    @Test
    fun `result getOrDefault returns default on error`() = runTest {
        // Given
        `when`(apiService.getRecentChangesets(Constants.ITALY_BBOX, null, null))
            .thenThrow(RuntimeException())

        // When
        val result = repository.fetchRecentChangesets()

        // Then
        val changesets = result.getOrDefault(emptyList())
        assertThat(changesets).isEmpty()
    }

    private fun createMockChangeset(id: Long, createdAt: String): OsmChangeset {
        return OsmChangeset(
            id = id,
            createdAt = createdAt,
            closedAt = createdAt,
            open = false,
            user = "TestUser",
            uid = 123L,
            minLat = 37.0,
            minLon = 13.0,
            maxLat = 38.0,
            maxLon = 14.0,
            numChanges = 10,
            commentsCount = 0,
            tags = null
        )
    }

    @Test
    fun `searchUserByUsername returns success with user`() = runTest {
        // Given
        val username = "Antonino_Faro"
        val mockChangeset =
            createMockChangeset(1L, "2026-02-21T10:00:00Z").copy(uid = 123L, user = username)
        val changesetWrapper = OsmChangesetWrapper(listOf(mockChangeset))
        `when`(apiService.getChangesetsByUsername(username, 1)).thenReturn(changesetWrapper)

        val mockUser = OsmUser(
            id = 123L,
            displayName = username,
            accountCreated = "2020-01-01T00:00:00Z",
            description = "Test user",
            img = null,
            roles = emptyList(),
            changesets = null,
            traces = null
        )
        val userWrapper = OsmUserWrapper(mockUser)
        `when`(apiService.getUserDetail(123L)).thenReturn(userWrapper)

        // When
        val result = repository.searchUserByUsername(username)

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(result.getOrNull()?.displayName).isEqualTo(username)
        assertThat(result.getOrNull()?.id).isEqualTo(123L)
    }

    @Test
    fun `searchUserByUsername returns error when user not found`() = runTest {
        // Given
        val username = "NonExistentUser"
        val changesetWrapper = OsmChangesetWrapper(emptyList())
        `when`(apiService.getChangesetsByUsername(username, 1)).thenReturn(changesetWrapper)

        // When
        val result = repository.searchUserByUsername(username)

        // Then
        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat(result.isError).isTrue()
    }
}

