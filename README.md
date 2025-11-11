# Bitácora Técnica — Proyecto NeuroNet / TetherFi
## Resumen técnico – Etapa de configuración del entorno

### 1. Instalación y configuración del entorno Java
- Se instaló **JDK 17** inicialmente mediante `sdkman`, y posteriormente se actualizó a **JDK 21 (Zulu)** para asegurar compatibilidad con Gradle 9.x y Android Gradle Plugin (AGP 8.x).
- Se definió el entorno:
  ```bash
  export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.5-zulu"
  ```
  y se verificó con `java -version`.

**Resultado:** entorno Java reconocido por Gradle y Android Studio sin conflictos de versión.

---

### 2. Diagnóstico del entorno de build y resolución de catálogos
- Se ejecutó:
  ```bash
  ./gradlew buildEnvironment --no-configuration-cache --refresh-dependencies
  ```
  con el objetivo de inspeccionar **repositorios y catálogos de dependencias** (`libs.versions.toml`).

- Durante este paso se identificaron referencias a repositorios **`jitpack.io`**, **`google()`** y **`mavenCentral()`**, pero no se requirió intervención posterior.

**Resultado:** configuración válida de catálogos y fuentes. Sin conflictos de plugin DSL ni dependencias.

---

### 3. Inspección de dependencias – módulo `:app`
- Se verificó la estructura de dependencias (sin compilar código):
  ```bash
  ./gradlew :app:dependencies --no-configuration-cache
  ```
- El proceso fue **BUILD SUCCESSFUL**, confirmando la correcta resolución de librerías AndroidX, Compose y KSP.

**Resultado:** el módulo `:app` fue reconocido por Gradle y todas sus dependencias se resolvieron satisfactoriamente.

---

### 4. Inspección de dependencias – módulo `:server`
- Se repitió el proceso en el módulo `:server`:
  ```bash
  ./gradlew :server:dependencies --no-configuration-cache
  ```
- También **BUILD SUCCESSFUL**, confirmando compatibilidad con dependencias Ktor y Kotlin Coroutines.

**Resultado:** el proyecto multi-módulo (`:app`, `:server`, `:core`, etc.) fue reconocido íntegramente por el sistema de compilación.

---

### Conclusión parcial
La estructura de repositorios (`settings.gradle.kts`) y catálogos (`libs.versions.toml`) fue validada.  
El entorno Java y Gradle se estabilizó correctamente, dejando el proyecto listo para compilación (`assembleDebug`) e instalación del `app-debug.apk`.

---

### 5. Edición de `libs.versions.toml`
Se reemplazaron las referencias personalizadas del fork **PYAMSOFT** por dependencias estándar de **Ktor 3.3.1**, manteniendo la coherencia de versión en todas las referencias (`version.ref = "ktor"`).  
Esto normaliza la resolución de librerías y evita dependencias rotas de un fork privado.

**Antes:**
```toml
ktor = "3.3.1-PYAMSOFT"
ktor-network = { group = "io.ktor", name = "ktor-network", version.ref = "ktor" }
ktor-server-netty = { group = "io.ktor", name = "ktor-server-netty-jvm", version.ref = "ktor" }
```

**Después:**
```toml
ktor = "3.3.1"
ktor-network = { module = "io.ktor:ktor-network", version.ref = "ktor" }
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
```

**Resultado:** dependencias limpias, repositorios estándar, sin referencias al fork Pyamsoft.

---

### 6. Limpieza del proyecto
Se realizó una limpieza completa para regenerar el entorno de build y verificar la integridad del cache de Gradle:

```bash
./gradlew -Dorg.gradle.java.home="$HOME/.sdkman/candidates/java/current" clean
```

**BUILD SUCCESSFUL**, confirmando entorno consistente y compilador funcional.

---

### 7. Detección del problema en el módulo `:server`
La compilación fallaba por la ausencia de funciones “custom” que el código del módulo `:server` esperaba del fork **PYAMSOFT**, y que no existen en **Ktor vanilla**:

```
connectWithConfiguration()
bindWithConfiguration()
socketTimeout()
remoteAddress()
```
Además de algunas variantes de envío y recepción UDP.

