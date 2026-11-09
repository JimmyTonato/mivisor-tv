# MiVisor TV · Entrega 3s: «Aceptar conexiones de otros (P2P completo)»

La 3r está en `docs/entregas/3r.md`. Se mantiene todo lo de la 3r.

## Por qué

Hay canales que salen con su nombre (el descriptor llega bien) pero se quedan en 0 emisores, mientras que en Ace Stream
oficial o en CanalPeer sí se ven. Pasa con canales de muy poca gente: si el que lo comparte está detrás de su router y
nosotros no aceptamos conexiones, nadie puede conectar con nadie. Ace Stream oficial acepta conexiones de otros y pide al
router que abra el puerto; hasta ahora MiVisor no.

## Qué cambia

- **Ajustes › Motor › «Aceptar conexiones de otros (P2P completo)»** (apagado por defecto). Al activarlo, el motor:
  - escucha conexiones de otros espectadores en el puerto 18621 (si está ocupado, en otro libre);
  - se anuncia para que lo encuentren y comparte lo que ya ha descargado;
  - pide al router que abra ese puerto (UPnP o NAT-PMP, lo que tenga el router).
  - Como mucho 8 conexiones entrantes (4 en aparatos con poca memoria).
- Cambiar el interruptor reinicia el motor al momento.
- La API del motor sigue solo en 127.0.0.1.
- **Con WARP conectado no sirve**: nadie puede entrar por WARP. El ajuste lo avisa.
- **Pantalla de carga**: si tras 25 s sigue sin emisores y el P2P completo está apagado, lo sugiere. El mensaje final de
  «0 emisores» también lo dice.
- **Diagnóstico (Ajustes › Motor y el informe)**: al arrancar dice si el motor va en «P2P completo» y en qué puerto, o
  «solo saliente».

## Cómo probarlo

1. Abre el canal que fallaba con el interruptor apagado: confirma que se queda en 0 emisores.
2. Actívalo (con WARP apagado) y vuelve a abrirlo.
3. Si sigue sin emisores, manda el informe de diagnóstico: dirá si el router abrió el puerto.

## Compilación

- **Pruebas:** pasan las **246 pruebas** JVM (4 nuevas sobre la configuración del motor en P2P completo).
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
- Firma, paquete `com.mivisor.tv` y comprobación SHA-256 de WARP: sin cambios.
