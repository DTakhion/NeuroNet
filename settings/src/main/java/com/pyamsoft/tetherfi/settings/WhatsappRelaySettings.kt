package com.pyamsoft.tetherfi.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
fun WhatsappRelaySettings(
    modifier: Modifier = Modifier,
    proxyPreferences: ProxyPreferences,
) {
  val scope = rememberCoroutineScope()

  val isWhatsappMode by
      proxyPreferences.listenForWhatsappProxyModeChanges().collectAsStateWithLifecycle(false)
  val role by proxyPreferences.listenForProxyRoleChanges().collectAsStateWithLifecycle(ProxyRole.SERVER_ONLY)
  val finalHost by proxyPreferences.listenForWhatsappFinalHostChanges().collectAsStateWithLifecycle("")
  val finalPort by proxyPreferences.listenForWhatsappFinalPortChanges().collectAsStateWithLifecycle(443)
  val nextHopHost by proxyPreferences.listenForWhatsappNextHopHostChanges().collectAsStateWithLifecycle("")
  val nextHopPort by proxyPreferences.listenForWhatsappNextHopPortChanges().collectAsStateWithLifecycle(0)
  val listenerPort by proxyPreferences.listenForWhatsappListenerPortChanges().collectAsStateWithLifecycle(0)
  val token by proxyPreferences.listenForWhatsappTokenChanges().collectAsStateWithLifecycle("")

  val isEdge = role == ProxyRole.SERVER_ONLY

  var finalHostInput by remember(finalHost) { mutableStateOf(finalHost) }
  var finalPortInput by remember(finalPort) { mutableStateOf(finalPort.takeIf { it > 0 }?.toString() ?: "443") }
  var nextHopHostInput by remember(nextHopHost) { mutableStateOf(nextHopHost) }
  var nextHopPortInput by remember(nextHopPort) { mutableStateOf(nextHopPort.takeIf { it > 0 }?.toString() ?: "0") }
  var listenerPortInput by remember(listenerPort) { mutableStateOf(listenerPort.takeIf { it > 0 }?.toString() ?: "0") }
  var tokenInput by remember(token) { mutableStateOf(token) }

  Card(
      modifier = modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(MaterialTheme.keylines.content)) {
      Text(
          text = "Modo WhatsApp (TCP Relay)",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1F)) {
          Text(
              text = "Activa un listener TCP que encadena saltos solo para WhatsApp",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
            checked = isWhatsappMode,
            onCheckedChange = { enabled ->
              scope.launch { proxyPreferences.setWhatsappProxyMode(enabled) }
            },
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
          modifier = Modifier.fillMaxWidth(),
          value = listenerPortInput,
          onValueChange = { value -> listenerPortInput = value.filter { it.isDigit() }.take(5) },
          label = { Text("Puerto de escucha local") },
          placeholder = { Text("0 desactiva") },
          supportingText = { Text("Configura el puerto que WhatsApp usará como proxy local") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          enabled = isWhatsappMode,
      )

      Spacer(modifier = Modifier.height(12.dp))

      if (isEdge) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = finalHostInput,
            onValueChange = { finalHostInput = it.trim() },
            label = { Text("Host proxy final (A)") },
            placeholder = { Text("proxy.publico.com") },
            singleLine = true,
            enabled = isWhatsappMode,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = finalPortInput,
            onValueChange = { value -> finalPortInput = value.filter { it.isDigit() }.take(5) },
            label = { Text("Puerto proxy final") },
            placeholder = { Text("443") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = isWhatsappMode,
        )
      } else {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = nextHopHostInput,
            onValueChange = { nextHopHostInput = it.trim() },
            label = { Text("Host siguiente salto") },
            placeholder = { Text("192.168.x.x") },
            singleLine = true,
            enabled = isWhatsappMode,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = nextHopPortInput,
            onValueChange = { value -> nextHopPortInput = value.filter { it.isDigit() }.take(5) },
            label = { Text("Puerto siguiente salto") },
            placeholder = { Text("8228") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = isWhatsappMode,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
          modifier = Modifier.fillMaxWidth(),
          value = tokenInput,
          onValueChange = { tokenInput = it.trim() },
          label = { Text("Token (opcional)") },
          placeholder = { Text("Encabezado simple para hop") },
          singleLine = true,
          enabled = isWhatsappMode,
      )

      Spacer(modifier = Modifier.height(16.dp))

      val listener = listenerPortInput.toIntOrNull()?.takeIf { it in 1..65535 } ?: 0
      val finalPortValue = finalPortInput.toIntOrNull()?.takeIf { it in 1..65535 } ?: 0
      val nextHopPortValue = nextHopPortInput.toIntOrNull()?.takeIf { it in 1..65535 } ?: 0

      val isTargetValid =
          if (isEdge) finalHostInput.isNotBlank() && finalPortValue in 1..65535
          else nextHopHostInput.isNotBlank() && nextHopPortValue in 1..65535

      val canSave = isWhatsappMode && listener in 1..65535 && isTargetValid

      Button(
          modifier = Modifier.fillMaxWidth(),
          enabled = canSave,
          onClick = {
            scope.launch {
              proxyPreferences.setWhatsappProxyMode(true)
              proxyPreferences.setWhatsappListenerPort(listener)
              proxyPreferences.setWhatsappToken(tokenInput)

              if (isEdge) {
                proxyPreferences.setWhatsappFinalHost(finalHostInput)
                proxyPreferences.setWhatsappFinalPort(finalPortValue)
              } else {
                proxyPreferences.setWhatsappNextHopHost(nextHopHostInput)
                proxyPreferences.setWhatsappNextHopPort(nextHopPortValue)
              }
            }
          },
      ) {
        Text("Guardar modo WhatsApp")
      }
    }
  }
}
