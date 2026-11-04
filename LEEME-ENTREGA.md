# MiVisor TV · Entrega 3e: Canales y reproductor del diseño 06.1

La 3e va encima de la 3d y la sustituye entera.

Se basa en el paquete del diseñador, **MiVisor_TV_06_1**. Sus documentos están en `docs/diseno-06/`.

Esta entrega se centra en dos partes:
- **Canales**, que es lo que pediste.
- **El reproductor corregido (06.1)**.

Además trae lo común a toda la app:
- colores y foco del diseño 06;
- rail lateral;
- marca Órbita y pantalla de arranque.

Se conserva todo lo anterior:
- proveedores, favoritos, historial, recordatorios, agenda, copias y diagnóstico;
- la firma y la comprobación SHA-256 de WARP;
- el paquete `com.mivisor.tv`.

La base de datos no cambia: instalar encima de la 3d conserva todo.

## 1. Canales (diseño 06, sección 3)

| Qué | Cómo queda |
|---|---|
| Estructura | **Proveedor → familia → canal**, en tres columnas: Proveedores, Familias y la rejilla de canales |
| Vistas | **Proveedores**, **Categorías** (familias de todos los canales) y **Favoritos** |
| Accesos | **Buscar canal**, **Enlaces** (si tus listas los traen) y **Gestionar proveedores** |
| Proveedores | Las marcas reconocidas (DAZN, Movistar Plus+, beIN, Eurosport…). Al final, **«Otros / sin identificar»**: un canal nunca se atribuye a una marca por una coincidencia dudosa |
| Familias | Fútbol, Motor, Deportes, Cine y series, Generalistas, Noticias, Documentales, Infantil, Música y Otros. Solo salen las que tienen canales |
| Tarjetas compactas | 3 por fila y 70 dp de alto, así que caben muchos más canales por pantalla. Cada una lleva su logo en un recuadro fijo sin deformar, el nombre en 2 líneas como máximo, un punto de estado con texto («Lista · 3 fuentes»…) y una estrella si es favorito |
| Sin logo | Pictograma de señal y el nombre completo. Nada de iniciales inventadas |
| Duplicados | Las versiones HD, FHD y 4K de un canal son **fuentes** del mismo canal, nunca tarjetas repetidas |
| Orden | Natural: «DAZN 2» va antes que «DAZN 10» |
| **OK sobre un canal** | **Abre directamente el reproductor**, sin pasar por la ficha |
| Elegir proveedor o familia | Con OK. Pasar por encima con las flechas no cambia nada, para que no se mueva el filtro sin querer. El elegido se ve en champán; el enfocado, con anillo |
| Mantener OK | Añade el canal a Mi lista o lo quita |
| Al volver del reproductor | Mismo proveedor, misma familia y **foco en el mismo canal**, aunque estuviera muy abajo |

OK también reproduce directamente desde **Buscar** y desde las filas de canales de **Inicio**.

**Selección de fuente al pulsar OK.** Es la lógica que ya existía; no se ha tocado.
1. Primero la fuente más fiable según lo aprendido.
2. Si ninguna se ha probado todavía, la primera disponible, y se aprende del resultado.
3. Si falla, la recuperación y el paso a la siguiente fuente son automáticos.

En ningún caso se espera a que elijas una fuente a mano.

## 2. Reproductor (diseño 06.1)

| Situación | Cómo queda |
|---|---|
| Carga | Logo y nombre pequeños arriba a la izquierda y la Órbita girando en el centro. Debajo, un estado breve («Conectando con la fuente…», «Probando otra fuente…») y un detalle: fuente x de y, proveedor, emisores y «suele tardar ~N s» |
| Al empezar la imagen | **Los controles aparecen solos**, con el foco en **Pausar** |
| Barra de controles | Izquierda: **Info · Fuentes · Mi lista**. Centro: **Pausar/Reanudar**, botón redondo champán. Derecha: **Audio · Subtítulos · Vídeo · Ocultar** |
| Arriba | Logo, nombre y evento que se emite (o «Televisión en directo»), **EN DIRECTO**, «x / y fuentes», el retraso respecto al directo y la hora |
| Sin barra de progreso | En directo no la hay; no se dibuja una falsa |
| Autoocultado | A los **7 s** sin tocar el mando. El diseño recomienda probar entre 5 y 7 s. Nunca se ocultan con un panel abierto ni en pausa |
| Panel lateral | Pestañas **Info · Fuentes · Audio · Subtítulos · Vídeo** y «Cerrar». Lo seleccionado lleva borde verde agua, distinto del foco champán. La barra de debajo se atenúa y el foco no puede ir «al fondo» |
| Fuentes | Fuente, proveedor, calidad y estado (Funciona, A prueba, En pausa…). Al elegir una, se prueba sin salir del canal. Mantener OK la abre en Ace Stream |
| Audio y subtítulos | **Solo las pistas reales** del vídeo, con códec y canales. Si no hay subtítulos, lo dice; no se inventan idiomas |
| Vídeo | Ajustar, Rellenar o Estirar |
| Info | Canal, grupo, evento en directo, fuente, calidad, emisores, retraso, tiempo de arranque y audio |

