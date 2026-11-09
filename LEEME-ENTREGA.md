# MiVisor TV · Entrega 3p: la pantalla de carga dice qué está pasando

La 3o (con la 3o.1) está en `docs/entregas/3o.md`. Se mantienen el paquete, los datos, la firma y la verificación de WARP.

## Pasos de la carga

En vez de un «Buscando señal…» genérico, la pantalla de carga dice en qué paso está y cuánto lleva:

1. **«Buscando el canal… 12 s»**: el motor busca el descriptor del canal en el catálogo de Ace Stream.
   - A partir de 6 s: «Localizando el canal en el catálogo… A veces tarda hasta un minuto: no cierres, sigue buscando».
   - A partir de 30 s, si WARP no está conectada: «Si tarda mucho, conecta WARP».
2. **«Canal encontrado · buscando emisores… 8 s»**: ya tiene el descriptor.
   - Debajo pone en cuánto se encontró y «Esperando a quien lo emite».
   - A partir de 25 s sin emisores: «Nadie lo está emitiendo todavía: puede que la emisión esté caída». Aquí ya tiene sentido rendirse.
3. **«Conectado a N emisores · preparando la imagen…»**.

Cómo se sabe el paso: mientras el motor no tiene sesión de la fuente, sigue buscando el descriptor. Con sesión y 0 emisores, está buscando emisores. Con emisores, está preparando la imagen.

En el registro de fuentes queda «canal encontrado en N s».

## Si no sale imagen

El mensaje final dice **dónde se quedó**:
- **No encontró el canal en el catálogo:** suele ser el bloqueo de la operadora (conecta WARP). Si con WARP tampoco, pueden haber cambiado los servidores del catálogo (Ajustes › Motor).
- **El canal existe pero tiene 0 emisores:** la emisión está caída.
- **Había emisores pero no llegó imagen a tiempo:** reintentar suele bastar.

## Compilación

- **Pruebas:** pasan las **236 pruebas** JVM.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
