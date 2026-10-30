# MiVisor TV · Entrega 3a (base)

Proyecto nuevo, listo para subirlo a un repositorio privado y generar la primera APK de prueba. **No hace falta configurar nada.** Sin secretos, se firma como en la v18, con `app/debug.keystore`. Esa APK funciona en todo menos en el control de WARP. Ver `README.md` → Firma.

## Lo que trae

- **Proyecto y CI.**
  - Multimódulo en Kotlin con las versiones probadas de la v18: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21 y Compose 1.7.
  - El workflow compila Outpace, pasa las pruebas, firma con el procedimiento de la v18 (secretos o, si no hay, `app/debug.keystore`) y comprueba la APK.
- **Proveedores.**
  - Se añaden por código, escrito con el mando o con el móvil (QR).
  - El código se comprueba antes de guardarlo, se guarda cifrado con AES-GCM y el Android Keystore, y no se vuelve a mostrar.
  - Se pueden renombrar y eliminar, y cada uno se puede marcar como «necesita WARP».
- **Canales.**
  - Los de todos los proveedores se juntan por nombre real: «DAZN LaLiga» es un único canal, con las fuentes de cada proveedor.
  - Están agrupados por marcas y tipos (las secciones de la Web 7.7.0), con búsqueda tolerante a erratas y Mi lista (favoritos e historial).
- **Reproducción.**
  - Usa Outpace integrado, HLS por 127.0.0.1 y Media3, con el buffer adaptativo y el control del retraso de la v18.
  - Elige la fuente con la **fiabilidad de la fase 2**:
    - aprende solo con la app abierta y guarda el historial entre sesiones;
    - las pausas y la cuarentena tienen límites y reintentos;
    - los fallos de Internet, WARP, el motor o la memoria no penalizan a las fuentes.
  - Menú de fuentes, audio y subtítulos, pausa y ajuste de imagen.
- **WARP.**
  - Estado, conectar, desconectar y autorizar, con el mismo protocolo de la v18.
  - Comprobación de firma por la **huella SHA-256 completa**, no por el CN.
- **Agenda básica.** Directo y próximos por deporte, desde `mivisor-datos` en GitHub, con copia local y ETag. Al elegir un evento se abre uno de tus canales. No depende de la Raspberry ni la toca.
- **Interfaz.**
  - Diseño premium de la fase 2: tokens, tipografía Inter, foco con anillo blanco y escala 1,06, zona segura de TV y texto de 12 sp como mínimo.
  - Barra superior con Inicio, Agenda, Canales, Buscar, Mi lista y Ajustes.
  - Asistente de primera vez y modo ligero.
- **Privacidad.**
  - `allowBackup=false`.
  - El registro se limpia antes de guardarse: sin códigos, Content IDs completos, URLs ni IPs públicas.
  - Nada se ejecuta en segundo plano. Al salir de la app se para la reproducción y, poco después, Outpace.

## Lista de comprobación

A = lo comprueba la CI en cada compilación. T = hay que probarlo en vuestra tele.

