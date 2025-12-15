# Instrumentación de Logging para Trazar Flujo D→C→B→A

## Descripción General

Este documento describe cómo usar la infraestructura de logging agregada a TetherFi para rastrear problemas de estabilidad en la transmisión de datos entre dispositivos.

**Flujo de datos:** D (cliente) → C (relé) → B (conexión remota) → A (upstream/Internet)

---

## 📊 Infraestructura Implementada

### 1. **TraceLoggingManager** (`core/TraceLogging.kt`)

Utilidad centralizada para logging con:
- **UUID de sesión:** Identificador único para correlacionar logs
- **Counter incremental:** Numeración secuencial de eventos (TRACE-SESSION-00001, 00002, etc.)
- **Control de verbosidad:** Flag `isVerboseLoggingEnabled` para evitar spam
- **Niveles automáticos:** DEBUG en builds de desarrollo, INFO/WARN/ERROR en release

**Uso:**
```kotlin
// En cualquier clase inyectada
@Inject lateinit var traceLogger: TraceLoggingManager

// Loggear evento de sincronización
traceLogger.logWarn("SYNC:StatusBroadcast.set", "Estado: $oldStatus → $newStatus")

// Loggear flujo de datos
traceLogger.logDataFlow('C', "Relayando datos", bytes = 1024)
```

---

## 🔴 Puntos de Logging Instrumentados

### Punto D: Aceptación de Conexiones (Dispositivo)
**Archivo:** `server/src/main/java/.../TcpProxyManager.kt`
**Método:** `ensureAcceptedConnection()`

```kotlin
val connection = server.accept()
traceLogger?.logDataFlow('D', "Socket aceptado de cliente: ${connection.remoteAddress}")
Timber.d("[D-ACCEPT] Conexión entrante aceptada: ${connection.remoteAddress}")
```

**Buscar en logs:** `[D-ACCEPT]` o `[D-.*]`

---

### Punto C: Relé de Datos (Proxy)
**Archivo:** `server/src/main/java/.../TransportOperations.kt`
**Función:** `relayData()`

```kotlin
// Inicio de relés
Timber.d("[C-RELAY] Iniciando proxy→internet para ${client.info.address}")
Timber.d("[C-RELAY] Iniciando internet→proxy para ${client.info.address}")

// Reportes periódicos (cada 5 segundos)
Timber.d("[C-RELAY] Reporte: proxy→internet=${proxyToInternetBytes.value}B, internet→proxy=${internetToProxyBytes.value}B")

// Finalización
Timber.d("[C-RELAY] Sesión finalizada: total proxy→internet=$finalP2I B, internet→proxy=$finalI2P B")
```

**Buscar en logs:** `[C-RELAY]`

---

### Punto B: Binding/Conexión Remota (SOCKS)
**Archivo:** `server/src/main/java/.../BaseSOCKSImplementation.kt`
**Método:** `connect()`

```kotlin
Timber.d { "[B-CONNECT] Socket conectado a destino remoto: $remote para cliente ${client.info.address}" }
Timber.d { "[C-RELAY] Iniciando relayData para conexión SOCKS a $remote" }
```

**Buscar en logs:** `[B-CONNECT]`

---

### Punto A: Selección de Red Upstream
**Archivo:** `service/src/main/java/.../AndroidUpstreamNetworkSelector.kt`

**Logs existentes (ya presentes):**
```kotlin
Timber.d("Solicitando red upstream preferida: $preferred")
Timber.d("ÉXITO: Red upstream adquirida ($preferred): $primaryNetwork")
Timber.w("ADVERTENCIA: Falla adquisición de $preferred...")
Timber.e("CRÍTICO: No se pudo obtener conexión a Internet...")
```

**Buscar en logs:** `ÉXITO.*Red` o `CRÍTICO` o `Solicitando red`

---

### Sincronización de Estado (SYNC)
**Archivo:** `server/src/main/java/.../BaseStatusBroadcaster.kt` + `app/tile/ProxyTileService.kt`

```kotlin
// Status Broadcaster
traceLogger?.logWarn("SYNC:StatusBroadcast.set", "Estado anterior: $old → nuevo: $status")
Timber.d("[SYNC-STATE] Status transition: $old → $status")

// Tile UI
Timber.d { "[SYNC:TILE] Usuario hizo clic en tile, acción: TOGGLE" }
Timber.d { "[SYNC:TILE] Servicio está ejecutándose" }
```

**Buscar en logs:** `[SYNC`

---

## 🚀 Cómo Usar

### Opción 1: Captura Manual con ADB

```bash
# Terminal 1: Iniciar captura de logs
adb logcat -v threadtime | tee tetherfi_trace.log

# Terminal 2: Abrir aplicación, activar hotspot, conectar cliente
# ... realizar acciones de prueba ...

# Terminal 1: Presionar Ctrl+C para detener
```

### Opción 2: Script Automatizado (Linux/Mac)

```bash
# Dar permisos de ejecución
chmod +x bin/trace_dataflow.sh

# Ejecutar
./bin/trace_dataflow.sh

# Seguir instrucciones en pantalla
```

El script:
1. Limpia logs anteriores
2. Captura logs en tiempo real
3. Solicita pasos a realizar en el dispositivo
4. Analiza logs automáticamente
5. Genera resumen de eventos

