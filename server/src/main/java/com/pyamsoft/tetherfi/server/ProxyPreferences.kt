/*
 * Copyright 2025 pyamsoft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pyamsoft.tetherfi.server

import androidx.annotation.CheckResult
import kotlinx.coroutines.flow.Flow

interface ProxyPreferences {

  @CheckResult fun listenForHttpPortChanges(): Flow<Int>

  fun setHttpPort(port: Int)

  @CheckResult fun listenForHttpEnabledChanges(): Flow<Boolean>

  fun setHttpEnabled(enabled: Boolean)

  @CheckResult fun listenForSocksPortChanges(): Flow<Int>

  fun setSocksPort(port: Int)

  @CheckResult fun listenForSocksEnabledChanges(): Flow<Boolean>

  fun setSocksEnabled(enabled: Boolean)

  @CheckResult fun listenForUpstreamProxyEnabledChanges(): Flow<Boolean>

  fun setUpstreamProxyEnabled(enabled: Boolean)

  @CheckResult fun listenForUpstreamProxyHostChanges(): Flow<String>

  fun setUpstreamProxyHost(host: String)

  @CheckResult fun listenForUpstreamProxyPortChanges(): Flow<Int>

  fun setUpstreamProxyPort(port: Int)

  // --- Nuevos campos para rol y gateway automático ---

  @CheckResult fun listenForProxyRoleChanges(): Flow<ProxyRole>

  fun setProxyRole(role: ProxyRole)

  @CheckResult fun listenForUpstreamAutoGatewayChanges(): Flow<Boolean>

  fun setUpstreamAutoGateway(enabled: Boolean)

  // WhatsApp relay chain
  @CheckResult fun listenForWhatsappProxyModeChanges(): Flow<Boolean>

  fun setWhatsappProxyMode(enabled: Boolean)

  @CheckResult fun listenForWhatsappFinalHostChanges(): Flow<String>

  fun setWhatsappFinalHost(host: String)

  @CheckResult fun listenForWhatsappFinalPortChanges(): Flow<Int>

  fun setWhatsappFinalPort(port: Int)

  @CheckResult fun listenForWhatsappNextHopHostChanges(): Flow<String>

  fun setWhatsappNextHopHost(host: String)

  @CheckResult fun listenForWhatsappNextHopPortChanges(): Flow<Int>

  fun setWhatsappNextHopPort(port: Int)

  @CheckResult fun listenForWhatsappListenerPortChanges(): Flow<Int>

  fun setWhatsappListenerPort(port: Int)

  @CheckResult fun listenForWhatsappTokenChanges(): Flow<String>

  fun setWhatsappToken(token: String)
}