### Mando en el reproductor

| Tecla | Sin controles | Con controles | Panel abierto |
|---|---|---|---|
| OK | Muestra los controles | Activa el botón enfocado | Elige la pestaña, la pista o la fuente |
| Flechas | **Nada: no cambian de canal** | Recorren los botones | Recorren el panel |
| CH+/CH− y «canal anterior» | Nada | Nada | Nada |
| Atrás | **Vuelve a la pantalla de origen** | Oculta los controles | Cierra el panel y devuelve el foco al botón que lo abrió |

Para cambiar de canal: Atrás y otro canal en la lista. Ya no hay atajos ocultos ni el panel «Canales» dentro del reproductor.

**Avisos de error**:
- Reintentar.
- Probar con Ace Stream, si está instalada.
- Cambiar fuente.
- **Volver a Canales**.

Nunca hay bucles infinitos.

## 3. Lo común a toda la app

- **Colores del diseño 06.**
  - Fondo grafito `#080F19` y tarjetas `#132232`/`#1A2A39`.
  - Texto marfil `#F7F0E6`.
  - Acción y foco en champán `#E9C99E`/`#FFE1B8`.
  - «Bien» en verde agua `#9FD6C8`.
  - EN DIRECTO en granate apagado con letra clara, en vez de rojo chillón.
- **Foco.** Anillo champán y apenas un 2 % de escala, para que el elemento no invada a sus vecinos. Los botones principales son champán.
- **Rail lateral compacto.** Solo iconos, en el orden del diseño: Inicio, Canales, Agenda, Buscar, Mi lista y Ajustes. Al recibir el foco se despliega **encima** del contenido, sin moverlo, y muestra los nombres. Arriba hay una barra fina con la marca, el estado del motor y de WARP, y la hora. Desaparece la opción «Menú arriba / lateral» de Ajustes y del asistente.
- **Marca Órbita.** Es el símbolo principal propuesto por el diseñador.
  - Icono de lanzador en todas las densidades y adaptativo, sobre fondo grafito.
  - Banner de Android TV hecho a partir del símbolo. No usa la foto del estadio, que no es nuestra.
- **Pantalla de arranque.** La Órbita girando, «MiVisor TV» y «Preparando tus canales…».
  - Solo dura lo que tardan en leerse los proveedores guardados: no tiene duración mínima ni botón «Saltar».
  - Como mucho dura 6 s.
  - Si tarda, avisa con «Preparación más lenta de lo habitual».
- **Ajustes › Verificador.** «Comprobar ahora» funciona aunque no tengas favoritos: en ese caso prueba todos los canales, como mucho 60 fuentes. La comprobación automática sigue desactivada, como se acordó.

## 4. WARP: por qué sale «firma distinta» y cómo arreglarlo de verdad

WARP solo obedece a apps firmadas con **su misma clave**. La comprobación compara la huella SHA-256 del certificado; no se ha quitado ni rebajado.

| Firma | Huella | ¿Controla WARP? |
|---|---|---|
| La v18 que te funcionaba | La clave de MiVisor/WARP, que empieza por `B2:9F:F0:71…` | Sí |
| MiVisor TV ahora (sin secretos en GitHub) | `app/debug.keystore`, la clave de desarrollo de la v18, que empieza por `84:43:95:70…` | **No** |

La solución no es tocar el código: es firmar la release con la **misma clave que la v18**.

1. Abre el repositorio de la **v18** en GitHub, en *Settings → Secrets and variables → Actions*. Allí están estos cuatro secretos:
   - `SIGNING_KEYSTORE_BASE64`, que es el keystore original en base64;
   - `SIGNING_STORE_PASSWORD`;
   - `SIGNING_KEY_ALIAS`;
   - `SIGNING_KEY_PASSWORD`.
