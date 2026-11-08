# MiVisor TV · Entrega 3n: «Abrir un ID» (con QR desde el móvil)

La 3m está en `docs/entregas/3m.md`. Se mantienen el paquete, los datos, la firma y la verificación de WARP.

## Ajustes › Abrir un ID

Sirve para ver un ID o enlace de Ace Stream que te pasa alguien y que no está en tus canales.

- **Qué se puede pegar:**
  - el ID de 40 caracteres;
  - `acestream://…`;
  - un enlace con `?id=`, `content_id=` o `getstream?id=`;
  - un mensaje entero con el ID en medio.

  El ID se saca solo; si no hay ninguno, lo dice.
- **Desde el móvil:** a la derecha hay un QR.
  1. Lo escaneas con el móvil.
  2. Pegas allí el ID o el enlace y lo envías.
  3. Llega al campo de la tele; no se abre solo.

  Funciona solo en la red de casa y el QR caduca, igual que el de «Añadir proveedor».
- **Nombre opcional:** se le puede poner un nombre («Partido de Juan»).
- **Abrir:** se ve con Outpace, como cualquier canal. Se reintenta solo si se corta.
- **Abiertos hace poco:** los últimos 8. OK para volver a abrirlo; mantener OK para quitarlo de la lista.
- **Qué no toca:** no añade nada a tus proveedores ni a tus canales, y no se guarda como último canal visto.

## Compilación

- **Pruebas:** pasan las **232 pruebas** JVM. Hay 3 nuevas: qué textos se aceptan como ID y cuáles no; por ejemplo, un código de proveedor de 24 caracteres no vale.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
