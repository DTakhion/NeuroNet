package com.pyamsoft.tetherfi.server.proxy.session.tcp.socks

import androidx.annotation.CheckResult
import com.pyamsoft.pydroid.core.cast
import com.pyamsoft.pydroid.core.requireNotNull
import com.pyamsoft.pydroid.util.ifNotCancellation
import com.pyamsoft.tetherfi.core.Timber
import com.pyamsoft.tetherfi.server.ServerSocketTimeout
import com.pyamsoft.tetherfi.server.SocketCreator
import com.pyamsoft.tetherfi.server.broadcast.BroadcastNetworkStatus
import com.pyamsoft.tetherfi.server.clients.ByteTransferReport
import com.pyamsoft.tetherfi.server.clients.TetherClient
import com.pyamsoft.tetherfi.server.network.SocketBinder
import com.pyamsoft.tetherfi.server.proxy.ProxyConnectionInfo
import com.pyamsoft.tetherfi.server.proxy.ServerDispatcher
import com.pyamsoft.tetherfi.server.proxy.SocketTagger
import com.pyamsoft.tetherfi.server.proxy.SocketTracker
import com.pyamsoft.tetherfi.server.proxy.UpstreamProxyConfig
import com.pyamsoft.tetherfi.server.proxy.session.tcp.relayData
import com.pyamsoft.tetherfi.server.proxy.usingConnection
import com.pyamsoft.tetherfi.server.net.connectWithConfiguration
import com.pyamsoft.tetherfi.server.net.remoteAddress
import com.pyamsoft.tetherfi.server.net.socketTimeout
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.toJavaAddress
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import java.net.InetAddress
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal abstract class BaseSOCKSImplementation<
        AT : BaseSOCKSImplementation.SOCKSAddressType,
        R : BaseSOCKSImplementation.Responder<AT>,
        >
