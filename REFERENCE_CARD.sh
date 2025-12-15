#!/bin/bash
# TetherFi Logging Reference Card
# Comandos rápidos para rastrear problemas D→C→B→A

echo "╔════════════════════════════════════════════════════════════╗"
echo "║     TARJETA DE REFERENCIA: LOGGING D→C→B→A               ║"
echo "║     Copiar/pegar comandos para análisis rápido            ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Define variables
LOGFILE="tetherfi_trace.log"

echo "═══════════════════════════════════════════════════════════════"
echo "CAPTURA DE LOGS"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "1️⃣  Script automatizado (RECOMENDADO):"
echo "   bash bin/trace_dataflow.sh"
echo ""

echo "2️⃣  Captura manual:"
echo "   adb logcat -v threadtime | tee $LOGFILE"
echo ""

echo "3️⃣  Ejecutar tests:"
echo "   ./gradlew :server:test --tests DataFlowTest"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "ANÁLISIS POR PUNTO"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "📍 PUNTO D: Aceptación (Dispositivo)"
echo "   grep '[D-ACCEPT]' $LOGFILE"
echo "   grep 'Socket aceptado' $LOGFILE"
echo ""

echo "📍 PUNTO C: Relé (Proxy)"
echo "   grep '[C-RELAY]' $LOGFILE"
echo "   grep '[C-RELAY].*bytes' $LOGFILE"
echo "   grep '[C-RELAY].*Reporte' $LOGFILE"
echo ""

echo "📍 PUNTO B: Conexión (SOCKS)"
echo "   grep '[B-CONNECT]' $LOGFILE"
echo "   grep 'destino remoto' $LOGFILE"
echo ""

echo "📍 PUNTO A: Red Upstream (Internet)"
echo "   grep 'ÉXITO.*Red' $LOGFILE"
echo "   grep 'CRÍTICO.*conexión' $LOGFILE"
echo "   grep 'Solicitando red' $LOGFILE"
echo ""

echo "📍 SYNC: Sincronización"
echo "   grep '[SYNC' $LOGFILE"
echo "   grep '[SYNC-STATE]' $LOGFILE"
echo "   grep '[SYNC:TILE]' $LOGFILE"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "BÚSQUEDAS DIAGNÓSTICAS"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "❓ ¿Fluyo completo?"
echo "   grep -c '[D-ACCEPT]\\|[C-RELAY]\\|[B-CONNECT]\\|ÉXITO' $LOGFILE"
echo "   (Debería ser > 3)"
echo ""

echo "❓ ¿Cuántos bytes pasaron?"
echo "   grep '[C-RELAY].*Sesión finalizada' $LOGFILE"
echo ""

echo "❓ ¿Hay errores?"
echo "   grep 'ERROR\\|EXCEPTION\\|CRÍTICO' $LOGFILE | wc -l"
echo ""

echo "❓ ¿Sesión duró cuánto?"
echo "   echo 'Primer evento:' && head -1 $LOGFILE | cut -d' ' -f2"
echo "   echo 'Último evento:' && tail -1 $LOGFILE | cut -d' ' -f2"
echo ""

echo "❓ ¿Se sincronizó UI?"
echo "   grep '[SYNC' $LOGFILE | wc -l"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "DETECCIÓN DE PROBLEMAS"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "⚠️  SIN ACEPTACIÓN:"
echo "   [ -z \"\$(grep '[D-ACCEPT]' $LOGFILE)\" ] && echo '❌ No acepta conexiones'"
echo ""

echo "⚠️  SIN DATOS:"
echo "   grep '[C-RELAY].*Reporte' $LOGFILE | grep '0B' && echo '❌ Sin bytes'"
echo ""

echo "⚠️  SIN CONEXIÓN REMOTA:"
echo "   [ -z \"\$(grep '[B-CONNECT]' $LOGFILE)\" ] && echo '❌ No conecta remotamente'"
echo ""

echo "⚠️  SIN RED:"
echo "   [ -z \"\$(grep 'ÉXITO.*Red' $LOGFILE)\" ] && echo '❌ Sin red upstream'"
echo ""

echo "⚠️  SIN SINCRONIZACIÓN:"
echo "   [ -z \"\$(grep '[SYNC' $LOGFILE)\" ] && echo '❌ Sin sincronización'"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "GENERACIÓN DE REPORTES"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "📊 Resumen completo:"
echo "   echo '=== PUNTO D ===' && grep '[D-ACCEPT]' $LOGFILE | tail -3"
echo "   echo '=== PUNTO C ===' && grep '[C-RELAY].*Sesión' $LOGFILE"
echo "   echo '=== PUNTO B ===' && grep '[B-CONNECT]' $LOGFILE | tail -1"
echo "   echo '=== PUNTO A ===' && grep 'ÉXITO' $LOGFILE | tail -1"
echo "   echo '=== SYNC ===' && grep '[SYNC-STATE]' $LOGFILE | tail -1"
echo ""

echo "📊 Archivo con solo eventos D-C-B-A:"
echo "   grep '[D-ACCEPT]\\|[C-RELAY]\\|[B-CONNECT]\\|ÉXITO\\|[SYNC' $LOGFILE > trace_filtered.log"
echo ""

echo "📊 Timeline (últimas 50 eventos):"
echo "   tail -50 $LOGFILE | grep -E '[D-ACCEPT]|[C-RELAY]|[B-CONNECT]|[SYNC'"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "COMPILACIÓN Y TESTING"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "🔨 Compilar:"
echo "   ./gradlew clean build"
echo ""

echo "🧪 Tests punto a punto:"
echo "   ./gradlew :server:test --tests DataFlowTest.testDeviceConnectionAcceptance"
echo "   ./gradlew :server:test --tests DataFlowTest.testDataRelayBidirectional"
echo "   ./gradlew :server:test --tests DataFlowTest.testRemoteConnectionBinding"
echo "   ./gradlew :server:test --tests DataFlowTest.testUpstreamNetworkSelection"
echo "   ./gradlew :server:test --tests DataFlowTest.testCompleteDataFlowDCBA"
echo ""

echo "🧪 Todos los tests:"
echo "   ./gradlew :server:test --tests DataFlowTest"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "INSTALACIÓN EN DISPOSITIVO"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "📱 Instalar APK debug:"
echo "   adb install app/build/outputs/apk/debug/tetherfi-debug.apk"
echo ""

echo "📱 Instalar APK release:"
echo "   adb install app/build/outputs/apk/release/tetherfi-release.apk"
echo ""

echo "📱 Abrir aplicación:"
echo "   adb shell am start -n com.pyamsoft.tetherfi/.MainActivity"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "LIMPIEZA"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "🧹 Limpiar logs de dispositivo:"
echo "   adb logcat -c"
echo ""

echo "🧹 Eliminar archivos de trace locales:"
echo "   rm -f tetherfi_trace_*.log"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "💡 TIP: Guardar este archivo como función en .bashrc:"
echo "   logref() { cat REFERENCE_CARD.sh }"
echo "   logref | grep 'PUNTO D'"
echo ""
