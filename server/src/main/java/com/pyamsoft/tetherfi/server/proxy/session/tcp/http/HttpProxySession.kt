package com.pyamsoft.tetherfi.server.proxy.session.tcp.http

import com.pyamsoft.tetherfi.server.net.connectWithConfiguration
import com.pyamsoft.tetherfi.server.net.socketTimeout
import com.pyamsoft.pydroid.core.ThreadEnforcer
import com.pyamsoft.pydroid.util.ifNotCancellation
import com.pyamsoft.tetherfi.core.notification.NotificationErrorLauncher
import com.pyamsoft.tetherfi.server.ServerSocketTimeout
import com.pyamsoft.tetherfi.server.SocketCreator
import com.pyamsoft.tetherfi.server.broadcast.BroadcastNetworkStatus
import com.pyamsoft.tetherfi.server.clients.AllowedClients
import com.pyamsoft.tetherfi.server.clients.BlockedClients
import com.pyamsoft.tetherfi.server.clients.ByteTransferReport
import com.pyamsoft.tetherfi.server.clients.ClientResolver
import com.pyamsoft.tetherfi.server.clients.TetherClient
import com.pyamsoft.tetherfi.server.network.SocketBinder
import com.pyamsoft.tetherfi.server.proxy.ProxyConnectionInfo
import com.pyamsoft.tetherfi.server.proxy.ServerDispatcher
import com.pyamsoft.tetherfi.server.proxy.SharedProxy
import com.pyamsoft.tetherfi.server.proxy.SocketTagger
import com.pyamsoft.tetherfi.server.proxy.SocketTracker
import com.pyamsoft.tetherfi.server.proxy.UpstreamProxyConfig
import com.pyamsoft.tetherfi.server.proxy.session.tcp.TcpProxySession
import com.pyamsoft.tetherfi.server.proxy.session.tcp.TransportWriteCommand
import com.pyamsoft.tetherfi.server.proxy.usingConnection
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.InetSocketAddress as JInetSocketAddress
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import javax.net.SocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
internal class HttpProxySession
@Inject
internal constructor(
    @param:Named("app_scope") private val appScope: CoroutineScope,
    private val notificationErrorLauncher: NotificationErrorLauncher,
    private val transport: HttpTransport,
    socketTagger: SocketTagger,
    blockedClients: BlockedClients,
    clientResolver: ClientResolver,
    allowedClients: AllowedClients,
    enforcer: ThreadEnforcer,
) :
    TcpProxySession<HttpProxyRequest>(
        transport = transport,
        socketTagger = socketTagger,
        blockedClients = blockedClients,
        clientResolver = clientResolver,
        allowedClients = allowedClients,
        enforcer = enforcer,
    ) {

    override val proxyType = SharedProxy.Type.HTTP

    private data class UpstreamProxy(val address: JInetSocketAddress)

    @Suppress("ReturnCount")
    private fun resolveUpstreamProxy(
        request: HttpProxyRequest,
        overrideConfig: UpstreamProxyConfig?,
    ): UpstreamProxy? {
        if (overrideConfig != null && overrideConfig.isValid()) {
            return UpstreamProxy(JInetSocketAddress(overrideConfig.host, overrideConfig.port))
        }

        val selector = ProxySelector.getDefault() ?: return null

        val target = URI.create("http://${request.host}:${request.port}")
        val proxies = runCatching { selector.select(target) }.getOrNull() ?: return null

        for (proxy in proxies) {
            if (proxy == Proxy.NO_PROXY) continue

            if (proxy.type() == Proxy.Type.HTTP) {
                val address = proxy.address()
                if (address is JInetSocketAddress) {
                    return UpstreamProxy(address)
                }
            }
        }

        return null
    }

    /**
     * Versión original que usa Ktor + SocketCreator.
     * NOTA: el parámetro `upstream` se pasa desde TcpProxySession, pero aquí lo ignoramos.
     */
    private suspend inline fun <T> connectToInternet(
        networkBinder: SocketBinder.NetworkBinder,
        socketCreator: SocketCreator,
        timeout: ServerSocketTimeout,
        autoFlush: Boolean,
        request: HttpProxyRequest,
        targetHost: String,
        targetPort: Int,
        socketTracker: SocketTracker,
        upstreamProxy: UpstreamProxy?,
        noinline onError: (Throwable) -> Unit,
        crossinline block: suspend (ByteReadChannel, ByteWriteChannel) -> T,
    ): T =
        socketCreator.create(
            type = SocketCreator.Type.CLIENT,
            onError = { onError },
            onBuild = { builder ->
                debugLog { "Connect to upstream target=$targetHost:$targetPort for ${request.raw}" }

                val remote =
                    InetSocketAddress(
                        hostname = targetHost,
                        port = targetPort,
                    )

                val socket =
                    builder
                        .tcp()
                        .configure {
                            reuseAddress = true
                            // reusePort = true // (no soportado en Ktor 3)
                        }
                        .also { socketTagger.tagSocket() }
                        .connectWithConfiguration(
                            remote = remote,
                            configure = {
                                val duration = timeout.timeoutDuration
                                if (!duration.isInfinite()) {
                                    // Usamos el “socketTimeout” compat de KtorCompat
                                    this.socketTimeout = duration.inWholeMilliseconds
                                }
                            },
                        )

                // Ahora sí podemos llamar suspend:
                networkBinder.bindToNetwork(socket)

                // Track para shutdown
                socketTracker.track(socket)

                return@create socket.usingConnection(autoFlush = autoFlush) {
                        internetInput,
                        internetOutput,
                    ->
                    block(internetInput, internetOutput)
                }
            },
        )

    private suspend fun handleProxyToInternetError(
        throwable: Throwable,
        client: TetherClient,
        request: HttpProxyRequest,
        proxyOutput: ByteWriteChannel,
    ) {
        throwable.ifNotCancellation {
            if (throwable is SocketTimeoutException) {
                warnLog { "Proxy:Internet socket timeout! $request $client" }
            } else {
                errorLog(throwable) { "Error during Internet exchange $request $client" }
                transport.writeProxyOutput(proxyOutput, request, TransportWriteCommand.ERROR)
            }
        }
    }

    /**
     * Implementación que satisface la nueva firma de TcpProxySession.proxyToInternet.
     *
     * IMPORTANTE: `upstream` llega desde TcpProxySession, pero en HTTP seguimos apoyándonos
     * en SocketCreator + Ktor. El selector de red (WiFi/Datos) lo manejamos vía SocketBinder.
     */
    override suspend fun proxyToInternet(
        scope: CoroutineScope,
        socketCreator: SocketCreator,
        upstream: SocketFactory, // <-- parámetro requerido por la superclase, aquí no usado
        upstreamProxyConfig: UpstreamProxyConfig?,
        timeout: ServerSocketTimeout,
        connectionInfo: BroadcastNetworkStatus.ConnectionInfo.Connected,
        networkBinder: SocketBinder.NetworkBinder,
        serverDispatcher: ServerDispatcher,
        proxyInput: ByteReadChannel,
        proxyOutput: ByteWriteChannel,
        proxyConnectionInfo: ProxyConnectionInfo,
        socketTracker: SocketTracker,
        client: TetherClient,
        request: HttpProxyRequest,
        onReport: suspend (ByteTransferReport) -> Unit,
    ) {
        enforcer.assertOffMainThread()

        val upstreamProxy = resolveUpstreamProxy(request, upstreamProxyConfig)
        val (targetHost, targetPort) =
            if (upstreamProxy != null) {
                upstreamProxy.address.run { hostString to port }
            } else {
                request.host to request.port
            }

        try {
            connectToInternet(
                autoFlush = true,
                socketCreator = socketCreator,
                timeout = timeout,
                networkBinder = networkBinder,
                targetHost = targetHost,
                targetPort = targetPort,
                socketTracker = socketTracker,
                request = request,
                upstreamProxy = upstreamProxy,
                onError = { e ->
                    appScope.launch(context = Dispatchers.IO) {
                        handleProxyToInternetError(
                            throwable = e,
                            proxyOutput = proxyOutput,
                            request = request,
                            client = client,
                        )
                        notificationErrorLauncher.showError(e)
                    }
                },
                block = { internetInput, internetOutput ->
                    try {
                        transport.exchangeInternet(
                            scope = scope,
                            serverDispatcher = serverDispatcher,
                            proxyInput = proxyInput,
                            proxyOutput = proxyOutput,
                            internetInput = internetInput,
                            internetOutput = internetOutput,
                            request = request,
                            useUpstreamProxy = upstreamProxy != null,
                            client = client,
                            onReport = onReport,
                        )
                    } finally {
                        internetOutput.flush()
                    }
                },
            )
        } catch (e: Throwable) {
            handleProxyToInternetError(
                throwable = e,
                proxyOutput = proxyOutput,
                request = request,
                client = client,
            )
        }
    }
}
