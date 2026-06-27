package com.antoninofaro.welcometool.data.model

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SafeApiCallTest {

    @Test
    fun `successful call returns Result Success`() = runTest {
        val result = safeApiCall { "hello" }

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo("hello")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.isError).isFalse()
        assertThat(result.getOrNull()).isEqualTo("hello")
        assertThat(result.getOrDefault("fallback")).isEqualTo("hello")
    }

    @Test
    fun `exception is wrapped in Result Error`() = runTest {
        val result = safeApiCall { throw IllegalStateException("boom") }

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat((result as Result.Error).exception).hasMessageThat().isEqualTo("boom")
        assertThat(result.isError).isTrue()
        assertThat(result.isSuccess).isFalse()
        assertThat((result as Result.Error).exception).isNotNull()
    }

    @Test(expected = CancellationException::class)
    fun `CancellationException is rethrown`() = runTest {
        safeApiCall { throw CancellationException("cancelled") }
    }

    @Test
    fun `Error message is captured from exception`() = runTest {
        val result = safeApiCall { throw RuntimeException("custom message") }

        assertThat((result as Result.Error).message).isEqualTo("custom message")
    }

    @Test
    fun `onSuccess callback is invoked for Success`() = runTest {
        val safeResult = safeApiCall { 42 }
        var invoked = false
        safeResult.onSuccess { invoked = true; assertThat(it).isEqualTo(42) }
        assertThat(invoked).isTrue()
    }

    @Test
    fun `onError callback is not invoked for Success`() = runTest {
        val safeResult = safeApiCall { 42 }
        var invoked = false
        safeResult.onError { invoked = true }
        assertThat(invoked).isFalse()
    }

    @Test
    fun `onError callback is invoked for Error`() = runTest {
        val safeResult = safeApiCall { throw RuntimeException("fail") }
        var invoked = false
        safeResult.onError { invoked = true; assertThat(it.message).isEqualTo("fail") }
        assertThat(invoked).isTrue()
    }

    @Test
    fun `onSuccess callback is not invoked for Error`() = runTest {
        val safeResult = safeApiCall { throw RuntimeException("fail") }
        var invoked = false
        safeResult.onSuccess { invoked = true }
        assertThat(invoked).isFalse()
    }
}