| # | Comprobación | Cómo |
|---|---|---|
| 1 | No hay claves privadas, contraseñas ni binarios en el repositorio; solo está la clave de desarrollo de la v18 | A |
| 2 | Outpace se compila desde el commit fijado `7d77f410…` y su licencia coincide | A |
| 3 | Pruebas del núcleo: 164 en la JVM (proveedores, canales, agenda, fiabilidad con los vectores de la fase 2, redacción, motor, móvil, WARP) y las de navegación de la app | A |
| 4 | La APK lleva Outpace para ARMv7 y ARM64, sus licencias y ningún fichero de clave | A |
| 5 | Se compara la huella SHA-256 de la APK con la de WARP. Si coincide, OK. Con la clave de desarrollo, aviso «no podrá controlar WARP» y nombre `…-sin-control-WARP.apk`. Con secretos que no coinciden, la CI falla | A |
| 6 | Se instala **junto a la v18** sin desinstalar nada | T |
| 7 | Solo con la APK firmada con los secretos: Ajustes › WARP muestra «Correcto: misma huella» y conecta y desconecta. Con la de desarrollo debe decir «Huella distinta» | T |
| 8 | Lee vuestros proveedores; los canales repetidos aparecen como uno solo con varias fuentes | T |
| 9 | Abre canales en una tele **ARMv7** y en otra **ARM64**; Ajustes › Motor muestra «En marcha» | T |
| 10 | «Escribir con el móvil» rellena el código desde el móvil, en la misma Wi-Fi | T |
| 11 | Con el mando se llega a todo: pestañas, rejillas, fichas, reproductor, menús y diálogos. Atrás siempre vuelve | T |
| 12 | Quitar la red a mitad de un intento no marca la fuente: Ficha del canal › «Sin probar» o «Funciona», no «En pausa» | T |
| 13 | Tras cerrar y abrir la app, la ficha del canal mantiene el estado de las fuentes | T |

Para los puntos T, decidnos la marca, el modelo, la versión de Android y la memoria de cada tele.

## Diferencias con la especificación de la fase 2

| En la especificación | En la 3a | Por qué |
|---|---|---|
| Room | SQLite del sistema (`core:storage`), con esquema versionado | Room obliga a añadir KSP, y esta compilación no se puede probar aquí. El cambio a Room es interno y puede hacerse más adelante sin perder datos |
| `tv-material` 1.1.0 | Componentes propios sobre Compose Foundation, como la v18 y el prototipo | Mantener las versiones probadas de la v18 sin subir Kotlin ni AGP |
| Media3 1.11.x | Media3 1.4.1, la de la v18 | La versión que ya funciona con Outpace. Admite Android 6 |
| Coil y logos | Placas con iniciales | Los logos llegan con la agenda completa |
| Módulos `:providers` y `:agenda` | Paquetes dentro de `core:catalog` | Menos módulos y el mismo aislamiento (sin Android) |
| Pruebas de foco con capturas en tres resoluciones (CI) | Pendiente | Requieren un emulador de TV en la CI. Mientras tanto, el punto 11 se prueba en la tele |

Además, la fiabilidad estaba prevista para la 3b y ya viene en esta entrega.

## Lo que no está y cuándo llega

Ajustes › Próximamente lo enseña también dentro de la app.

- **3b:** Ace Stream externo, recuperación automática y canal anterior, enlaces externos.
- **3c:** agenda completa («Dónde verlo», competiciones, recordatorios), logos y vista previa.
- **3d:** copia de seguridad cifrada, diagnóstico completo con informe al móvil y verificador (desactivado por defecto).

## Importante: compilación

Los módulos del núcleo (`core:*`, salvo `core:storage`) se han compilado y probado aquí: **164 pruebas en verde**.

El código Android (pantallas, almacenamiento, Outpace en Android, WARP y reproductor) **no se ha podido compilar aquí**: este entorno no tiene acceso a los repositorios de Google ni de Maven. Se ha escrito sobre las mismas versiones y, donde era posible, con el mismo código de la v18 que ya compila. Además, se ha revisado a mano y con comprobaciones automáticas de imports y referencias.

Aun así, **la primera ejecución de la CI puede dar algún error de compilación**. Si pasa, mandadnos el log de la ejecución, que no contiene secretos, y lo corregimos en una corrección de esta misma entrega. No hemos probado la app en ninguna tele.

## Datos

- **Agenda.** Por defecto se lee `https://raw.githubusercontent.com/JimmyTonato/mivisor-datos/main/agenda.json`. El repositorio tiene que ser público, porque la app no lleva tokens. Se puede cambiar en Ajustes › Agenda o al compilar con `-PagendaUrl=…`.
- **Proveedores.** Se leen por DNS con el mismo dominio y el mismo decodificador que la v18.
- No se ha modificado la Raspberry, MiVisor Web, MiVisor Agenda, la v18, Outpace en producción ni ningún repositorio de producción.