---

## 📈 Análisis de Logs

### Buscar Eventos de Punto Específico

```bash
# Punto D (Aceptación)
grep "[D-ACCEPT]" tetherfi_trace.log

# Punto C (Relé)
grep "[C-RELAY]" tetherfi_trace.log

# Punto B (Conexión remota)
grep "[B-CONNECT]" tetherfi_trace.log

# Punto A (Red upstream)
grep "ÉXITO.*Red\|CRÍTICO" tetherfi_trace.log

# Sincronización
grep "[SYNC" tetherfi_trace.log
```

### Buscar Errores

```bash
# Todos los errores
grep "ERROR\|EXCEPTION" tetherfi_trace.log

# Errores en puntos críticos
grep "[D-ACCEPT].*ERROR\|[C-RELAY].*ERROR\|[B-CONNECT].*ERROR" tetherfi_trace.log
```

### Rastrear Sesión Completa

```bash
# Ver todos los eventos de una sesión (por UUID)
# Los logs incluyen TRACE-<UUID>-<COUNTER>
grep "TRACE-.*-00001" tetherfi_trace.log  # Primer evento
grep "TRACE-.*-00002" tetherfi_trace.log  # Segundo evento
# etc.
```

---

## 🧪 Pruebas Automatizadas

### Ejecutar Tests de Flujo de Datos

```bash
# Desde raíz del proyecto
./gradlew :server:test --tests DataFlowTest

# Tests específicos
./gradlew :server:test --tests DataFlowTest.testDeviceConnectionAcceptance
./gradlew :server:test --tests DataFlowTest.testDataRelayBidirectional
./gradlew :server:test --tests DataFlowTest.testCompleteDataFlowDCBA
```

**Ubicación:** `server/src/test/java/.../DataFlowTest.kt`

Tests incluyen:
- ✅ Aceptación de conexiones (D)
- ✅ Relé bidireccional (C)
- ✅ Conexión remota (B)
- ✅ Selección de red (A)
- ✅ Sincronización de estado (SYNC)
- ✅ Flujo completo D→C→B→A
- ✅ Manejo de errores

---

## 📋 Checklist de Diagnóstico

Cuando investigues problemas de estabilidad:

- [ ] ¿Se aceptan conexiones en D? Busca `[D-ACCEPT]`
- [ ] ¿Se relayan datos en C? Busca `[C-RELAY]` con bytes > 0
- [ ] ¿Se conecta remotamente en B? Busca `[B-CONNECT]` sin ERROR
- [ ] ¿Se selecciona red en A? Busca `ÉXITO.*Red` sin timeout
- [ ] ¿Se sincroniza estado? Busca `[SYNC-STATE]` y `[SYNC:TILE]`
- [ ] ¿Hay errores? Busca `ERROR`, `WARN`, `EXCEPTION`
- [ ] ¿Hay race conditions? Busca eventos simultáneos con timestamps cercanos

---

## 🛠️ Configuración Avanzada

### Habilitar/Deshabilitar Logging Verbose

```kotlin
// En BuildConfig o preferences
if (BuildConfig.DEBUG) {
    // En DEBUG builds, siempre verbose
    traceLogger.isVerboseLoggingEnabled = true
}

// En release, controlable por flag
preferences.listenForVerboseLogging().collect { enabled ->
    traceLogger.isVerboseLoggingEnabled = enabled
}
```

### Personalizar Formato de Logs

Edita `TraceLoggingManager.kt`:

```kotlin
// Cambiar formato de trace ID
fun nextTraceId(): String {
    val counter = eventCounter.incrementAndGet()
    return "[${System.currentTimeMillis()}]-$counter"  // Ejemplo: timestamp
}
```

---

## ⚠️ Notas Importantes

1. **Performance:** Los logs pueden afectar performance en dispositivos lentos. Usa `isVerboseLoggingEnabled` con cuidado en producción.

2. **Privacidad:** Los IPs y direcciones de clientes se loggean. Asegúrate de manejar logs sensibles correctamente.

3. **Persistencia:** Los logs de logcat se pierden después de cierto tiempo. Guarda logs importantes en archivos.

4. **Build Type:**
   - **DEBUG:** Todos los logs DEBUG habilitados automáticamente
   - **RELEASE:** Solo INFO/WARN/ERROR, verbose requiere flag explícito

---

## 📞 Soporte

Para problemas con logging:
1. Verifica que `TraceLoggingManager` está inyectado correctamente
2. Asegúrate de que Timber está configurado en la app
3. Revisa `BuildConfig.DEBUG` para confirmar build type
4. Ejecuta `./gradlew :server:test` para validar tests

---

## 🔗 Archivos Relacionados

- Core infrastructure: `core/src/main/java/.../TraceLogging.kt`
- Tests: `server/src/test/java/.../DataFlowTest.kt`
- Script: `bin/trace_dataflow.sh`
- Puntos de logging:
  - `server/.../TcpProxyManager.kt` (D)
  - `server/.../TransportOperations.kt` (C)
  - `server/.../BaseSOCKSImplementation.kt` (B)
  - `service/.../AndroidUpstreamNetworkSelector.kt` (A)
  - `server/.../BaseStatusBroadcaster.kt` + `app/tile/ProxyTileService.kt` (SYNC)
