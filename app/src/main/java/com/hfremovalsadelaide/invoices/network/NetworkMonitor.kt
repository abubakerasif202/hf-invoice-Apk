package com.hfremovalsadelaide.invoices.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes device network availability so the UI can present immediate offline status
 * and recover cleanly once connectivity returns.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            _isConnected.value = checkCurrentConnectivity()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            _isConnected.value = networkCapabilities.hasValidatedInternet()
        }
    }

    fun start() {
        _isConnected.value = checkCurrentConnectivity()
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }
    }

    fun stop() {
        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    fun refreshNow(): Boolean {
        val current = checkCurrentConnectivity()
        _isConnected.value = current
        return current
    }

    private fun checkCurrentConnectivity(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasValidatedInternet()
    }

    private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
