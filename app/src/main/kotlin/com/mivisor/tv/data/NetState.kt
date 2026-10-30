package com.mivisor.tv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock

/**
 * Estado de la red del aparato (solo lectura, ACCESS_NETWORK_STATE). La escucha de cambios solo está activa con la
 * app en primer plano ([start]/[stop] desde la Activity): sirve para no culpar a una fuente de un corte de red.
 */
object NetState {
    @Volatile private var lastChange = 0L
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** ¿Tiene la TV Internet validado? null = no se sabe (entonces no se asume que no). */
    fun online(context: Context): Boolean? = runCatching {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrNull()

    fun vpnActive(context: Context): Boolean? = runCatching {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }.getOrNull()

    /** Segundos desde el último cambio de red visto con la app abierta (null si no hubo). */
    fun secondsSinceChange(): Double? = lastChange.takeIf { it > 0 }?.let { (SystemClock.elapsedRealtime() - it) / 1000.0 }

    fun start(context: Context) {
        if (callback != null) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { lastChange = SystemClock.elapsedRealtime() }
            override fun onLost(network: Network) { lastChange = SystemClock.elapsedRealtime() }
        }
        runCatching {
            cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb)
            callback = cb
        }
    }

    fun stop(context: Context) {
        val cb = callback ?: return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        runCatching { cm?.unregisterNetworkCallback(cb) }
        callback = null
    }
}
