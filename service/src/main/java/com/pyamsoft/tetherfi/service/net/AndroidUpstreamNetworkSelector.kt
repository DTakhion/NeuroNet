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
import timber.log.Timber // Asumiendo que el proyecto usa Timber, si no, usa android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AndroidUpstreamNetworkSelector @Inject constructor(
    private val context: Context,
    private val cm: ConnectivityManager
) : UpstreamNetworkSelector {

    // Guardamos referencias a los callbacks para poder desregistrarlos en release()
    private var wifiCb: ConectivityManager.NetworkCallBack? = null
    private var cellCb: ConnectivityManager.NetworkCallback? = null

    override suspend fun acquire(preferred: UpstreamPref, fallback: UpstreamPref): Network {
        // 1. INTENTO PRINCIPAL
        Timber.d("Solicitando red upstream preferida: $preferred")

        // Usamos runCatching para encapsular errores inesperados
        val primaryResult = runCatching { tryAcquire(preferred)}

        //Si tuvimos xito y la red no es nula, la retomamos
        val primaryNetwork = primaryResult.getOrNull()
        if (primaryResult.isSuccess && primaryNetwork != null) {
            Timber.d("XITO:Redupstreamadquirida($preferred): $primaryNetwork")
            returnprimaryNetwork
        }

        // 2. REPORTE DE FALLO
        val errror = primaryResult.exceptionOrNull()
        Timber.w(error,"ADVERTENCIA: Falllaadquisicinde $preferred.Intentando fallback...")

        //3.INTENTO DE FALLBACK(RESPALDO)
        Timber.d("Intentandoredde respaldo: $fallback")
        val fallbackResult = runCatching{tryAcquire(fallback)}

        val fallbackNetwork = fallbackResult.getOrNull()
        if (fallbackNetwork.isSuccess && fallbackNetwork != null){
            Timber.d("XITO:Redderespaldoadquirida($fallback): $fallbackNetwork")
            returnfallbackNetwork
        }

        //4.ERROR FATAL
        //Si llegamos aqui,ambas redes fallaron. Lanzamos excepcin para detener el servicio.
        val finalMsg = "CRTICO:Nose pudoobtenerconexinaInternet." +
                "Fall $preferredytambin fall $fallback.Verificaquetengasseal."
        Timber.e(finalMsg)
        throw IllegalStateException(finalMsg)
    }

    /**
     * Lógica interna para solicitar la red Android.
     * Usa Corrutinas para esperar asincronicamente a que el sistema nos proporcione red.
     */
    @CheckResult
    private suspend fun tryAcquire(pref: UpstreamPref) : Network? {
        // Definamos un timeout de 6 segundos para no dejar al usuario esperado eternsmente
        return withTimeoutOrNull(6000L) {
            suspendCancellableCoroutine { cont ->

                // Construimos el request según lo que pide el PDF [cite: 61-66]
                val builder = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

                when (pref) {
                    UpstreamPref.WIFI -> {
                        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        //En Android 12+ podemos pedir exclusividad para mejorar rendimiento.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                        }
                    }
                    UpstreamPref.CELL-> {
                        builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    }
                }
                val  request = builder.build()

                //Creamos el callback que recibe la respuesta del sistema [cite:67-71]
                val  cb = object : ConnectivityManager.NetworkCallBack() {
                    override fun onAvailable(network: Network) {
                        // IMPORTANTE: Verificar si la corrutina sigue activa para evitar crashes
                        if (cont.isActive) {
                            Timber.d("Red disponibe: $network para preferencia $pref")
                            cont.resume(network)
                        }
                    }

                    override fun  onUnavailable() {
                        if (cont.isActive) {
                            Timber.w("Red no disponible para preferencia $pref")
                            cont.resume(null)
                        }
                    }

                    override fun onLost(network: Network) {
                        //Opcional: Manejar perdida de red si fuera necesario en tiempo real
                        Timber.w("Red perdida: $network")
                    }
                }

                // Guardamos el callback en la variable de clase correspondiente para limpiarlo luego.
                if (pref == UpstreamPref.WIFI) {
                    //Limpiamos el anterior si existe
                    wifiCb?.let {runCatching{cm.unregisterNetworkCallback(it)}}
                    wifiCb = cb
                } else {
                    cellCb?.let {runCatching{cm.unregisterNetworkCallback(it)}}
                    cellCb = cb
                }

                // Solicitamos red al sistema
                Timber.d("Ejecutando requestNetwork para $pref")
                try {
                    cm.requestNetwork(request, cb)
                } catch (e:SecurityException) {
                    Timber.e(e, "Error de permisos al solicitar red")
                    if (cont.isActive) cont.resume(null)
                } catch (e:Exception) {
                    Timber.e(e, "Error desconocido al solicitar red")
                    if (cont.isActive) cont.resume(null)
                }

                // Si la corrutina se cancela (ej. timeout), limpiamos
                cont.invokeOnCancellation {
                    // No desregistremos aqui inmediatamente para permitir reintentos rápidos
                    // la limpieza se hace en release()
                }
            }
        }
    }

    override  fun release() {
        Timber.d("Liverando selectores de red upstream")

        //Limpieza segura de callbacks [cire: 73-75]
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