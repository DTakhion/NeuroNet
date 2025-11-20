package com.pyamsoft.tetherfi.server.proxy

import com.pyamsoft.tetherfi.server.Server
import com.pyamsoft.tetherfi.server.broadcast.BroadcastNetworkStatus
import com.pyamsoft.tetherfi.server.lock.Locker
import kotlinx.coroutines.flow.Flow
import javax.net.SocketFactory // <--- [MODIFICACION 1] Import necesario

interface SharedProxy : Server {

    /**
     * Inicia el proxy compartido.
     * [upstream] es el Factory que enruta el tráfico a la red correcta (Wi-Fi/Celular).
     */
    suspend fun start(
        lock: Locker.Lock,
        connectionStatus: Flow<BroadcastNetworkStatus.ConnectionInfo>,
        upstream: SocketFactory, // <--- [MODIFICACION 2] Nuevo parámetro obligatorio
    )

    enum class Type {
        HTTP,
        SOCKS,
    }

    // [ADVERTENCIA IMPORTANTE]
    // Si tu archivo original tenía más código aquí abajo (como interfaces
    // "Factory" o "Runner"), ¡MANTENLO! No lo borres.
    // Solo modifica la función start() de arriba.
    // Si no había nada más, entonces este archivo está completo.
}
