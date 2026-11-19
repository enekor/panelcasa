#!/bin/sh
# Capturas del panel con Chrome 119 sin ventana, a los anchos reales de la tablet (1280x800):
# 1184 px = ancho completo menos el panel lateral de 96 px; 789 px = dos tercios de ese espacio.
# Uso: tools/screenshots.sh [ruta a chrome]
set -e
cd "$(dirname "$0")/.."
CHROME="${1:-/root/panelcasa-tools/chrome-linux64/chrome}"
OUT=tools/out
mkdir -p "$OUT"
shot() {
  "$CHROME" --headless=new --no-sandbox --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --window-size="$1,800" --virtual-time-budget=2000 --screenshot="$OUT/$2.png" \
    "file://$(pwd)/panel/dev.html?$3" >/dev/null 2>&1
  echo "$OUT/$2.png"
}
shot 1184 completo-A "mode=A&alerts=1"
shot 1184 completo-B "mode=B"
shot 789 dostercios-A "mode=A&alerts=1"
shot 789 dostercios-B-teorico-sinconexion "mode=B&rt=0&stale=1"
shot 1184 sin-datos "empty=1"
