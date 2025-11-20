package com.pyamsoft.tetherfi.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.pyamsoft.tetherfi.server.widi.WifiSharedProxy
import com.pyamsoft.tetherfi.service.net.UpstreamNetworkSelector
import com.pyamsoft.tetherfi.service.net.UpstreamPref
import com.pyamsoft.tetherfi.service.notification.NotificationLauncher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ProxyForegroundService : Service() {


    // INYECCION DE DEPNDENCIAS


    // 1. El nuevo selector de red que creamos en el paso anterior
    @Inject
    lateint var upstreamSelector: UpstreamNetworkSelector

    // 2. El proxy Wi-Fi existente (se modificará en el siguiente paso)
    @Inject
    lateinit var wifiSharedProxy: WifiSharedProxy

    // 3. Gestor de notificaciones (para mostrar estado al usuario)
    @Inject
    lateinit var notificationLauncher: NotificationLauncher

    // Scope para manejar las corrutinas de servicio
    // Usamos SupervisorJob para que un fallo en una tarea hija no mate todo el scope
    private  val  scope = Coroutine(SupervisorJob() + Dispatchers.Main)


    // CICLO DE VIDA DEL SERVICIO


    override fun onBind(intent: Intent?): IBinder? {
        // Este servicio no permite binding, solo funciona como Foreground Service
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("Creando ProxyForegroundService")
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if (intent == null){
            Timber.w("Intent es null en onStartCommand")
            return START_STICKY
        }

        // Asumimos que el intent trae una acción (START o STOP)
        val  action = intent.action
        Timber.d("onStartCommandaccin: $action")

        when (action) {
            Object { ACTION_START -> handleStart() } // Android studio me hizo cambiar esto, ya que dijo que era código java y lo convirtio automaticamente a Kotlin (p 6)
            Object { ACTION_STOP->handleStop() }
            else->Timber.w("Acción desconocida recibida: $action")
        }

        //START_STICKY le dice al sistema que recree el servicio si lo mata por memoria.
        return  START_STICKY
    }

    override fun onDestroy(){
        super.onDestroy()
        Timber.d("Destruyendo ProxyForegroundService")

        // 1. Detener cualquier operación asincrona en curso
        scope.cancel()

        // 2. Liberar los recursos de red (callbacks de ConnectivityManager)
        upstreamSelector.release()

        // 3. Detener el proxy si estaba corriendo
        wifiSharedProxy.stop()
    }


    // LÓGICA PRINCIPAL (Start / Stop)


    private fun handleStop() {
        Timber.d("Solicitud de detencóin recibida")
        stopForeground(true)
        stopSelf()
    }

    private fun handleStart() {
        Timber.d("Solicitud de inicio recibida. Comenzando secuencia de arranque...")

        // Iniciamos el servicio en primer plano inmediatamente para que Android no nos mate
        notificationLauncher.startForeground(this)

        // Lanzamos la corrutina para la configuración para la configuración asincrona (Red -> Proxy)
        scope.laucnh {
            try {
                startProxyFlow()
            } catch(e:Throwable) {
                Timber.e(e,"Error no capturado en el flujo de inicio")
                handleFatalError(e)
            }
        }
    }

    /**
     *  Flujo principal de inicio con manejo de errores y selección de red.
     */
    private  suspend fun startProxyFlow() {
        // Paso 1: Notificar al usuario que estamos buscando red.
        notificationLauncher.update(
            title = "TetherFi",
            description = "Buscando conexión a Internet..."
            isError = false
        )

        // Paso 2: Adquirir la red upstream (Wi-Fi o Celular)
        //Esta llamada suspende la ejecución hasta que tengamos internet o timeout
        Timber.d("Intentando adquirir red upstream...")

        // TODO: Aqui podrás leer las preferencias del usuario para saber si prefiere WIFI o CELL
        // Por ahora, hardcodeamos WIFI como preferido, con fallback a CELL.
        val upstreamNetwork = upstreamSelector.acquire(
            preferred = UpstreamPref.WIFI,
            fallback = UpstreamPref.CELL
        )

        Timber.d("Red adquirida exitosamente: $upstreamNetwork")

        // Paso 3: Obtener el SocketFactory nativo de esa red
        // Esto es crucial: todo socket creado con este factory saldrá por esa red específica.
        val upstreamSocketFactory = upstreamNetwork.socketFactory

        // Paso 4: Iniciar el Proxy Server inyectando el SocketFactory
        notificationLauncher.update(
            title = "TetherFi",
            description = "Iniciando servidor proxy..."
            isError = false
        )

        // ATENCIÓN: Esto dará error hasta que se modifique WifiSharedProxy (Parte 4)
        wifiSharedProxy.start(
            //Estos valores suelen venir de la configuración (settings), aqui pongo por ejemplo:
            port = 8228,
            // bindAddress = ..., // Si tu versión de WifiSharedProxy lo requiere
            upstreamSocketFactory = upstreamSocketFactory // <--- INYECCIÖN NUEVA
        )
        //-----------------------------------------------------------------------------------------------------------------------------

        Timber.d("Proxy inidciado y enroutando tráfico a través de $upstreamNetwork")

        notificationLauncher.update(
            title = "TetherFi Activo",
            description = "Conexión establecida. Hotspot listo.",
            isError = false
        )
    }

    /**
     * Manejo centralizado de errores fatales.
     * Detiene el servicio y avisa al usuario.
     */
    private fun handleFatalError(error: Throwable) {
        Timber.e(error, "Error fatal iniciando el proxy")

        // Actualizar notificación a estado de error (rojo/alerta)
        notificationLauncher.update(
            title = "Error de TetherFi",
            description = error.messaje ?: "Error desconocido al iniciar",
            isError = true
        )

        //Detener servicio limpiamente
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.pyamsoft.tetherfi.service.ACTION_START"
        const val  ACTION_STOP = "com.pyamsoft.tetherfi.service.ACTION_STOP"
    }
}