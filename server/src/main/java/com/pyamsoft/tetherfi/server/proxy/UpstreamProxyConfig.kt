package com.pyamsoft.tetherfi.server.proxy

import androidx.annotation.CheckResult

internal data class UpstreamProxyConfig(
    val host: String,
    val port: Int,
) {

  @CheckResult
  fun isValid(): Boolean {
    return host.isNotBlank() && port in 1..65535
  }
}
