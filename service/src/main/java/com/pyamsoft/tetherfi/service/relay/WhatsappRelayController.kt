package com.pyamsoft.tetherfi.service.relay

import com.pyamsoft.tetherfi.core.Timber
import com.pyamsoft.tetherfi.server.ProxyPreferences
import com.pyamsoft.tetherfi.server.ProxyRole
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Singleton
internal class WhatsappRelayController
@Inject
internal constructor(
    private val proxyPreferences: ProxyPreferences,
) {

  fun start(scope: CoroutineScope) {
    scope.launch(context = Dispatchers.IO) {
      val isEnabled = proxyPreferences.listenForWhatsappProxyModeChanges().first()
      if (!isEnabled) {
        Timber.d { "WhatsApp relay disabled, skip start" }
        return@launch
      }

      val role = proxyPreferences.listenForProxyRoleChanges().first()
      val listenerPort =
          proxyPreferences.listenForWhatsappListenerPortChanges().first().takeIf { it in 1..65535 }
      if (listenerPort == null) {
        Timber.w { "WhatsApp relay has no valid listener port" }
        return@launch
      }

      val targetHost: String
      val targetPort: Int
      if (role == ProxyRole.SERVER_ONLY) {
        targetHost = proxyPreferences.listenForWhatsappFinalHostChanges().first().trim()
        targetPort = proxyPreferences.listenForWhatsappFinalPortChanges().first()
      } else {
        targetHost = proxyPreferences.listenForWhatsappNextHopHostChanges().first().trim()
        targetPort = proxyPreferences.listenForWhatsappNextHopPortChanges().first()
      }

      if (targetHost.isBlank() || targetPort !in 1..65535) {
        Timber.w {
          "WhatsApp relay missing target host/port (role=$role host='$targetHost' port=$targetPort)"
        }
        return@launch
      }

      val token = proxyPreferences.listenForWhatsappTokenChanges().first().trim()

      Timber.d {
        "Starting WhatsApp relay on $listenerPort -> $targetHost:$targetPort (role=$role) token=${
            if (token.isBlank()) "<none>" else "<set>"
        }"
      }

      runRelay(
          scope = this,
          listenerPort = listenerPort,
          targetHost = targetHost,
          targetPort = targetPort,
          token = token,
      )
    }
  }

  private suspend fun runRelay(
      scope: CoroutineScope,
      listenerPort: Int,
      targetHost: String,
      targetPort: Int,
      token: String,
  ) =
      withContext(context = Dispatchers.IO) {
        val limiter = Semaphore(MAX_CONNECTIONS)
        val server = ServerSocket()
        try {
          server.reuseAddress = true
          server.soTimeout = ACCEPT_TIMEOUT_MS
          server.bind(InetSocketAddress(listenerPort))

          Timber.d { "WhatsApp relay listening on port $listenerPort" }

          var backoff = MIN_BACKOFF_MS
          while (scope.isActive) {
            try {
              val client = server.accept()
              limiter.withPermit {
                scope.launch(context = Dispatchers.IO) {
                  handleConnection(
                      client = client,
                      targetHost = targetHost,
                      targetPort = targetPort,
                      token = token,
                  )
                }
              }
              backoff = MIN_BACKOFF_MS
            } catch (e: SocketTimeoutException) {
              // Accept timeout, loop to check isActive
            } catch (e: IOException) {
              Timber.w { "Error accepting WhatsApp relay connection: ${e.message}" }
              delay(backoff)
              backoff = min(backoff * 2, MAX_BACKOFF_MS)
            }
          }
        } finally {
          Timber.d { "Shutting down WhatsApp relay" }
          runCatching { server.close() }
        }
      }

  private suspend fun handleConnection(
      client: Socket,
      targetHost: String,
      targetPort: Int,
      token: String,
  ) {
    withContext(context = Dispatchers.IO) {
      var upstream: Socket? = null
      try {
        client.tcpSetup()
        upstream = Socket()
        upstream.tcpSetup()
        upstream.connect(InetSocketAddress(targetHost, targetPort), CONNECT_TIMEOUT_MS)

        // Optional token for chained relays
        if (token.isNotBlank()) {
          upstream.getOutputStream().write((token + "\n").toByteArray(StandardCharsets.UTF_8))
          upstream.getOutputStream().flush()
        }

        coroutineScope {
          val downToUp = launch(context = Dispatchers.IO) {
            client.getInputStream().copyTo(upstream.getOutputStream(), BUFFER_SIZE_BYTES)
            upstream.getOutputStream().flush()
          }

          val upToDown = launch(context = Dispatchers.IO) {
            upstream.getInputStream().copyTo(client.getOutputStream(), BUFFER_SIZE_BYTES)
            client.getOutputStream().flush()
          }

          downToUp.join()
          upToDown.join()
        }
      } catch (e: Throwable) {
        Timber.w { "WhatsApp relay pipe failed: ${e.message}" }
      } finally {
        runCatching { client.close() }
        runCatching { upstream?.close() }
      }
    }
  }

  private fun Socket.tcpSetup() {
    keepAlive = true
    tcpNoDelay = true
    soTimeout = SOCKET_TIMEOUT_MS
  }

  companion object {

    private const val BUFFER_SIZE_BYTES = 16 * 1024
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val SOCKET_TIMEOUT_MS = 30_000
    private const val ACCEPT_TIMEOUT_MS = 2_000
    private const val MIN_BACKOFF_MS = 500L
    private const val MAX_BACKOFF_MS = 5_000L
    private const val MAX_CONNECTIONS = 32
  }
}
