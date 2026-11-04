# MiVisor TV · Entrega 3f: tele, tablet y móvil, Inicio nuevo, Canales e iconos

Se conserva todo lo anterior y el paquete `com.mivisor.tv`. Los datos no cambian. Las entregas anteriores están en `docs/entregas/`. La 3e, con la 3e.1 y la 3e.2, está en `docs/entregas/3e.md`.

## 1. La app sabe en qué aparato está

**Cómo lo detecta.** Usa lo que Android dice de verdad, en este orden:
1. Modo de interfaz «televisión» o Leanback → **tele**.
2. Sin pantalla táctil (cajas con mando) → **tele**.
3. Táctil con lado corto de 600 dp o más → **tablet**. Si no, **móvil**.

Si algún aparato se identifica mal, se puede forzar en Ajustes › Pantalla y rendimiento › **Tipo de pantalla**: Automático, Televisión, Tablet o Móvil.

**Tamaño en cualquier tele.** El diseño mide siempre 960 dp de ancho, sea 720p, 1080p o 4K y de las pulgadas que sea. Antes, algunas teles que declaran una densidad rara veían la app diminuta o enorme.

**Qué cambia según el aparato:**

| | Tele | Tablet | Móvil |
|---|---|---|---|
| Menú | Barra arriba (o lateral, en Ajustes) | Barra arriba (o lateral, en Ajustes) | Lateral fino solo con iconos |
| Foco con anillo | Siempre | Solo si usas mando o teclado | Solo si usas mando o teclado |
| Reproductor | OK muestra los controles | **Un toque** los muestra y otro los oculta. Flecha ← para salir | Igual que tablet |
| Orientación | La de la tele | Horizontal | Horizontal |
| Favorito | Mantener OK | Mantener pulsado | Mantener pulsado |

> **Pendiente:** en el móvil la app va siempre en horizontal. Las pantallas están pensadas en apaisado; la versión vertical del móvil sería otra entrega.

## 2. Menú arriba

El rail de la izquierda se sustituye por una **barra superior**, como en Google TV o Netflix:
- **Izquierda:** la Órbita y la marca.
- **Centro:** Inicio, Canales, Agenda y Mi lista.
- **Derecha:** iconos de Buscar y Ajustes, el estado del motor y de WARP, y la hora.

Con el mando se llega pulsando ↑ desde el contenido. Así el contenido gana todo el ancho.

Si prefieres el lateral, ve a Ajustes › Pantalla y rendimiento › **Menú › Lateral**. Recupera el rail del diseño 06 tal como estaba.

## 3. Inicio nuevo (diseño 06)

En este orden:

1. **Portada grande.**
   - **Con evento destacado:** es el evento en directo o el próximo que tenga canal en tus proveedores, con sus escudos y los colores de los equipos. Tiene un botón principal, «Ver ahora», que reproduce al momento, y uno secundario, «Dónde verlo» u «Otras opciones».
   - **Sin evento destacado:** «Seguir viendo» con tu último canal o, si no hay ninguno, una portada que lleva a Canales y a Agenda.
2. **En emisión y a continuación:** los directos y lo que empieza en las próximas 3 horas.
3. **Continuar viendo** y **Próximas citas**, lado a lado (en pantallas estrechas, uno debajo del otro).
   - **Continuar viendo:** tus 3 últimos canales. OK los reproduce.
   - **Próximas citas:** lo importante de las próximas 24 horas, primero lo que tiene recordatorio. Cada fila tiene un botón **Recordar / Avisaré** que pone o quita el recordatorio ahí mismo.
4. **Favoritos:** hasta 4 y una tarjeta «Ver todos», que lleva a Mi lista.
5. **Competiciones seguidas:**
   - **Qué muestra cada tarjeta:** cuántos directos hay o cuál es el próximo evento.
   - **Si no sigues ninguna:** salen como sugeridas LaLiga, Champions, Premier, F1 y MotoGP.
   - **Cómo seguir una:** en la página de una competición hay un botón nuevo, **Seguir competición**.
   - **Copia cifrada:** las competiciones seguidas van en ella.
6. **Agenda próxima:** las siguientes citas por orden de hora, con la competición y el canal.

Las filas de canales por marca que había antes en Inicio se han quitado. Para eso está Canales.

## 4. Canales sin columnas laterales

