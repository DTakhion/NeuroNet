package com.pyamsoft.tetherfi.server.proxy.manager.factory

import androidx.annotation.CheckResult
import com.pyamsoft.pydroid.bus.EventConsumer
import com.pyamsoft.pydroid.core.ThreadEnforcer
import com.pyamsoft.tetherfi.core.AppDevEnvironment
import com.pyamsoft.tetherfi.server.ExpertPreferences
import com.pyamsoft.tetherfi.server.ProxyPreferences
import com.pyamsoft.tetherfi.server.ServerInternalApi
import com.pyamsoft.tetherfi.server.SocketCreator
import com.pyamsoft.tetherfi.server.broadcast.BroadcastNetworkStatus
import com.pyamsoft.tetherfi.server.event.ServerStopRequestEvent
import com.pyamsoft.tetherfi.server.network.SocketBinder
import com.pyamsoft.tetherfi.server.proxy.ServerDispatcher
import com.pyamsoft.tetherfi.server.proxy.SharedProxy
import com.pyamsoft.tetherfi.server.proxy.SocketTagger
import com.pyamsoft.tetherfi.server.proxy.manager.ProxyManager
import com.pyamsoft.tetherfi.server.proxy.manager.TcpProxyManager
import com.pyamsoft.tetherfi.server.proxy.session.ProxySession
import com.pyamsoft.tetherfi.server.proxy.session.tcp.TcpProxyData
import javax.inject.Inject
import javax.inject.Named
import javax.net.SocketFactory // <--- [MODIFICACION 1] Import necesario
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class DefaultProxyManagerFactory
@Inject
internal constructor(
    @param:ServerInternalApi private val socketBinder: SocketBinder,
    @param:Named("app_scope") private val appScope: CoroutineScope,
    @param:Named("http") private val httpSession: ProxySession<TcpProxyData>,
    @param:Named("socks") private val socksSession: ProxySession<TcpProxyData>,
    private val expertPreferences: ExpertPreferences,
    private val socketTagger: SocketTagger,
    private val enforcer: ThreadEnforcer,
    private val proxyPreferences: ProxyPreferences,
    private val appEnvironment: AppDevEnvironment,
    private val serverStopConsumer: EventConsumer<ServerStopRequestEvent>,
) : ProxyManager.Factory {

    @CheckResult
    private fun createTcp(
        proxyType: SharedProxy.Type,
        session: ProxySession<TcpProxyData>,
        info: BroadcastNetworkStatus.ConnectionInfo.Connected,
        socketCreator: SocketCreator,
        dispatcher: ServerDispatcher,
        port: Int,
        // [MODIFICACION 2] Recibimos el upstream
        upstream: SocketFactory,
    ): ProxyManager {
        enforcer.assertOffMainThread()

        return TcpProxyManager(
            appScope = appScope,
            socketTagger = socketTagger,
            appEnvironment = appEnvironment,
            yoloRepeatDelay = 3.seconds,
            enforcer = enforcer,
            serverStopConsumer = serverStopConsumer,
            socketBinder = socketBinder,
            expertPreferences = expertPreferences,
            proxyType = proxyType,
            session = session,
            hostConnection = info,
            port = port,
            serverDispatcher = dispatcher,
            socketCreator = socketCreator,
            // [MODIFICACION 3] Inyectamos el upstream en el constructor del Manager
            upstream = upstream,
        )
    }

    @CheckResult
    private suspend fun createHttp(
        info: BroadcastNetworkStatus.ConnectionInfo.Connected,
        socketCreator: SocketCreator,
        dispatcher: ServerDispatcher,
        // [MODIFICACION 4] Pasamos el upstream
        upstream: SocketFactory,
    ): ProxyManager {
        enforcer.assertOffMainThread()

        val port = proxyPreferences.listenForHttpPortChanges().first()

        return createTcp(
            proxyType = SharedProxy.Type.HTTP,
            session = httpSession,
            info = info,
            socketCreator = socketCreator,
            dispatcher = dispatcher,
            port = port,
            // [MODIFICACION 5] Relevo del upstream
            upstream = upstream,
        )
    }

    @CheckResult
    private suspend fun createSocks(
        info: BroadcastNetworkStatus.ConnectionInfo.Connected,
        socketCreator: SocketCreator,
        dispatcher: ServerDispatcher,
        // [MODIFICACION 6] Pasamos el upstream
        upstream: SocketFactory,
    ): ProxyManager {
        enforcer.assertOffMainThread()

        val port = proxyPreferences.listenForSocksPortChanges().first()

        return createTcp(
            proxyType = SharedProxy.Type.SOCKS,
            session = socksSession,
            info = info,
            socketCreator = socketCreator,
            dispatcher = dispatcher,
            port = port,
            // [MODIFICACION 7] Relevo del upstream
            upstream = upstream,
        )
    }

    // [MODIFICACION 8] Implementamos la interfaz actualizada
    override suspend fun create(
        type: SharedProxy.Type,
        info: BroadcastNetworkStatus.ConnectionInfo.Connected,
        socketCreator: SocketCreator,
        serverDispatcher: ServerDispatcher,
        upstream: SocketFactory, // <--- El nuevo parámetro obligatorio
    ): ProxyManager =
        withContext(context = Dispatchers.Default) {
            return@withContext when (type) {
                SharedProxy.Type.HTTP ->
                    createHttp(
                        info = info,
                        socketCreator = socketCreator,
                        dispatcher = serverDispatcher,
                        upstream = upstream, // <--- Pasamos el valor
                    )
                SharedProxy.Type.SOCKS ->
                    createSocks(
                        info = info,
                        socketCreator = socketCreator,
                        dispatcher = serverDispatcher,
                        upstream = upstream, // <--- Pasamos el valor
                    )
            }
        }
}
