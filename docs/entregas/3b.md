# MiVisor TV · Entrega 3b (reproducción e interfaz)

La 3b va encima de la 3a, que ya compila y funciona. No hay que configurar nada nuevo: la firma y la pre-release automática siguen igual (ver `README.md`). Lo que trajo la 3a está en `docs/entregas/3a.md`.

## Lo que trae

### Reproducción

- **Un corte real se recupera solo.** Si la imagen se para mientras ves un canal, se recupera por escalones:
  1. Espera unos segundos, porque el buffer suele volver solo.
  2. A los 8 s, salta al directo.
  3. A los 16 s, recarga la misma fuente.
  4. A los 26 s, cambia a la siguiente fuente.

  Arriba aparece un aviso discreto («Recuperando la imagen…»). La fiabilidad apunta cuánto costó recuperarla.
- **Quitar la red o WARP a mitad no castiga fuentes.** Antes de culpar a una fuente se mira si el fallo es general (sin Internet, WARP caída, motor, poca memoria). Si lo es, no se penaliza ni se cambia de fuente: se espera a que vuelva («Sin conexión: esperando a que vuelva la red…») y se recarga la misma.
- **Multiaudio.** El idioma de audio que elijas en un canal se recuerda y se vuelve a poner la próxima vez que lo veas.
- **WARP por proveedor.**
  - Si una fuente es de un proveedor marcado como «necesita WARP», MiVisor TV conecta WARP antes de probarla y espera a que esté lista.
  - Si WARP no se puede usar (no instalada, con otra firma o sin autorizar), esa fuente se salta **sin penalizarla** y el aviso lo explica.
  - Con la APK firmada con la clave de desarrollo, WARP no se puede controlar desde la app. Esas fuentes solo funcionan si conectas WARP a mano.
- **Ace Stream como alternativa** (Ajustes › Motor):
  - **Motor:** Outpace (recomendado) o Ace Stream. Con Ace Stream, cada canal se abre en la app oficial con su mejor fuente, y se vuelve a MiVisor TV con Atrás.
  - **Si Outpace no da imagen:** Preguntar (botón «Probar con Ace Stream»), Automático o Nunca.
  - **Desde el menú de fuentes:** mantener OK sobre una fuente la abre en Ace Stream.
- **Cambiar de canal.**
  - Con los controles ocultos, ▲▼ o CH+/CH− cambian de canal dentro del mismo grupo.
  - «Canal anterior» está en el reproductor y en Canales, y también responde a la tecla de canal anterior del mando.
- **Enlaces externos.** Los enlaces de los proveedores que no son Ace Stream (Telegram, TVGram, magnet, webs) aparecen en Canales › Por temática › «Enlaces externos». Se abren con la app que Android tenga para cada uno, igual que en la v18.

### Interfaz, más cerca del prototipo

- **Barra superior:** marca «M MiVisor», pestañas con un punto bajo la elegida, ajustes como icono, una pastilla con el estado de Outpace y WARP, y la hora.
- **Rótulos de canal:** «DAZN / LALIGA», «M+ / DEPORTES», «EURO / SPORT 1», con los tonos del prototipo (claro, oscuro, azul y rojo).
- **Tarjetas de canal:**
  - Llevan el resplandor y el rótulo en el centro, y debajo el nombre y el estado con su punto («Funciona», «Sin probar», «En pausa»…).
  - Indican «Multiaudio» y si el canal está en Mi lista.
- **Canales:**
  - Cabecera con recuento y atajos (Buscar canal, Favoritos, Canal anterior, Todos los canales).
  - «Grupos» por marca y «Por temática» en bloques de color. Cada uno abre su rejilla de canales.
- **Inicio:**
  - Portada con el evento destacado: colores de los equipos, escudos de texto, «EN DIRECTO», y los botones «Ver ahora», «Otras opciones» y «Favorito».
  - Debajo, «En directo» con tarjetas de evento, «Seguir viendo», «Mi lista» y los grupos.
- **Reproductor:**
  - Arriba, el rótulo, el nombre, «EN DIRECTO» y la hora.
  - Abajo, los datos de la fuente y los botones Pausa, Audio y subtítulos, Fuente *x* de *y*, Canales, Canal anterior, Mi lista e Imagen.
  - Una línea de ayuda explica cómo cambiar de canal.
- **Mi lista:** pestañas Favoritos e Historial.

Siguen siendo **placas de texto, no logos**: los logos de canal llegan en la 3c. Por eso los escudos de los equipos usan las siglas sobre un color estable, en lugar de las imágenes reales.

## Lista de comprobación en la tele (T)

| # | Comprobación |
|---|---|
| 1 | Mientras ves un canal, desenchufa la red o desconecta WARP 30 s. Sale «Sin conexión: esperando…». Al volver la red, se recupera **la misma** fuente, y en la ficha del canal la fuente **no** aparece «En pausa» |
| 2 | Un corte real del canal (sin tocar la red) muestra «Recuperando la imagen…» y vuelve solo, o pasa a otra fuente si no puede |
| 3 | En un canal multiaudio, cambia el idioma en Audio y subtítulos; sal y vuelve a entrar: está el idioma elegido |
| 4 | Con Ace Stream instalada, en Ajustes › Motor elige Ace Stream: el canal se abre en su app y **Atrás** vuelve a MiVisor TV |
| 5 | Con «Si Outpace no da imagen: Preguntar», un canal sin imagen ofrece «Probar con Ace Stream» |
| 6 | ▲▼ cambia de canal dentro del grupo; «Canal anterior» vuelve al último visto |
| 7 | Canales › «Enlaces externos» abre un enlace de Telegram, TVGram o magnet con su app |
| 8 | Con el mando se llega a todo lo nuevo: tarjetas de grupo y temática, portada de Inicio, controles del reproductor y pestañas de Mi lista |

## Qué queda fuera

- **Ace Stream «dentro» de MiVisor TV** (su motor con el reproductor de MiVisor) queda para más adelante. En la 3b, Ace Stream se abre en su propia app, que es lo que la especificación pide comprobar.
- **Logos, «Dónde verlo» completo, competiciones y recordatorios:** 3c.
- **Copias, diagnóstico completo y verificador:** 3d.

## Compilación

Las pruebas del núcleo pasan aquí: **167 en verde**, entre ellas los escalones de recuperación, los rótulos, el cambio de canal y los enlaces externos. El código Android se ha revisado con las mismas comprobaciones que en la 3a, pero no se ha podido compilar en este entorno. Si la ejecución de GitHub falla, pasadme su log y lo corrijo.

No se ha modificado nada de la Raspberry, MiVisor Web, MiVisor Agenda, la v18 ni Outpace en producción.
