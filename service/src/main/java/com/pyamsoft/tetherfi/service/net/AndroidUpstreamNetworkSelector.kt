package com.pyamsoft.tetherfi.service.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.CheckResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AndroidUpstreamNetworkSelector @Inject constructor(
    private val context: Context,
    private val cm: ConnectivityManager,
) : UpstreamNetworkSelector {

    // Guardamos referencias a los callbacks para poder desregistrarlos en release()
    private var wifiCb: ConnectivityManager.NetworkCallback? = null
    private var cellCb: ConnectivityManager.NetworkCallback? = null

    override suspend fun acquire(
        preferred: UpstreamPref,
        fallback: UpstreamPref,
    ): Network {
        // 1. INTENTO PRINCIPAL
        Timber.d("Solicitando red upstream preferida: $preferred")

        val primaryResult = runCatching { tryAcquire(preferred) }
        val primaryNetwork = primaryResult.getOrNull()

        if (primaryNetwork != null) {
            Timber.d("ÉXITO: Red upstream adquirida ($preferred): $primaryNetwork")
            return primaryNetwork
        }

        // 2. REPORTE DE FALLO
        val error = primaryResult.exceptionOrNull()
        if (error != null) {
            Timber.w(error, "ADVERTENCIA: Falla adquisición de $preferred. Intentando fallback...")
        } else {
            Timber.w("ADVERTENCIA: No se pudo adquirir $preferred. Intentando fallback...")
        }

        // 3. INTENTO DE FALLBACK (RESPALDO)
        Timber.d("Intentando red de respaldo: $fallback")
        val fallbackResult = runCatching { tryAcquire(fallback) }
        val fallbackNetwork = fallbackResult.getOrNull()

        if (fallbackNetwork != null) {
            Timber.d("ÉXITO: Red de respaldo adquirida ($fallback): $fallbackNetwork")
            return fallbackNetwork
        }

        // 4. ERROR FATAL
        val finalMsg =
            "CRÍTICO: No se pudo obtener conexión a Internet. " +
                    "Falló $preferred y también falló $fallback. Verifica que tengas señal."
        fallbackResult.exceptionOrNull()?.let { Timber.e(it, finalMsg) } ?: Timber.e(finalMsg)
        throw IllegalStateException(finalMsg)
    }

    /**
     * Lógica interna para solicitar la red Android.
     * Usa corrutinas para esperar asincrónicamente a que el sistema nos proporcione red.
     */
    @CheckResult
    private suspend fun tryAcquire(pref: UpstreamPref): Network? {
        // Timeout de 6 segundos para no dejar al usuario esperando eternamente
        return withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine { cont ->

                // Construimos el request según la preferencia
                val builder = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

                when (pref) {
                    UpstreamPref.WIFI -> {
                        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        // En Android 12+ podemos pedir ciertas capacidades extra si se requiere
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                        }
                    }

                    UpstreamPref.CELL -> {
                        builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    }
                }

                val request = builder.build()

                // Callback que recibe la respuesta del sistema
                val cb = object : ConnectivityManager.NetworkCallback() {

                    override fun onAvailable(network: Network) {
                        if (cont.isActive) {
                            Timber.d("Red disponible: $network para preferencia $pref")
                            cont.resume(network)
                        }
                    }

                    override fun onUnavailable() {
                        if (cont.isActive) {
                            Timber.w("Red no disponible para preferencia $pref")
                            cont.resume(null)
                        }
                    }

                    override fun onLost(network: Network) {
                        // Opcional: manejar pérdida de red en tiempo real
                        Timber.w("Red perdida: $network")
                    }
                }

                // Guardamos el callback en la variable de clase correspondiente para limpiarlo luego.
                if (pref == UpstreamPref.WIFI) {
                    wifiCb?.let { runCatching { cm.unregisterNetworkCallback(it) } }
                    wifiCb = cb
                } else {
                    cellCb?.let { runCatching { cm.unregisterNetworkCallback(it) } }
                    cellCb = cb
                }

                // Solicitamos red al sistema
                Timber.d("Ejecutando requestNetwork para $pref")
                try {
                    cm.requestNetwork(request, cb)
                } catch (e: SecurityException) {
                    Timber.e(e, "Error de permisos al solicitar red")
                    if (cont.isActive) cont.resume(null)
                } catch (e: Exception) {
                    Timber.e(e, "Error desconocido al solicitar red")
                    if (cont.isActive) cont.resume(null)
                }

                // Si la corrutina se cancela (ej. timeout), la limpieza fina se hace en release()
                cont.invokeOnCancellation {
                    // Podríamos limpiar aquí también, pero de momento
                    // dejamos la responsabilidad principal a release()
                }
            }
        }
    }

    override fun release() {
        Timber.d("Liberando selectores de red upstream")

        wifiCb?.let {
            runCatching { cm.unregisterNetworkCallback(it) }
        }
        cellCb?.let {
            runCatching { cm.unregisterNetworkCallback(it) }
        }

        wifiCb = null
        cellCb = null
    }
}