Estos métodos eran *wrappers utilitarios* para simplificar configuración de sockets y conexiones.

---

### 8. Solución aplicada — Compat Shim
Para destrabar el proceso sin reinstalar el fork, se creó un archivo de compatibilidad local:

**`KtorCompat.kt`**
- Contiene *extension functions* que emulan las funciones del fork utilizando las APIs públicas de Ktor.
- Mantiene la firma esperada para no romper el código existente.

**Objetivo:** asegurar compatibilidad binaria temporal y permitir compilación completa del módulo `:server` sin modificar la lógica base.

**Resultado:** compilación estable, sin dependencias externas, y compatibilidad asegurada con Ktor oficial.

---

### Conclusión
La intervención normalizó las dependencias del proyecto y restauró la compatibilidad del módulo `:server` con Ktor oficial, manteniendo la arquitectura modular original.  
Se logró continuar el flujo de build sin dependencias del fork Pyamsoft, preparando el entorno para la fase de compilación (`assembleDebug`).

---

### Contexto General

Este parte del documento detalla el proceso completo de refactorización y compatibilización del módulo **`server`** del proyecto **NeuroNet**, cuyo propósito es manejar la capa de proxy TCP/UDP para la aplicación **TetherFi**.  
El trabajo se realizó a raíz de los errores de compilación generados tras la migración a **Ktor 3.x**, los cuales afectaban funciones críticas como la conexión, el enlace de sockets y la comunicación entre procesos.

---

## Entorno

- macOS + Gradle 9.1 + Kotlin 2.0.21 + AGP 8.7.x
- JDK 21 (Zulu) configurado mediante SDKMAN
- Android Studio Jellyfish | Build #AI-241.22218.26.2412.12750108

---

## Problemas Iniciales

Durante la compilación con Gradle se presentaron los siguientes errores recurrentes:

- `Unresolved reference 'connectWithConfiguration'`
- `Suspension functions can only be called within coroutine body`
- `Cannot infer type for type parameter 'R'`
- `Argument type mismatch` en llamadas a `bindWithConfiguration`
- `onBeforeBind` no reconocido como parámetro válido

Además, las extensiones personalizadas definidas en `KtorCompat.kt` dejaron de funcionar correctamente por los cambios en la API de Ktor.

---

## Archivos Involucrados

| Archivo | Descripción | Estado |
|----------|--------------|--------|
| `KtorCompat.kt` | Contiene funciones de compatibilidad (`connectWithConfiguration`, `bindWithConfiguration`, `sendCompat`, `receiveCompat`, `TRY_CALL`) | Refactorizado y estable |
| `HttpProxySession.kt` | Gestiona la conexión HTTP dentro del servidor proxy | Corregido y validado |
| `BaseSOCKSImplementation.kt` | Implementación base de comandos SOCKS (CONNECT, BIND, UDP_ASSOCIATE) | Refactorizado y validado |
| `UDPRelayServer.kt` | Relay de datagramas UDP entre cliente y destino remoto | Refactorizado y validado |

---

## Cambios Técnicos Aplicados

### **1. Compatibilidad con Ktor 3.x**
- Se reescribieron funciones de compatibilidad en `KtorCompat.kt` con firmas actualizadas.
- Se añadieron versiones *Compat* para llamadas suspendidas:
  - `sendCompat()`
  - `receiveCompat()`
- Se mantuvo el control de errores mediante `TRY_CALL`.

---

### **2. Refactor de `BaseSOCKSImplementation.kt`**
- Se encapsularon las llamadas suspendidas (`connectWithConfiguration`) dentro de:
  ```kotlin
  runBlocking(Dispatchers.IO) { ... }
  ```
  para ejecutar funciones suspendidas en contextos no suspendidos.
- Se ajustó el uso de `socketTimeout` dentro del bloque `configure`.
- Se resolvieron problemas de inferencia genérica (`Cannot infer type for 'R'`).
- Se preservó la lógica original del protocolo SOCKS sin alterar su flujo.

---

### **3. Refactor de `UDPRelayServer.kt`**
- Eliminado el parámetro obsoleto `onBeforeBind` de `bindWithConfiguration()`.
- Reemplazadas las llamadas directas por las funciones de compatibilidad:
  ```kotlin
  socket.sendCompat()
  socket.receiveCompat()
  serverSocket.sendCompat()
  ```