protected constructor(
    protected val appScope: CoroutineScope,
    protected val socketTagger: SocketTagger,
) : SOCKSImplementation<R> {

    private suspend fun connect(
        scope: CoroutineScope,
        socketCreator: SocketCreator,
        timeout: ServerSocketTimeout,
        serverDispatcher: ServerDispatcher,
        socketTracker: SocketTracker,
        networkBinder: SocketBinder.NetworkBinder,
        proxyInput: ByteReadChannel,
        proxyOutput: ByteWriteChannel,
        client: TetherClient,
        destinationAddress: InetAddress,
        destinationPort: UShort,
        addressType: AT,
        responder: R,
        upstreamProxyConfig: UpstreamProxyConfig?,
        onError: suspend (Throwable) -> Unit,
        onReport: suspend (ByteTransferReport) -> Unit,
    ) =
        socketCreator.create(
            type = SocketCreator.Type.CLIENT,
            onError = { appScope.launch(Dispatchers.IO) { onError(it) } },
            onBuild = { builder ->
                runBlocking(Dispatchers.IO) {
                    val connected =
                        try {
                            builder
                                .tcp()
                                .configure { reuseAddress = true }
                                .also { socketTagger.tagSocket() }
                                .let { b ->
                                    withTimeout(2.minutes) {
                                        val target =
                                            if (upstreamProxyConfig?.isValid() == true) {
                                                InetSocketAddress(
                                                    hostname = upstreamProxyConfig.host,
                                                    port = upstreamProxyConfig.port,
                                                )
                                            } else {
                                                InetSocketAddress(
                                                    hostname = destinationAddress.hostName,
                                                    port = destinationPort.toInt(),
                                                )
                                            }

                                        b.connectWithConfiguration(
                                            remote = target,
                                            onConnected = { s -> runBlocking { networkBinder.bindToNetwork(s) } },
                                            configure = {
                                                val d = timeout.timeoutDuration
                                                if (!d.isInfinite()) this.socketTimeout = d.inWholeMilliseconds
                                            },
                                        )
                                    }
                                }
                                .also { socketTracker.track(it) }
                        } catch (e: Throwable) {
                            if (e is TimeoutCancellationException) {
                                Timber.w { "Timeout while waiting for socket connect()" }
                                responder.sendRefusal()
                                throw e
                            } else {
                                e.ifNotCancellation {
                                    Timber.e(e) { "Error during socket connect()" }
                                    responder.sendRefusal()
                                    return@runBlocking
                                }
                            }
                        }

                    connected.use { socket ->
                        val remote = socket.remoteAddress
                        Timber.d { "SOCKS CONNECTED: $remote" }
                        Timber.d { "[B-CONNECT] Socket conectado a destino remoto: $remote para cliente ${client.nickName}" }

                        socket.usingConnection(autoFlush = false) { internetInput, internetOutput ->
                            val tunneled =
                                upstreamProxyConfig?.takeIf { it.isValid() }?.let {
                                    establishUpstreamTunnel(
                                        internetInput = internetInput,
                                        internetOutput = internetOutput,
                                        destinationHost = destinationAddress.hostName,
                                        destinationPort = destinationPort.toInt(),
                                    )
                                } ?: true

                            if (!tunneled) {
                                Timber.w { "Upstream proxy rejected tunnel for ${destinationAddress.hostName}:${destinationPort}" }
                                responder.sendRefusal()
                                return@usingConnection
                            }

                            try {
                                responder.sendConnectSuccess(
                                    addressType = addressType,
                                    remote = remote.cast<InetSocketAddress>(),
                                )
                            } catch (e: Throwable) {
                                e.ifNotCancellation {
                                    Timber.e(e) { "Error sending connect() SUCCESS notification" }
                                    return@usingConnection
                                }
                            }

                            try {
                                Timber.d { "[C-RELAY] Iniciando relayData para conexión SOCKS a $remote" }
                                relayData(
                                    scope = scope,
                                    client = client,
                                    proxyInput = proxyInput,
                                    proxyOutput = proxyOutput,
                                    internetInput = internetInput,
                                    internetOutput = internetOutput,
                                    serverDispatcher = serverDispatcher,
                                    onReport = onReport,
                                )
                                Timber.d { "[C-RELAY] RelayData finalizado para $remote" }
                            } finally {
                                internetOutput.flush()
                            }
                        }
                    }
                }
            },
        )

    private suspend fun establishUpstreamTunnel(
        internetInput: ByteReadChannel,
        internetOutput: ByteWriteChannel,
        destinationHost: String,
        destinationPort: Int,
    ): Boolean {
        val connectLine = "CONNECT ${destinationHost}:${destinationPort} HTTP/1.1\r\n"
        val hostLine = "Host: ${destinationHost}:${destinationPort}\r\n"

        internetOutput.writeFully((connectLine + hostLine + "\r\n").encodeToByteArray())
        internetOutput.flush()

        val statusLine = internetInput.readUTF8Line() ?: return false
        if (!statusLine.startsWith("HTTP/")) {
            Timber.w { "Upstream CONNECT bad status line: $statusLine" }
            return false
        }

        if (!statusLine.contains(" 200")) {
            Timber.w { "Upstream CONNECT rejected tunnel: $statusLine" }
            return false
        }

        while (true) {
            val line = internetInput.readUTF8Line() ?: break
            if (line.isBlank()) {
                break
            }
        }

        return true
    }

    private suspend fun bind(
        scope: CoroutineScope,
        socketCreator: SocketCreator,
        serverDispatcher: ServerDispatcher,
        socketTracker: SocketTracker,
        connectionInfo: BroadcastNetworkStatus.ConnectionInfo.Connected,
        proxyInput: ByteReadChannel,
        proxyOutput: ByteWriteChannel,
        client: TetherClient,
        destinationAddress: InetAddress,
        addressType: AT,
        responder: R,
        onError: suspend (Throwable) -> Unit,
        onReport: suspend (ByteTransferReport) -> Unit,
    ) =
        socketCreator.create(
            type = SocketCreator.Type.SERVER,
            onError = { appScope.launch(Dispatchers.IO) { onError(it) } },
            onBuild = { builder ->
                runBlocking(Dispatchers.IO) {
                    val bound =
                        try {
                            builder
                                .tcp()
                                .configure { reuseAddress = true }
                                .also { socketTagger.tagSocket() }
                                .let { b ->
                                    Timber.d { "SOCKS BIND -> ${connectionInfo.hostName}" }
                                    b.bind(
                                        hostname = connectionInfo.hostName,
                                        port = 0,
                                        configure = { reuseAddress = true },
                                    )
                                }
                                .also { socketTracker.track(it) }
                                .use { server ->
                                    val boundSocket = withTimeout(2.minutes) { server.accept() }
                                    responder.sendBindInitialized(
                                        addressType = addressType,
                                        bound = server.localAddress.cast(),
                                    )
                                    boundSocket
                                }
                        } catch (e: Throwable) {
                            if (e is TimeoutCancellationException) {
                                Timber.w { "Timeout while waiting for socket bind()" }
                                responder.sendRefusal()
                                throw e
                            } else {
                                e.ifNotCancellation {
                                    Timber.e(e) { "Error during socket bind()" }
                                    responder.sendError()
                                    return@runBlocking
                                }
                            }
                        }

                    socketTracker.track(bound)

                    bound.use { socket ->
                        val hostAddress = socket.remoteAddress.cast<InetSocketAddress>().requireNotNull()
                        if (hostAddress.toJavaAddress() != destinationAddress) {
                            Timber.w { "bind() address $hostAddress != original $destinationAddress" }
                            responder.sendRefusal()
                            return@use
                        }

                        try {
                            responder.sendBindInitialized(
                                addressType = addressType,
                                bound = hostAddress,
                            )
                        } catch (e: Throwable) {
                            e.ifNotCancellation {
                                Timber.e(e) { "Error sending bind() SUCCESS notification" }
                                responder.sendError()
                                return@use
                            }
                        }

                        socket.usingConnection(autoFlush = false) { internetInput, internetOutput ->
                            try {
                                relayData(
                                    scope = scope,
                                    client = client,
                                    serverDispatcher = serverDispatcher,
                                    proxyInput = proxyInput,
                                    proxyOutput = proxyOutput,
                                    internetInput = internetInput,
                                    internetOutput = internetOutput,
                                    onReport = onReport,
                                )
                            } finally {
                                internetOutput.flush()
                            }
                        }
                    }
                }
            },
        )

    protected suspend fun performSOCKSCommand(
        scope: CoroutineScope,
        socketCreator: SocketCreator,
        timeout: ServerSocketTimeout,
        serverDispatcher: ServerDispatcher,
        socketTracker: SocketTracker,
        connectionInfo: BroadcastNetworkStatus.ConnectionInfo.Connected,
        networkBinder: SocketBinder.NetworkBinder,
        proxyInput: ByteReadChannel,
        proxyOutput: ByteWriteChannel,
        proxyConnectionInfo: ProxyConnectionInfo,
        client: TetherClient,
        command: SOCKSCommand,
        destinationPort: UShort,
        destinationAddress: InetAddress,
        addressType: AT,
        responder: R,
        upstreamProxyConfig: UpstreamProxyConfig?,
        onError: suspend (Throwable) -> Unit,
        onReport: suspend (ByteTransferReport) -> Unit,
    ) =
        when (command) {
            SOCKSCommand.CONNECT -> {
                connect(
                    scope = scope,
                    socketCreator = socketCreator,
                    socketTracker = socketTracker,
                    networkBinder = networkBinder,
                    serverDispatcher = serverDispatcher,
                    proxyInput = proxyInput,
                    proxyOutput = proxyOutput,
                    responder = responder,
                    client = client,
                    destinationAddress = destinationAddress,
                    destinationPort = destinationPort,
                    addressType = addressType,
                    timeout = timeout,
                    upstreamProxyConfig = upstreamProxyConfig,
                    onError = onError,
                    onReport = onReport,
                )
            }
            SOCKSCommand.BIND -> {
                bind(
                    scope = scope,
                    socketCreator = socketCreator,
                    socketTracker = socketTracker,
                    serverDispatcher = serverDispatcher,
                    connectionInfo = connectionInfo,
                    proxyInput = proxyInput,
                    proxyOutput = proxyOutput,
                    responder = responder,
                    client = client,
                    destinationAddress = destinationAddress,
                    addressType = addressType,
                    onError = onError,
                    onReport = onReport,
                )
            }
            SOCKSCommand.UDP_ASSOCIATE -> {
                udpAssociate(
                    scope = scope,
                    timeout = timeout,
                    networkBinder = networkBinder,
                    socketCreator = socketCreator,
                    socketTracker = socketTracker,
                    serverDispatcher = serverDispatcher,
                    connectionInfo = connectionInfo,
                    proxyInput = proxyInput,
                    proxyOutput = proxyOutput,
                    proxyConnectionInfo = proxyConnectionInfo,
                    responder = responder,
                    client = client,
                    addressType = addressType,
                    onError = onError,
                    onReport = onReport,
                )
            }
        }

    protected abstract suspend fun udpAssociate(
        scope: CoroutineScope,
        timeout: ServerSocketTimeout,
        networkBinder: SocketBinder.NetworkBinder,
        socketCreator: SocketCreator,
        serverDispatcher: ServerDispatcher,
        socketTracker: SocketTracker,
        connectionInfo: BroadcastNetworkStatus.ConnectionInfo.Connected,
        proxyInput: ByteReadChannel,
        proxyOutput: ByteWriteChannel,
        proxyConnectionInfo: ProxyConnectionInfo,
        client: TetherClient,
        addressType: AT,
        responder: R,
        onError: suspend (Throwable) -> Unit,
        onReport: suspend (ByteTransferReport) -> Unit,
    )

    internal interface SOCKSAddressType

    internal interface Responder<AT : SOCKSAddressType> : SOCKSImplementation.Responder {
        suspend fun sendRefusal()
        suspend fun sendError()
        suspend fun sendConnectSuccess(addressType: AT, remote: InetSocketAddress?)
        suspend fun sendBindInitialized(addressType: AT, bound: InetSocketAddress?)

        companion object {
            internal const val DEBUG_SOCKS_REPLIES = false
            internal val INVALID_IPV6_BYTES = ByteArray(16)
            internal val INVALID_IPV4_BYTES = ByteArray(4)
            internal const val INVALID_PORT: Short = 0

            @CheckResult
            internal fun InetSocketAddress.getJavaInetSocketAddress(): InetAddress {
                return toJavaAddress()
                    .cast<java.net.InetSocketAddress>()
                    .requireNotNull { "Failed to cast to java.net.InetSocketAddress: $this" }
                    .address
                    .requireNotNull { "Failed to get IP address from $this" }
            }
        }
    }
}
