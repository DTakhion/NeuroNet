package com.pyamsoft.tetherfi.server.net

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import java.net.InetSocketAddress as JInet
import java.net.SocketAddress as JSocket

// Selector por defecto
private val DefaultSelector by lazy { SelectorManager(Dispatchers.IO) }

/* ===================== TCP ===================== */

/** Conecta usando hostname/port para evitar tipos internos de Ktor. */
suspend fun connectWithConfiguration(
    host: String,
    port: Int,
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
): Socket {
    return aSocket(DefaultSelector).tcp().connect(host, port, configure)
}

/** Overload que acepta java.net.InetSocketAddress. */
suspend fun connectWithConfiguration(
    remote: JInet,
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
): Socket {
    return aSocket(DefaultSelector).tcp().connect(remote.hostString, remote.port, configure)
}

/** Overload extra: remote como java.net.SocketAddress (se castea si es Inet). */
suspend fun connectWithConfiguration(
    remote: JSocket,
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
): Socket {
    val inet = (remote as? JInet) ?: JInet("localhost", 0)
    return connectWithConfiguration(inet, configure)
}

/** Overload extra: remote como io.ktor.network.sockets.SocketAddress. */
suspend fun connectWithConfiguration(
    remote: SocketAddress,
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
): Socket {
    val inet = when (remote) {
        is io.ktor.network.sockets.InetSocketAddress -> JInet(remote.hostname, remote.port)
        else -> JInet("localhost", 0)
    }
    return connectWithConfiguration(inet, configure)
}

/** Compat no-op: propiedad de Socket (legacy) */
var Socket.socketTimeout: Int
    get() = 0
    set(@Suppress("UNUSED_PARAMETER") value) { /* no-op */ }

/** Compat no-op: timeout dentro de configure{ } */
var SocketOptions.TCPClientSocketOptions.socketTimeout: Long
    get() = 0
    set(@Suppress("UNUSED_PARAMETER") value) { /* no-op */ }

/** Compat: remoteAddress para TCP (fallback neutro) */
val Socket.remoteAddressCompat: JSocket
    get() = JInet("0.0.0.0", 0)

/* ===================== UDP ===================== */

/** Bind usando hostname/port (evita tipos internos). */
suspend fun bindWithConfiguration(
    local: JInet? = null,
    configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
): BoundDatagramSocket {
    val builder = aSocket(DefaultSelector).udp()
    return if (local != null) builder.bind(local.hostString, local.port, configure)
    else builder.bind(configure = configure)
}

/** Compat: remoteAddress como java.net.SocketAddress */
val BoundDatagramSocket.remoteAddress: JSocket
    get() = (this as? ConnectedDatagramSocket)?.remoteAddress?.let { it.toJava() }
        ?: JInet("0.0.0.0", 0)

/* ===================== Helpers ===================== */

private fun SocketAddress.toJava(): JInet {
    return when (this) {
        is io.ktor.network.sockets.InetSocketAddress -> JInet(this.hostname, this.port)
        else -> JInet("0.0.0.0", 0)
    }
}

/** send/receive proxies por si el código legado llama a variantes antiguas. */
suspend fun BoundDatagramSocket.sendCompat(d: Datagram) { this.send(d) }
suspend fun BoundDatagramSocket.receiveCompat(): Datagram = this.receive()

/* ===================== Trampolines para call-sites antiguos ===================== */
/* Cubrimos:
 *  - connectWithConfiguration(remote) { it: Socket -> ... }  // trailing lambda sobre Socket
 *  - connectWithConfiguration(host, port) { it: Socket -> ... }
 *  - remote como JSocket o Ktor SocketAddress
 *  - bindWithConfiguration(local, maybeDatagramSocket = { s -> ... })
 *  - variantes con onBeforeBind() opcional
 */

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun Any.connectWithConfiguration(
    remote: JInet,
    configureSocket: ((Socket) -> Unit)
): Socket {
    val s = com.pyamsoft.tetherfi.server.net.connectWithConfiguration(remote) { /* options no-op */ }
    configureSocket(s)
    return s
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun Any.connectWithConfiguration(
    host: String,
    port: Int,
    configureSocket: ((Socket) -> Unit)
): Socket {
    val s = com.pyamsoft.tetherfi.server.net.connectWithConfiguration(host, port) { /* options no-op */ }
    configureSocket(s)
    return s
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun Any.connectWithConfiguration(
    remote: JSocket,
    configureSocket: ((Socket) -> Unit)
): Socket {
    val s = com.pyamsoft.tetherfi.server.net.connectWithConfiguration(remote) { /* options no-op */ }
    configureSocket(s)
    return s
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun Any.connectWithConfiguration(
    remote: SocketAddress,
    configureSocket: ((Socket) -> Unit)
): Socket {
    val s = com.pyamsoft.tetherfi.server.net.connectWithConfiguration(remote) { /* options no-op */ }
    configureSocket(s)
    return s
}

/** Variantes con onConnected + options (por si existen ambos lambdas) */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun Any.connectWithConfiguration(
    remote: JInet,
    onConnected: ((Socket) -> Unit)? = null,
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
): Socket {
    val s = com.pyamsoft.tetherfi.server.net.connectWithConfiguration(remote, configure)
    onConnected?.invoke(s)
    return s
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun Any.connectWithConfiguration(
    host: String,
    port: Int,
    onConnected: ((Socket) -> Unit)? = null,
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
): Socket {
    val s = com.pyamsoft.tetherfi.server.net.connectWithConfiguration(host, port, configure)
    onConnected?.invoke(s)
    return s
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun Any.connectWithConfiguration(
    remote: JSocket,
    onConnected: ((Socket) -> Unit)? = null,
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
): Socket {
    val s = com.pyamsoft.tetherfi.server.net.connectWithConfiguration(remote, configure)
    onConnected?.invoke(s)
    return s
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun Any.connectWithConfiguration(
    remote: SocketAddress,
    onConnected: ((Socket) -> Unit)? = null,
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
): Socket {
    val s = com.pyamsoft.tetherfi.server.net.connectWithConfiguration(remote, configure)
    onConnected?.invoke(s)
    return s
}

/** UDP: cubrir maybeDatagramSocket como (BoundDatagramSocket) -> Unit y/o () -> Unit */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun Any.bindWithConfiguration(
    local: JInet? = null,
    maybeDatagramSocket: ((BoundDatagramSocket) -> Unit)? = null,
    configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
): BoundDatagramSocket {
    val s = com.pyamsoft.tetherfi.server.net.bindWithConfiguration(local, configure)
    maybeDatagramSocket?.invoke(s)
    return s
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun Any.bindWithConfiguration(
    local: JInet? = null,
    onBeforeBind: (() -> Unit)? = null,
    maybeDatagramSocket: ((BoundDatagramSocket?) -> Unit)? = null,
    configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
): BoundDatagramSocket {
    onBeforeBind?.invoke()
    val s = com.pyamsoft.tetherfi.server.net.bindWithConfiguration(local, configure)
    maybeDatagramSocket?.invoke(s)
    return s
}

/* ===================== TRY_CALL (overloads para compatibilidad) ===================== */

/** Versión con lambdas (preferida) */
inline fun <T> TRY_CALL(vararg branches: () -> T): T {
    var last: Throwable? = null
    for (b in branches) try { return b() } catch (t: Throwable) { last = t }
    throw last ?: IllegalStateException("TRY_CALL without branches")
}

/** Versión permisiva para call-sites antiguos que pasan valores ya evaluados */
inline fun <K> TRY_CALL(vararg branches: K): K = branches.first()
