#!/usr/bin/env bash
# Muestra la huella SHA-256 del certificado de una APK (para comparar MiVisor TV con WARP).
# Uso: bash tools/warp-fingerprint.sh MiVisor-TV-1.0.N.apk [WARP.apk]
# Necesita apksigner (Android SDK build-tools). No necesita ninguna clave ni contraseña.
set -euo pipefail
APKSIGNER="${APKSIGNER:-$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/*/ 2>/dev/null | sort -V | tail -1)apksigner}"
for apk in "$@"; do
  fp=$("$APKSIGNER" verify --print-certs "$apk" | grep -m1 "certificate SHA-256 digest" | awk '{print $NF}' | tr 'a-f' 'A-F' | sed 's/../&:/g; s/:$//')
  echo "$apk  $fp"
done
if [ $# -eq 2 ]; then
  a=$("$APKSIGNER" verify --print-certs "$1" | grep -m1 "SHA-256" | awk '{print $NF}')
  b=$("$APKSIGNER" verify --print-certs "$2" | grep -m1 "SHA-256" | awk '{print $NF}')
  if [ "$a" = "$b" ]; then echo "OK: misma firma (MiVisor TV podrá controlar WARP)"; else echo "DISTINTA: MiVisor TV no podrá controlar esa WARP"; exit 1; fi
fi
