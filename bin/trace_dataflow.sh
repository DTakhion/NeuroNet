#!/bin/bash

# TetherFi Data Flow Testing Script
# Propósito: Reproducir flujo D→C→B→A y capturar logs
# Uso: ./trace_dataflow.sh <loglevel>
#
# Niveles de log:
#   D = DEBUG (muestra todo)
#   I = INFO  (solo INFO/WARN/ERROR)
#   V = VERBOSE (habilita flag de logging verbose)

set -e

LOGLEVEL="${1:-D}"
LOGFILE="tetherfi_trace_$(date +%s).log"

echo "╔════════════════════════════════════════════════════════════╗"
echo "║  TetherFi Data Flow Trace: D→C→B→A                        ║"
echo "║  $(date '+%Y-%m-%d %H:%M:%S')                                  ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "Capturando logs en: $LOGFILE"
echo "Nivel de log: $LOGLEVEL"
echo ""

# Habilitar logs en adb (requiere dispositivo conectado via USB o emulador)
if ! adb devices | grep -q "device$"; then
    echo "⚠️  ADVERTENCIA: No hay dispositivo Android conectado."
    echo "   Conecta un dispositivo via USB o inicia un emulador."
    exit 1
fi

# Limpiar logs anteriores
adb logcat -c

# Iniciar captura de logs en background
adb logcat -v threadtime | tee "$LOGFILE" &
LOGCAT_PID=$!

echo "PID del logcat: $LOGCAT_PID"
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║  PASOS A REALIZAR EN EL DISPOSITIVO:                      ║"
echo "║  1. Abre la aplicación TetherFi                           ║"
echo "║  2. Activa el hotspot (botón ON/toggle)                   ║"
echo "║  3. Conecta otro dispositivo (WiFi o USB)                 ║"
echo "║  4. Abre navegador en dispositivo cliente                 ║"
echo "║  5. Accede a http://www.google.com o similar              ║"
echo "║  6. Verifica que funcione la navegación                   ║"
echo "║  7. Presiona Ctrl+C aquí cuando termines (espera ~30s)   ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Esperar input del usuario
read -p "Presiona ENTER para comenzar a capturar logs..."

echo ""
echo "⏳ Capturando durante 60 segundos..."
echo "   Realiza las acciones de prueba ahora..."
echo ""

sleep 60

echo ""
echo "⏹️  Deteniendo captura de logs..."
kill $LOGCAT_PID 2>/dev/null || true
wait $LOGCAT_PID 2>/dev/null || true

# Procesar logs
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║  ANÁLISIS DE FLUJO D→C→B→A                               ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

echo "📊 RESUMEN DE EVENTOS CAPTURADOS:"
echo ""

echo "🔴 PUNTO D (Aceptación de conexiones):"
grep -i "\[D-ACCEPT\]\|\[D-.*\]" "$LOGFILE" | tail -10 || echo "   (sin eventos)"
echo ""

echo "🟠 PUNTO C (Relé de datos):"
grep -i "\[C-RELAY\]" "$LOGFILE" | tail -10 || echo "   (sin eventos)"
echo ""

echo "🟡 PUNTO B (Binding/Conexión remota):"
grep -i "\[B-CONNECT\]" "$LOGFILE" | tail -10 || echo "   (sin eventos)"
echo ""

echo "🟢 PUNTO A (Selección de red upstream):"
grep -i "ÉXITO.*Red.*adquirida\|Preferred.*network\|[A]-" "$LOGFILE" | tail -10 || echo "   (sin eventos)"
echo ""

echo "🔵 SYNC (Sincronización de estado):"
grep -i "\[SYNC" "$LOGFILE" | tail -10 || echo "   (sin eventos)"
echo ""

echo "⚠️  ERRORES Y ADVERTENCIAS:"
grep -i "ERROR\|WARN\|EXCEPTION" "$LOGFILE" | head -20 || echo "   (ninguno)"
echo ""

echo "✅ Logs guardados en: $LOGFILE"
echo ""
echo "💾 Para análisis posterior ejecuta:"
echo "   grep '\[D-ACCEPT\]\|\[C-RELAY\]\|\[B-CONNECT\]\|\[SYNC' $LOGFILE"
echo ""