2. GitHub no deja leer el valor de un secreto ya guardado. Necesitas el **archivo keystore original** (el `.jks` o `.keystore` con el que firmabas la v18) y sus contraseñas. Si tienes el archivo, conviértelo en tu ordenador con `base64 -w0 mivisor.jks > keystore.txt`.
3. En el repositorio de **MiVisor TV**, en *Settings → Secrets and variables → Actions → New repository secret*, crea los **cuatro secretos con los mismos nombres**:
   - `SIGNING_KEYSTORE_BASE64`: el contenido de `keystore.txt`;
   - `SIGNING_STORE_PASSWORD`;
   - `SIGNING_KEY_ALIAS`;
   - `SIGNING_KEY_PASSWORD`.
4. Lanza el workflow **MiVisor TV (APK)**.
   - Con los secretos puestos, firma con ese keystore y comprueba que la huella es la de WARP.
   - Si no coincide, **la compilación falla**: no se publica una APK que no pueda controlar WARP.
   - La APK sale sin el sufijo `-sin-control-WARP`.
5. En la tele, **desinstala una vez** MiVisor TV, porque cambia la firma, e instala la nueva.
6. Comprueba que Ajustes › WARP dice «Correcto: misma huella SHA-256 y permiso concedido» y que la barra superior dice «WARP conectada» al conectar.

La clave privada nunca va en el código, en el ZIP ni en la APK. Solo vive en los secretos de GitHub.

## 5. Lo que queda pendiente del paquete del diseñador

Esta entrega se ha centrado en **Canales** y el **reproductor**. Del resto del diseño 06 ya se ven los colores, el foco, el rail y la marca, pero **sus pantallas aún no tienen la maquetación nueva**:

| Pieza del diseño 06 | Estado |
|---|---|
| Inicio con portada a sangre (foto de estadio), «Continuar viendo», «Próximas citas» y «Competiciones seguidas» | Pendiente. Mantiene la estructura de la 3d con los colores nuevos |
| Agenda con fecha → deporte → competiciones y orden cronológico global, más la ficha con panel contextual «Dónde verlo» | Pendiente. Mantiene la de la 3d |
| Buscar con resultados tipados y Mi lista con «Vistos» | Pendiente. Ya funcionan, con los colores nuevos |
| Ajustes en 7 grupos (General, Reproductor, Interfaz, Servicios, Canales, Datos, Pruebas) | Pendiente. Mantiene los apartados de la 3d |
| Variante del icono «Arco» | No se usa: el diseño recomienda una sola marca, la Órbita |
| Fotos de estadio y logos de DAZN/Movistar del prototipo | No se incluyen: el propio diseñador avisa de que son ilustrativos o de terceros, sin licencia |

Si te parece bien cómo quedan Canales y el reproductor, el siguiente paso sería aplicar el diseño 06 completo a Inicio y Agenda.

## 6. Comprobaciones en la tele

| # | Prueba | Debe pasar |
|---|---|---|
| 1 | Abrir la app | Pantalla de Órbita breve y luego Inicio; el icono y el banner son los nuevos |
| 2 | Rail | Al pulsar ← desde el contenido se despliega encima sin mover nada; ↑↓ recorren las secciones; → vuelve al contenido |
| 3 | Canales › DAZN › Fútbol | Solo canales de fútbol de DAZN, en orden natural; caben 3 por fila |
| 4 | **OK en un canal** | **Reproduce directamente** |
| 5 | Al empezar la imagen | Aparecen los controles con foco en Pausar; se ocultan solos a los 7 s |
| 6 | Controles ocultos y flechas ↑↓←→ | No pasa nada: el canal no cambia |
| 7 | Fuentes / Audio / Subtítulos / Vídeo | Abren el panel; Atrás lo cierra y vuelve al botón |
| 8 | Atrás con controles; Atrás otra vez | Oculta los controles; vuelve a Canales **con el foco en el mismo canal** |
| 9 | Mantener OK en un canal | Añadido a Mi lista; aparece la estrella y sale en «Favoritos» |
| 10 | Canal sin logo | Pictograma de señal y nombre completo |
| 11 | Ajustes › Verificador › Comprobar ahora | Arranca el motor y cuenta las fuentes con imagen |
| 12 | Con los secretos de la v18 puestos | Ajustes › WARP: misma huella; conectar y desconectar funcionan |

## Compilación

- **Pruebas:** pasan las **176 pruebas** de los módulos JVM. Son nuevas las de familias de canales y orden natural.
- **Código Android:** lleva las revisiones de imports y referencias de siempre, pero **no se ha compilado aquí ni se ha probado en una tele**. Se comprobará en GitHub Actions; si falla, pásame el log.
- **Fuera de este proyecto no se ha tocado nada:** ni la Raspberry, ni MiVisor Web, ni MiVisor Agenda, ni `mivisor-datos`, ni la v18, ni Outpace en producción.
