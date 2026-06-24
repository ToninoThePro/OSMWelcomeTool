package com.antoninofaro.welcometool.data.model

import timber.log.Timber

/**
 * Sealed class per rappresentare il risultato di operazioni asincrone
 */
sealed class Result<T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String? = exception.message) :
        Result<Nothing>()

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrDefault(defaultValue: T): T = when (this) {
        is Success -> data
        else -> defaultValue
    }

    fun exceptionOrNull(): Exception? = when (this) {
        is Error -> exception
        else -> null
    }
}

/**
 * Extension function per eseguire un blocco in sicurezza e wrappare il risultato
 */
suspend inline fun <T> safeApiCall(crossinline apiCall: suspend () -> T): Result<T> {
    return try {
        Result.Success(apiCall())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        @Suppress("UNCHECKED_CAST")
        Result.Error(e) as Result<T>
    }
}

/**
 * Extension function per eseguire un blocco se il risultato è Success
 */
inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

/**
 * Extension function per eseguire un blocco se il risultato è Error
 */
inline fun <T> Result<T>.onError(action: (Exception) -> Unit): Result<T> {
    if (this is Result.Error) action(exception)
    return this
}

/**
 * Extension function per loggare il risultato di un'operazione
 * @param errorMsg Messaggio da loggare in caso di errore
 * @param successMsg Funzione che genera il messaggio da loggare in caso di successo
 */
inline fun <T> Result<T>.log(
    errorMsg: String,
    successMsg: (T) -> String
): Result<T> {
    return this.onSuccess {
        Timber.d(successMsg(it))
    }.onError {
        Timber.e(it, errorMsg)
    }
}

