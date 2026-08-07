package com.examshield.scanner

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkStatus(
    val isMobileDataActive: Boolean,
    val isWifiActive: Boolean,
    val networkType: String,
    val signalStrength: Int,
    val hasInternet: Boolean
)

class NetworkMonitor(private val context: Context) {

    private val TAG = "NetworkMonitor"

    private val connectivityManager = context.getSystemService(
        Context.CONNECTIVITY_SERVICE
    ) as ConnectivityManager

    private val telephonyManager = context.getSystemService(
        Context.TELEPHONY_SERVICE
    ) as TelephonyManager

    private val _networkStatus = MutableStateFlow(
        NetworkStatus(
            isMobileDataActive = false,
            isWifiActive = false,
            networkType = "None",
            signalStrength = 0,
            hasInternet = false
        )
    )
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun startMonitoring() {
        Log.d(TAG, "Starting network monitoring")

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateStatus()
            }

            override fun onLost(network: Network) {
                updateStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                updateStatus()
            }
        }

        connectivityManager.registerNetworkCallback(
            request,
            networkCallback!!
        )

        updateStatus()
    }

    private fun updateStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager
            .getNetworkCapabilities(activeNetwork)

        val hasWifi = capabilities?.hasTransport(
            NetworkCapabilities.TRANSPORT_WIFI
        ) ?: false

        val hasCellular = capabilities?.hasTransport(
            NetworkCapabilities.TRANSPORT_CELLULAR
        ) ?: false

        val hasInternet = capabilities?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) ?: false

        val networkType = when {
            hasWifi -> "WiFi"
            hasCellular -> getMobileNetworkType()
            else -> "None"
        }

        _networkStatus.value = NetworkStatus(
            isMobileDataActive = hasCellular && hasInternet,
            isWifiActive = hasWifi,
            networkType = networkType,
            signalStrength = 0,
            hasInternet = hasInternet
        )

        Log.d(TAG, "Network: $networkType | Data: $hasCellular")
    }

    private fun getMobileNetworkType(): String {
        return try {
            when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
                else -> "Mobile"
            }
        } catch (e: Exception) {
            "Mobile"
        }
    }

    fun stopMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "Unregister error", e)
            }
        }
    }
}