Las dos columnas de **Proveedores** y **Grupos** se han quitado. Ahora:

- **Arriba:**
  - un selector: **Proveedores · Categorías · Favoritos**;
  - una fila de **fichas con los proveedores** (Todos, Movistar Plus+, DAZN…);
  - dentro de un proveedor, una segunda fila de fichas con sus **grupos**, para saltar directamente a uno.
- **La rejilla** ocupa todo el ancho, con los canales **agrupados con su título**:
  - «M+ LaLiga · 3 canales», con LaLiga, 2 y 3;
  - «M+ Liga de Campeones · 2 canales»…
  - y, al final, «Otros canales».
- **Las tarjetas:**
  - el logo grande ocupa toda la tarjeta, con el nombre completo y el estado debajo;
  - **columnas:** 4 en la tele y 3 en el móvil; en una tablet grande, las que quepan.
- **Se mantiene lo de siempre:** OK reproduce, mantener OK añade a Mi lista y al volver del reproductor el foco sigue en el mismo canal.

## 5. Iconos sin «borde dentro de borde»

Al cargar cada icono, la app mira sus píxeles. La regla tiene pruebas en `LogoShape`.

- **Si el icono trae su propio fondo** (una tarjeta blanca, azul…, quizá con su marco):
  - se recorta su margen transparente y su marco;
  - la tarjeta de la app **toma el color de ese fondo**;
  - se ve **una sola pieza**, sin el recuadro de la app alrededor.
- **Si es un logo suelto sobre transparente:** va sobre el fondo de la app. Si es oscuro y no se leería, va sobre una **placa clara**.
- **Si el fondo es una foto o un degradado:** se recorta a sangre para llenar la tarjeta.

Vale para los iconos de picons_dobleM y para los de cualquier otra fuente. La memoria que usa está acotada, como el resto de imágenes, para que no pese en teles de 1 GB.

## 6. Otros cambios

- **Nombre de la APK:** siempre `MiVisor-TV-1.0.N.apk`, sin «-sin-control-WARP». La comprobación SHA-256 sigue igual. Si la firma es o no la de WARP lo dicen el resumen de la compilación y la release.
- **Colores:** sigo con la paleta del diseñador (fondo grafito azulado y acento champán). Funciona bien en tele y no cansa a oscuras. Si al verla en la tele sigue sin convencerte, se cambia en un solo sitio (`Theme.kt`).

## 7. Qué probar

| # | Prueba | Debe pasar |
|---|---|---|
| 1 | Abrir en la tele | Barra arriba. ↑ desde el contenido llega a la barra; ←/→ recorren las secciones |
| 2 | Abrir en la tablet | Barra arriba, sin anillos de foco al tocar. Va en horizontal |
| 3 | Abrir en el móvil | Lateral fino con iconos. Va en horizontal |
| 4 | Ajustes › Pantalla › Tipo de pantalla | Al cambiarlo, el menú y el tamaño cambian al momento |
| 5 | Ajustes › Pantalla › Menú › Lateral | Vuelve el rail lateral |
| 6 | Inicio | Portada, «En emisión…», los dos paneles, Favoritos, Competiciones y Agenda próxima |
| 7 | Próximas citas › Recordar | Cambia a «Avisaré» y sale el mensaje. Otra vez lo quita |
| 8 | Una competición › Seguir competición | Al volver a Inicio aparece en «Competiciones seguidas» |
| 9 | Canales › Movistar | Fichas de grupos; la rejilla con «M+ LaLiga», «M+ Liga de Campeones»… con sus títulos |
| 10 | Un icono con fondo blanco y marco | Una sola tarjeta blanca, sin el recuadro oscuro alrededor |
| 11 | Reproductor en tablet | Un toque muestra los controles y otro los oculta; la flecha ← sale |

## Compilación

- **Pruebas:** pasan las **189 pruebas** de los módulos JVM. Son nuevas las de tipo de aparato y escala y las de iconos con fondo propio.
- **Código Android:** lleva las revisiones de imports y referencias de siempre, pero **no se ha compilado aquí ni se ha probado en ningún aparato**. Se comprueba en GitHub Actions; si falla, pásame el log.
- **Fuera de este proyecto no se ha tocado nada:** ni la Raspberry, ni MiVisor Web, ni MiVisor Agenda, ni `mivisor-datos`, ni la v18, ni Outpace en producción.
