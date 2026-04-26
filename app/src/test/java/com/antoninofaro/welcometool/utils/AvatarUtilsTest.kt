package com.antoninofaro.welcometool.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AvatarUtilsTest {

    @Test
    fun `getGravatarUrl generates valid gravatar URL`() {
        val displayName = "TestUser"
        val size = 128

        val url = AvatarUtils.getGravatarUrl(displayName, size)

        assertThat(url).startsWith("https://www.gravatar.com/avatar/")
        assertThat(url).contains("s=128")
        assertThat(url).contains("d=identicon")
        assertThat(url).contains("r=pg")
    }

    @Test
    fun `getGravatarUrl with different sizes`() {
        val displayName = "TestUser"

        val url50 = AvatarUtils.getGravatarUrl(displayName, 50)
        val url200 = AvatarUtils.getGravatarUrl(displayName, 200)

        assertThat(url50).contains("s=50")
        assertThat(url200).contains("s=200")
    }

    @Test
    fun `getProfileImageUrl returns osm image when available`() {
        val osmImageUrl = "https://example.com/image.jpg"
        val displayName = "TestUser"

        val url = AvatarUtils.getProfileImageUrl(osmImageUrl, displayName)

        assertThat(url).isEqualTo(osmImageUrl)
    }

    @Test
    fun `getProfileImageUrl returns gravatar when osm image is null`() {
        val displayName = "TestUser"

        val url = AvatarUtils.getProfileImageUrl(null, displayName)

        assertThat(url).startsWith("https://www.gravatar.com/avatar/")
    }

    @Test
    fun `getProfileImageUrl returns gravatar when osm image is empty`() {
        val displayName = "TestUser"

        val url = AvatarUtils.getProfileImageUrl("", displayName)

        assertThat(url).startsWith("https://www.gravatar.com/avatar/")
    }

    @Test
    fun `getGravatarUrl generates consistent hash for same username`() {
        val displayName = "ConsistentUser"

        val url1 = AvatarUtils.getGravatarUrl(displayName)
        val url2 = AvatarUtils.getGravatarUrl(displayName)

        assertThat(url1).isEqualTo(url2)
    }

    @Test
    fun `getGravatarUrl generates different hashes for different usernames`() {
        val url1 = AvatarUtils.getGravatarUrl("User1")
        val url2 = AvatarUtils.getGravatarUrl("User2")

        assertThat(url1).isNotEqualTo(url2)
    }

    @Test
    fun `getGravatarUrl generates correct MD5 hash according to Gravatar spec`() {
        // Test case: Known email should produce expected MD5 hash
        // Email: "test@openstreetmap.org" -> MD5: "3eb2279441ebc03f5aab33533936787b"
        val displayName = "test"

        val url = AvatarUtils.getGravatarUrl(displayName)

        // Verify the URL contains the correct MD5 hash for this email
        assertThat(url).contains("3eb2279441ebc03f5aab33533936787b")
        // Verify the complete URL format
        assertThat(url).isEqualTo("https://www.gravatar.com/avatar/3eb2279441ebc03f5aab33533936787b?s=128&d=identicon&r=pg")
    }

    @Test
    fun `getGravatarUrl handles email normalization correctly`() {
        // Gravatar requires lowercase and trimmed emails
        // " TEST "@openstreetmap.org should produce same hash as "test@openstreetmap.org"
        val url1 = AvatarUtils.getGravatarUrl(" TEST ")
        val url2 = AvatarUtils.getGravatarUrl("test")

        // Both should produce the same hash after normalization
        assertThat(url1).isEqualTo(url2)
    }
}
