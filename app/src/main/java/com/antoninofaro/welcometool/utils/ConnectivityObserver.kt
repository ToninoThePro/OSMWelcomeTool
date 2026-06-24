package com.antoninofaro.welcometool.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val isOnline: StateFlow<Boolean> = observeConnectivity()
        .stateIn(
            scope = kotlinx.coroutines.GlobalScope,
            started = SharingStarted.Eagerly,
            initialValue = checkCurrentConnectivity()
        )

    private fun checkCurrentConnectivity(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun observeConnectivity(): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                try { trySend(true) } catch (_: CancellationException) {}
            }
            override fun onLost(network: Network) {
                try { trySend(false) } catch (_: CancellationException) {}
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                try { trySend(connected) } catch (_: CancellationException) {}
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }
}
