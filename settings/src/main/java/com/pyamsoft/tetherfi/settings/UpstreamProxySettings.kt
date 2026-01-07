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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import kotlinx.coroutines.launch

@Composable
fun UpstreamProxySettings(
    modifier: Modifier = Modifier,
    proxyPreferences: ProxyPreferences,
) {
  val scope = rememberCoroutineScope()
  
  val isEnabled by proxyPreferences.listenForUpstreamProxyEnabledChanges()
      .collectAsStateWithLifecycle(initialValue = false)
  
  val host by proxyPreferences.listenForUpstreamProxyHostChanges()
      .collectAsStateWithLifecycle(initialValue = "")
  
  val port by proxyPreferences.listenForUpstreamProxyPortChanges()
      .collectAsStateWithLifecycle(initialValue = 0)

  var hostInput by remember(host) { mutableStateOf(host) }
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
          text = "Configuración de Proxy Upstream",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
      )
      
      Spacer(modifier = Modifier.height(8.dp))
      
      // Descripción
      Text(
          text = "Activa esta opción si este dispositivo debe conectarse a otro proxy (modo repetidor). " +
                  "Dispositivo A (con Internet) = desactivado. " +
                  "Dispositivos B/C (intermedios) = activado con IP de dispositivo anterior.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      
      Spacer(modifier = Modifier.height(16.dp))
      
      // Switch principal
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1F)) {
          Text(
              text = "Actuar como cliente proxy",
              style = MaterialTheme.typography.bodyLarge,
          )
          Text(
              text = if (isEnabled) "Este dispositivo se conectará al proxy configurado" 
                     else "Este dispositivo NO usará proxy upstream",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Checkbox(
            checked = isEnabled,
            onCheckedChange = { enabled ->
              scope.launch {
                proxyPreferences.setUpstreamProxyEnabled(enabled)
              }
            },
        )
      }
      
      // Campos de configuración (solo si está habilitado)
      if (isEnabled) {
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = hostInput,
            onValueChange = { 
              hostInput = it
            },
            label = { Text("IP del proxy upstream") },
            placeholder = { Text("Ej: 192.168.49.1") },
            supportingText = { Text("Dirección IP del dispositivo que comparte Internet") },
            singleLine = true,
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
        
        // Botón para guardar configuración
        androidx.compose.material3.Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
              scope.launch {
                proxyPreferences.setUpstreamProxyHost(hostInput)
                val portValue = portInput.toIntOrNull() ?: 8228
                proxyPreferences.setUpstreamProxyPort(portValue)
              }
            },
            enabled = hostInput.isNotBlank() && portInput.toIntOrNull() != null,
        ) {
          Text("Guardar configuración")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Info actual guardada
        if (host.isNotBlank()) {
          Text(
              text = "✓ Configuración guardada: $host:$port",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.tertiary,
          )
        }
      }
    }
  }
}
