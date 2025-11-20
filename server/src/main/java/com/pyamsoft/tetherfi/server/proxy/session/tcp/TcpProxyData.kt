package com.pyamsoft.tetherfi.server.proxy.session.tcp

import com.pyamsoft.tetherfi.server.proxy.ProxyConnectionInfo
import com.pyamsoft.tetherfi.server.proxy.session.ProxyData
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import javax.net.SocketFactory // <--- [MODIFICACION 1] Import agregado

@ConsistentCopyVisibility
internal data class TcpProxyData
internal constructor(
    internal val proxyInput: ByteReadChannel,
    internal val proxyOutput: ByteWriteChannel,
    internal val proxyConnectionInfo: ProxyConnectionInfo,
    // [MODIFICACION 2] Nuevo campo para transportar el factory de red
    internal val upstream: SocketFactory,
) : ProxyData