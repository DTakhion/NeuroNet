package com.pyamsoft.tetherfi.server.proxy.manager

import androidx.annotation.CheckResult
import com.pyamsoft.tetherfi.server.SocketCreator
import com.pyamsoft.tetherfi.server.broadcast.BroadcastNetworkStatus
import com.pyamsoft.tetherfi.server.lock.Locker
import com.pyamsoft.tetherfi.server.proxy.ServerDispatcher
import com.pyamsoft.tetherfi.server.proxy.SharedProxy
import javax.net.SocketFactory

internal interface ProxyManager {

    suspend fun loop(
        lock: Locker.Lock,
        onOpened: suspend () -> Unit,
        onClosing: suspend () -> Unit,
        onError: suspend (Throwable) -> Unit,
    )

    interface Factory {

        @CheckResult
        suspend fun create(
            type: SharedProxy.Type,
            info: BroadcastNetworkStatus.ConnectionInfo.Connected,
            socketCreator: SocketCreator,
            serverDispatcher: ServerDispatcher,
            upstream: SocketFactory,   // ← NUEVO PARÁMETRO OBLIGATORIO
        ): ProxyManager
    }
}
