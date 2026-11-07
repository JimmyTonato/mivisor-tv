# MiVisor TV · Entrega 3k.2: vista previa en Canales, verificador por proveedor y retoques de la tele

La 3j está en `docs/entregas/3j.md`. Se mantienen el paquete, los datos, la firma y la verificación de WARP.

## Lo que faltaba por desarrollar

- **Vista previa en vídeo en Canales** (el interruptor que salía «Todavía no disponible»).
  - Con el foco 2,5 s sobre un canal, su mejor fuente se ve **dentro de la tarjeta, sin sonido**, con la etiqueta «Sin sonido».
  - Usa un único reproductor pequeño, solo mientras estás en Canales. Se suelta al salir, al abrir un canal o al pasar la app a segundo plano.
  - La fuente se queda preparada un minuto por si la abres.
  - Por defecto está encendida, salvo en aparatos con poca memoria. Se cambia en Ajustes › Pantalla y rendimiento.
- **«No penalizar fuentes por fallos de la red, de la VPN o del aparato»:** el interruptor era solo decorativo; ahora funciona. Apagado, todo fallo cuenta para la fuente.
- **Verificador por proveedor** (Ajustes › Verificador).
  - Además de «Comprobar todo ahora», cada proveedor tiene su fila: comprueba solo sus fuentes (como mucho 80), por fases. OK otra vez para parar.
  - Cada resultado dice: fuentes, con imagen, sin imagen, **arranque medio** y **los 3 canales que antes arrancan**.
  - Se guarda la última comprobación de cada proveedor.

## Tele y tablet

- **Tele, un 6 % más pequeño:** pasa de 960 a 1024 dp de ancho.
- **Botones:** más bajos (44 dp) y con el texto un punto menor.
- **Proveedores:** las dos sincronizaciones son ahora filas con su explicación, para que no se corte nada. «Añadir manualmente» y «Releer todos» van como botones cortos.
- **Banners y escudos:** las acciones son ahora filas.
- **Escudos sin el círculo blanco detrás:** el escudo tal cual, con una sombra muy suave. Las fotos de los tenistas siguen en círculo.
- **3k.1 · Luchadores de UFC:** sus fotos son de cuerpo entero y salía el pantalón. Ahora, si la foto es alargada, se recorta desde arriba y se acerca a la cara. Las fotos de busto (tenistas) siguen igual.
- **Partidos principales:** los dos escudos juntos a la izquierda y los nombres al lado. El lado derecho queda libre para el logo de la competición (antes el del Real Madrid se ponía encima).
- **Portada grande con banner:** los escudos van hacia el centro, sin tapar el logo de la derecha.
- **Ficha del partido:** el banner mantiene la proporción de su franja (3,2:1) en cualquier ancho. En la tablet se ve entero, como en la tele.

## Compilación

- **3k.2:** la 3k.1 no compilaba porque `ChannelPreview.kt` importaba `EngineManager` del paquete equivocado (`engine` en vez de `outpace`). Corregido, y comprobado que todas las importaciones del proyecto existen.

- **Pruebas:** pasan las **228 pruebas** JVM (las de la densidad de la tele, actualizadas a 1024 dp).
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
- **Vista previa:** está pensada para que no moleste al reproductor principal (se suelta antes de abrir un canal), pero conviene probarla en el TV stick. Si va justo, se apaga en Ajustes.
