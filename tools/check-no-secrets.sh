#!/usr/bin/env bash
# Falla si el repositorio contiene claves, keystores o contraseñas. Se ejecuta en la CI antes de compilar
# y se puede lanzar a mano: bash tools/check-no-secrets.sh
set -uo pipefail
cd "$(dirname "$0")/.."
fail=0
files=$(git ls-files 2>/dev/null || find . -type f -not -path './.git/*' -not -path '*/build/*' -not -path './.gradle/*')

# 1) Ficheros de clave o de entorno. Única excepción: app/debug.keystore, la clave de DESARROLLO de la v18
#    (contraseñas estándar de Android, no es la clave de MiVisor/WARP), que se usa cuando no hay secretos.
bad=$(echo "$files" | sed 's#^\./##' | grep -v '^app/debug\.keystore$' | grep -Ei '\.(jks|keystore|p12|pfx|pem|key)$|(^|/)\.env($|\.)|local\.properties$|deploy_key|agenda\.env' || true)
if [ -n "$bad" ]; then echo "ERROR: ficheros de clave o de entorno en el repositorio:"; echo "$bad"; fail=1; fi

# 2) Contraseñas o claves escritas en el código o en la configuración ("android" es la estándar de la clave de desarrollo).
pat='(storePassword|keyPassword|SIGNING_[A-Z_]*PASSWORD)[[:space:]]*[=:][[:space:]]*["'"'"'][^"'"'"'$]{3,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}'
hits=$(echo "$files" | grep -Ev '^tools/check-no-secrets\.sh$|\.(png|jpg|otf|ttf|jar|so)$' | xargs -r grep -nIE "$pat" 2>/dev/null | grep -vE '(storePassword|keyPassword)[[:space:]]*=[[:space:]]*"android"[[:space:]]*$' || true)
if [ -n "$hits" ]; then echo "ERROR: posibles secretos escritos:"; echo "$hits"; fail=1; fi

# 3) Binarios del motor: los compila la CI desde el commit fijado (no se versionan).
so=$(echo "$files" | grep -E 'jniLibs/.*\.so$' || true)
if [ -n "$so" ]; then echo "ERROR: binarios .so versionados:"; echo "$so"; fail=1; fi

[ $fail -eq 0 ] && echo "OK: sin claves, contraseñas ni binarios del motor en el repositorio"
exit $fail
