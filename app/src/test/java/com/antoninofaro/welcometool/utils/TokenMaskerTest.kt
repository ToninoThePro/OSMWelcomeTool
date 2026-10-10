package com.antoninofaro.welcometool.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TokenMaskerTest {

    @Test
    fun `mask returns 'empty' for null token`() {
        assertThat(TokenMasker.mask(null)).isEqualTo("empty")
    }

    @Test
    fun `mask returns 'empty' for empty token`() {
        assertThat(TokenMasker.mask("")).isEqualTo("empty")
    }

    @Test
    fun `mask returns 'empty' for blank token`() {
        assertThat(TokenMasker.mask("   ")).isEqualTo("empty")
    }

    @Test
    fun `mask returns stars for short token`() {
        assertThat(TokenMasker.mask("1234567")).isEqualTo("****")
        assertThat(TokenMasker.mask("12345678")).isEqualTo("****")
    }

    @Test
    fun `mask returns masked token for long token`() {
        assertThat(TokenMasker.mask("123456789")).isEqualTo("1234...6789")
        assertThat(TokenMasker.mask("1234567890abcdef")).isEqualTo("1234...cdef")
    }

    @Test
    fun `redactHeader redacts Authorization token header`() {
        val redacted = TokenMasker.redactHeader("Authorization", "Token 1234567890abcdef")
        assertThat(redacted).isEqualTo("Token 1234...cdef")
    }

    @Test
    fun `redactHeader redacts short Authorization token header`() {
        val redacted = TokenMasker.redactHeader("Authorization", "Token 1234")
        assertThat(redacted).isEqualTo("Token ****")
    }

    @Test
    fun `redactHeader is case insensitive for header name`() {
        val redacted = TokenMasker.redactHeader("authorization", "Token 1234567890abcdef")
        assertThat(redacted).isEqualTo("Token 1234...cdef")
    }

    @Test
    fun `redactHeader is case insensitive for Token prefix`() {
        val redacted = TokenMasker.redactHeader("Authorization", "token 1234567890abcdef")
        assertThat(redacted).isEqualTo("Token 1234...cdef")
    }

    @Test
    fun `redactHeader does not redact non-Authorization header`() {
        val value = "Token 1234567890abcdef"
        val redacted = TokenMasker.redactHeader("X-Custom-Header", value)
        assertThat(redacted).isEqualTo(value)
    }

    @Test
    fun `redactHeader does not redact Authorization header without Token prefix`() {
        val value = "Bearer 1234567890abcdef"
        val redacted = TokenMasker.redactHeader("Authorization", value)
        assertThat(redacted).isEqualTo(value)
    }
}
