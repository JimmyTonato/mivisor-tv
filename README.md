# MiVisor TV

App de Android TV de MiVisor (entrega 3a). Es un proyecto nuevo, con el paquete `com.mivisor.tv`, y se instala junto a la APK v18 (`com.mivisor.app`) sin reemplazarla.

- Android 6.0 o posterior.
- ARMv7 y ARM64, las mismas arquitecturas que Outpace.
- APK privada, compilada con GitHub Actions. No se publica en ninguna tienda.

## Firma: el mismo procedimiento que la v18

**No hace falta configurar nada para compilar.** Funciona igual que la v18:

| Situación | Con qué se firma | ¿Controla WARP? |
|---|---|---|
| Sin secretos (lo normal ahora) | `app/debug.keystore`, la **misma** clave de desarrollo de la v18 (no se genera otra) | **No.** Su huella no es la de WARP. La CI lo avisa y la APK sale con el nombre `…-sin-control-WARP.apk` |
| Con los cuatro secretos de la v18 | El keystore de MiVisor/WARP que da GitHub Actions | Sí, si su huella coincide con la de WARP. Si no coincide, la compilación falla |

Para que la APK pueda controlar WARP más adelante, hay que copiar los valores de estos secretos del repositorio de la v18 al nuevo, en Settings → Secrets and variables → Actions:

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

La variable `WARP_SIGNER_SHA256` es opcional. Si no se pone, se usa la misma huella que en la v18.

Al pasar de la clave de desarrollo a la de WARP cambia la firma, así que hay que desinstalar MiVisor TV una vez antes de instalar la nueva.

La comprobación de la huella SHA-256 frente a WARP **se hace siempre**:
- Si coincide, todo sigue normalmente.
- Si no coincide con la clave de desarrollo, la APK se entrega, pero marcada como no compatible con WARP en el aviso, en el resumen de la ejecución y en el propio nombre del archivo.
- Si no coincide con los secretos, la compilación falla.

La agenda no se genera en este repositorio. La app la lee de `mivisor-datos` y aquí no hay workflow de agenda.

## Cómo se genera la APK

1. Sube el contenido del ZIP a la rama `main` del repositorio nuevo, que debe ser privado.
2. Se lanza solo el workflow **MiVisor TV (APK)**. También se puede lanzar a mano desde la pestaña *Actions → Run workflow*.
3. Pasos del workflow:
   1. Comprueba que en el repositorio no hay claves privadas ni contraseñas.
   2. Compila Outpace desde el commit fijado de la v18.
   3. Pasa las pruebas.
   4. Compila la release con R8 y la firma con los secretos o, si no están, con `app/debug.keystore`.
   5. Compara la huella con la de WARP y comprueba que Outpace y sus licencias están dentro.
4. La APK `MiVisor-TV-1.0.<n>.apk` (o `…-sin-control-WARP.apk`) y su `.sha256` aparecen como artefacto de la ejecución durante 30 días. Solo la ven quienes tienen acceso al repositorio.
5. Opcional: al subir una etiqueta `v…` (por ejemplo `v1.0.0-prueba1`), se crea además una *release* de prueba con la APK.

## Detalles de la firma

- `app/build.gradle.kts` funciona como la v18:
  - Si existe `SIGNING_KEYSTORE_PATH`, usa ese keystore con `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` y `SIGNING_KEY_PASSWORD`.
  - Si no, usa `app/debug.keystore`, con las contraseñas estándar de Android.
- Solo las releases firmadas con la clave de los secretos declaran el permiso `com.mivisor.permission.CONTROL_WARP` (de tipo *signature*) desde `app/src/warpSigned`, igual que la v18.
- `app/debug.keystore` es la clave de desarrollo de la v18. La clave privada de MiVisor/WARP nunca está en el repositorio.
- `tools/check-no-secrets.sh` lo comprueba en cada compilación, y `tools/warp-fingerprint.sh` compara la firma de dos APK.

## Estructura

| Módulo | Qué hace |
|---|---|
| `core:model`, `core:net`, `core:catalog` | Proveedores por DNS (de la v18), agrupación de canales, agenda y búsqueda |
| `core:reliability` | Fiabilidad de fuentes: traducción literal del modelo de la fase 2, probada con sus vectores |
| `core:engine` | Supervisor de Outpace, buffer y retraso respecto al directo (de la v18) |
| `core:remote`, `core:services`, `core:diagnostics` | Escribir con el móvil, protocolo de WARP y limpieza del registro |
| `core:storage` | SQLite local y códigos cifrados con el Android Keystore |
| `engine:outpace`, `feature:warp`, `feature:playback` | Outpace en Android, control de WARP y reproducción con Media3 |
| `ui:design` | Tokens, tipografía Inter y componentes de TV del diseño de la fase 2 |
| `app` | Pantallas, navegación y montaje de todo |

Los módulos `core:*`, salvo `core:storage`, no dependen de Android y tienen sus propias pruebas JUnit.