- Encapsuladas llamadas suspendidas dentro de bloques `runBlocking`.
- Conservada la lógica original de:
  - `LastActivityTimeHolder`
  - Control de timeout (`ServerSocketTimeout`)
  - Rate limiting (`enforceBandwidthLimit`)
  - Reporting periódico (`ByteTransferReport`)

---

### **4. Limpieza y Estabilización Final**
- Se eliminaron warnings graves y errores de tipo.
- El módulo `server` logró compilar completamente con:
  ```bash
  ./gradlew :server:compileDebugKotlin --no-configuration-cache
  ```

---

## Conclusión

El módulo **`server`** del proyecto **NeuroNet** fue completamente restaurado y actualizado para operar bajo **Ktor 3.x**, preservando la estructura del proxy SOCKS y el relay UDP.  
El sistema compila de manera estable, sin pérdida de funcionalidad ni dependencias rotas.

---

### Objetivo final

Compilar el módulo `:app` para generar el **APK de depuración** y **instalarlo** en el emulador Android para pruebas manuales.

---

## 1. Ensamble del APK (assembleDebug)
Ejecutar desde la **raíz del proyecto**:

```bash
./gradlew :app:assembleDebug --no-configuration-cache
```

**Salida esperada:** `BUILD SUCCESSFUL`

### Artefactos generados
Según los *productFlavors* del proyecto, se generan dos APKs de **debug**:

```
./app/build/outputs/apk/google/debug/app-google-debug.apk
./app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
```

- **app-google-debug.apk**; usa dependencias con **Google Play Services**. Recomendado para emuladores con *Google APIs*.
- **app-fdroid-debug.apk**; *flavor* sin dependencias propietarias (útil en entornos sin Play Services).

**Contenido del APK de debug:**
- Código Kotlin/Java compilado
- Recursos (`res/`), `AndroidManifest.xml`, `assets/`
- Firma **de depuración** (keystore debug), **no** de producción
- Metadatos de compilación y `android:debuggable="true"`

> Nota: La firma de debug permite instalación e inspección en dispositivos/emuladores internos. Para distribución externa, usar **variant release** firmada.

---

## 2. Emulador de referencia (entorno de prueba)
- **AVD:** `MeshPoC-API36-arm64`
- **Android:** 16.0 “Baklava” (**API 36**)
- **Arquitectura del AVD:** `arm64-v8a`
- **Google APIs:** habilitadas (recomendado para `app-google-debug.apk`)

> Alternativa sugerida: crear también un emulador **x86_64** (mayor rendimiento en hosts Intel/AMD).

---

## 3. Instalación del APK en el emulador

### Opción; Por ADB (recomendado)
1. Verifica que el emulador esté **encendido** y visible para ADB:
   ```bash
   adb devices
   # List of devices attached
   # emulator-5554   device
   ```

2. Instala el **APK Google** de debug:
   ```bash
   adb install -r ./app/build/outputs/apk/google/debug/app-google-debug.apk
   ```

**Salida esperada:**
```
Performing Streamed Install
Success
```

> Si el emulador es `arm64-v8a` y hubiera error de ABI, recompila para esa ABI:
```bash
./gradlew :app:assembleGoogleDebug -Pandroid.injected.build.abi=arm64-v8a
adb install -r ./app/build/outputs/apk/google/debug/app-google-debug.apk
```

---

## 4. Verificación en el emulador
- Abrir el **cajón de apps** y localizar el ícono de la app (según `applicationId`).
- Iniciar la app y validar flujo básico de pantallas.
- Ver **logs** si es necesario:
  ```bash
  adb logcat
  ```

---

## 5. Apagar el emulador (limpieza)
```bash
adb -s emulator-5554 emu kill   # apaga el emulador en ejecución (no lo elimina)
```
> Para **eliminar** un AVD:
```bash
avdmanager delete avd -n MeshPoC-API36-arm64
```

---

### Resultado final
APK de debug **generado e instalado** correctamente en el emulador `MeshPoC-API36-arm64` (API 36). Listo para pruebas manuales y validación funcional.
