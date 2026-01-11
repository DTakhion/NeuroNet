package com.pyamsoft.tetherfi.service

import android.app.Service
import android.content.Intent
import android.net.Network
import android.os.IBinder
import com.pyamsoft.tetherfi.server.ProxyPreferences
import com.pyamsoft.tetherfi.server.ProxyRole
import com.pyamsoft.tetherfi.server.widi.WifiSharedProxy
import com.pyamsoft.tetherfi.service.net.UpstreamNetworkSelector
import com.pyamsoft.tetherfi.service.net.UpstreamPref
import com.pyamsoft.tetherfi.service.notification.NotificationLauncher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber

@AndroidEntryPoint
class ProxyForegroundService : Service() {

    // INYECCIÓN DE DEPENDENCIAS

    @Inject
    lateinit var upstreamSelector: UpstreamNetworkSelector

    @Inject
    lateinit var wifiSharedProxy: WifiSharedProxy

    @Inject
    lateinit var notificationLauncher: NotificationLauncher

    @Inject
    lateinit var proxyPreferences: ProxyPreferences

    // Scope para corrutinas del service
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)


    // CICLO DE VIDA DEL SERVICIO

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("Creando ProxyForegroundService")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Timber.w("Intent es null en onStartCommand")
            return START_STICKY
        }

        val action = intent.action
        Timber.d("onStartCommand acción: $action")

        when (action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
            else -> Timber.w("Acción desconocida: $action")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("Destruyendo ProxyForegroundService")

        scope.cancel()
        upstreamSelector.release()
        wifiSharedProxy.stop()
    }


    // LÓGICA PRINCIPAL

    private fun handleStop() {
        Timber.d("Solicitud de detención recibida")
        stopForeground(true)
        stopSelf()
    }

    private fun handleStart() {
        Timber.d("Solicitud de inicio recibida. Arrancando...")

        notificationLauncher.startForeground(this)

        scope.launch {
            try {
                startProxyFlow()
            } catch (e: Throwable) {
                Timber.e(e, "Error fatal en flujo de inicio")
                handleFatalError(e)
            }
        }
    }

    private suspend fun startProxyFlow() {

        notificationLauncher.update(
            title = "TetherFi",
            description = "Buscando conexión a Internet...",
            isError = false,
        )

        Timber.d("Intentando adquirir red upstream...")

        val role = proxyPreferences.listenForProxyRoleChanges().first()
        val isRelay = role == ProxyRole.RELAY
        val isClient = role == ProxyRole.CLIENT_ONLY

        if (isClient) {
            notificationLauncher.update(
                title = "Modo cliente",
                description = "Este dispositivo no actúa como repetidor. Se detiene el servicio.",
                isError = false,
            )
            stopSelf()
            return
        }

        // Reintentar WiFi hasta 3 veces (puede interrumpirse temporalmente al activar WiFi Direct)
        var upstreamNetwork: Network? = null
        var wifiAttempts = 0
        val maxWifiRetries = 3
        
        while (upstreamNetwork == null && wifiAttempts < maxWifiRetries) {
            wifiAttempts++
            Timber.d("Intento WiFi #$wifiAttempts de $maxWifiRetries...")
            
            upstreamNetwork = upstreamSelector.acquire(
                preferred = UpstreamPref.WIFI,
                fallback = if (isRelay) null else if (wifiAttempts >= maxWifiRetries) UpstreamPref.CELL else null
            )
            
            if (upstreamNetwork == null && wifiAttempts < maxWifiRetries) {
                Timber.w("WiFi no disponible aún, reintentando en 2 segundos...")
                delay(2000)
            }
        }
        
        if (upstreamNetwork == null) {
            if (isRelay) {
                Timber.w("No se pudo adquirir WiFi en modo repetidor; forzamos rol CLIENTE")
                proxyPreferences.setProxyRole(ProxyRole.CLIENT_ONLY)
                proxyPreferences.setUpstreamProxyEnabled(false)
                notificationLauncher.update(
                    title = "Modo cliente aplicado",
                    description = "El dispositivo no puede repetir (hotspot + Wi‑Fi). Conéctate como cliente.",
                    isError = true,
                )
                stopSelf()
                return
            }

            throw IllegalStateException("No se pudo adquirir ninguna red después de $wifiAttempts intentos")
        }

        Timber.d("Red adquirida exitosamente: $upstreamNetwork")

        val upstreamSocketFactory = upstreamNetwork.socketFactory

        notificationLauncher.update(
            title = "TetherFi",
            description = "Iniciando servidor proxy...",
            isError = false,
        )

        wifiSharedProxy.start(
            port = 8228,
            upstreamSocketFactory = upstreamSocketFactory,
        )

        Timber.d("Proxy iniciado y enroutando tráfico a través de $upstreamNetwork")

        notificationLauncher.update(
            title = "TetherFi Activo",
            description = "Conexión establecida. Hotspot listo.",
            isError = false,
        )
    }

    private fun handleFatalError(error: Throwable) {
        Timber.e(error, "Error fatal al iniciar el proxy")

        notificationLauncher.update(
            title = "Error de TetherFi",
            description = error.message ?: "Error desconocido",
            isError = true,
        )

        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.pyamsoft.tetherfi.service.ACTION_START"
        const val ACTION_STOP = "com.pyamsoft.tetherfi.service.ACTION_STOP"
    }
}