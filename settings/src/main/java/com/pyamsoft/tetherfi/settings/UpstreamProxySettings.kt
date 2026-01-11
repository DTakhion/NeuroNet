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

package com.pyamsoft.tetherfi.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyamsoft.pydroid.theme.keylines
import com.pyamsoft.tetherfi.server.ProxyPreferences
import com.pyamsoft.tetherfi.server.ProxyRole
import kotlinx.coroutines.launch

@Composable
fun UpstreamProxySettings(
    modifier: Modifier = Modifier,
    proxyPreferences: ProxyPreferences,
) {
  val scope = rememberCoroutineScope()
  
  val isEnabled by proxyPreferences.listenForUpstreamProxyEnabledChanges()
    .collectAsStateWithLifecycle(initialValue = false)
  val isAutoGateway by proxyPreferences.listenForUpstreamAutoGatewayChanges()
    .collectAsStateWithLifecycle(initialValue = true)
  val role by proxyPreferences.listenForProxyRoleChanges()
    .collectAsStateWithLifecycle(initialValue = ProxyRole.SERVER_ONLY)
  val host by proxyPreferences.listenForUpstreamProxyHostChanges()
    .collectAsStateWithLifecycle(initialValue = "")
  val port by proxyPreferences.listenForUpstreamProxyPortChanges()
    .collectAsStateWithLifecycle(initialValue = 0)

  val isRelay = role == ProxyRole.RELAY
  val isClient = role == ProxyRole.CLIENT_ONLY
  val isServer = role == ProxyRole.SERVER_ONLY

  var hostInput by remember(host, isAutoGateway) { mutableStateOf(host) }
  var portInput by remember(port) { mutableStateOf(if (port > 0) port.toString() else "8228") }

  Card(
      modifier = modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
      ),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.keylines.content),
    ) {
      // Título de sección
      Text(
          text = "Rol y Proxy Upstream",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Selector de rol
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoleChip(
            label = "A: Solo servidor",
            selected = isServer,
        ) {
          scope.launch {
            proxyPreferences.setProxyRole(ProxyRole.SERVER_ONLY)
            proxyPreferences.setUpstreamProxyEnabled(false)
          }
        }
        Spacer(modifier = Modifier.width(8.dp))
        RoleChip(
            label = "B: Repetidor",
            selected = isRelay,
        ) {
          scope.launch {
            proxyPreferences.setProxyRole(ProxyRole.RELAY)
            proxyPreferences.setUpstreamProxyEnabled(true)
          }
        }
        Spacer(modifier = Modifier.width(8.dp))
        RoleChip(
            label = "C: Solo cliente",
            selected = isClient,
        ) {
          scope.launch {
            proxyPreferences.setProxyRole(ProxyRole.CLIENT_ONLY)
            proxyPreferences.setUpstreamProxyEnabled(false)
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
          text = when {
            isServer -> "Este dispositivo comparte Internet. Upstream desactivado."
            isRelay -> "Este dispositivo repetirá tráfico usando un proxy upstream."
            else -> "Este dispositivo actúa solo como cliente, no inicia proxy."
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Estado del upstream (solo informativo, controlado por rol)
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1F)) {
          Text(
              text = "Cliente proxy upstream (controlado por rol)",
              style = MaterialTheme.typography.bodyLarge,
          )
          Text(
              text = if (isEnabled) "Activado (modo repetidor)" else "Desactivado",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Checkbox(
            enabled = false,
            checked = isEnabled,
            onCheckedChange = {},
        )
      }

      // Configuración visible solo en modo repetidor
      if (isRelay) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1F)) {
            Text(
                text = "Gateway automático (Wi‑Fi Direct)",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Usa 192.168.49.1 u host del grupo si está disponible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Checkbox(
              checked = isAutoGateway,
              onCheckedChange = { enabled ->
                scope.launch { proxyPreferences.setUpstreamAutoGateway(enabled) }
              },
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val autoText = "Auto (192.168.49.1)"
        val displayedHost = if (isAutoGateway && hostInput.isBlank()) autoText else hostInput

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = displayedHost,
            onValueChange = { newValue -> hostInput = newValue },
            label = { Text("IP del proxy upstream") },
            placeholder = { Text(autoText) },
            supportingText = {
              Text(
                  if (isAutoGateway)
                      "Se autocompleta. Desactiva auto para escribir manualmente."
                  else "Dirección IP del dispositivo que comparte Internet",
              )
            },
            singleLine = true,
            enabled = !isAutoGateway,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = portInput,
            onValueChange = {
              portInput = it.filter { char -> char.isDigit() }.take(5)
            },
            label = { Text("Puerto del proxy") },
            placeholder = { Text("8228") },
            supportingText = { Text("Puerto del proxy (por defecto 8228)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Spacer(modifier = Modifier.height(16.dp))

        val isPortValid = portInput.toIntOrNull() != null && portInput.toInt() in 1..65535
        val canSave = isAutoGateway || (hostInput.isNotBlank() && isPortValid)

        // Botón para guardar configuración
        androidx.compose.material3.Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
              scope.launch {
                proxyPreferences.setUpstreamProxyEnabled(true)
                proxyPreferences.setUpstreamProxyHost(if (isAutoGateway) "" else hostInput)
                val portValue = portInput.toIntOrNull() ?: 8228
                proxyPreferences.setUpstreamProxyPort(portValue)
              }
            },
            enabled = canSave,
        ) {
          Text("Guardar configuración")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isAutoGateway) "✓ Gateway automático activado"
            else "✓ Configuración guardada: $host:$port",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
      }
      else {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isClient) "Modo cliente: no se inicia proxy. Conéctate al repetidor."
            else "Modo servidor: comparte Internet sin usar upstream.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun RoleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Button(
      onClick = onClick,
      enabled = true,
      shape = MaterialTheme.shapes.small,
      colors = ButtonDefaults.buttonColors(
          containerColor =
              if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceVariant,
          contentColor =
              if (selected) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurfaceVariant,
      ),
  ) {
    Text(text = label)
  }
}
