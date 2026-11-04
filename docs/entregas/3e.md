# MiVisor TV · Entrega 3e.2: canales numerados y el minuto de espera

Corrige la 3e.1, que va justo debajo.

## 1. Cada número es un canal distinto

**El fallo.** «M+ Liga de Campeones 2» se juntaba con «M+ Liga de Campeones» como si fuera otra fuente del mismo canal. Por eso no aparecía el número. Lo mismo pasaba con DAZN 1/2/3 y M+ LaLiga 2/3.

**Ahora:**
- Cada número es su propio canal, con su nombre completo.
- Las fuentes de un canal son solo las que tienen el mismo nombre y el mismo número. Por ejemplo, «Movistar LaLiga 2 FHD» y «M+ LaLiga 2» son dos fuentes del mismo canal, M+ LaLiga 2.
- En Canales › Proveedores, los canales se juntan en grupos:

| Proveedor | Grupo | Canales |
|---|---|---|
| Movistar Plus+ | M+ Liga de Campeones | M+ Liga de Campeones, M+ Liga de Campeones 2… |
| Movistar Plus+ | M+ LaLiga | M+ LaLiga, 2, 3… |
| DAZN | DAZN | DAZN 1, 2, 3… |

La regla es **general**: no hay nada específico para Movistar ni DAZN. Vale para cualquier canal con un número al final, como Eurosport 1/2, LaLiga TV Hypermotion 2/3 o Movistar Deportes 2, y también si el número va pegado (`Eurosport2` = `Eurosport 2`). Un canal numerado sin hermanos, como «Calle 13», sale en «Canales sueltos» con su nombre completo.

«1» y «sin número» se tratan como canales distintos. Si en tus listas significan lo mismo, dímelo y los uno.

Un canal que antes era favorito por el nombre sin número (por ejemplo «DAZN») y que en tus listas solo existe con número («DAZN 1», «DAZN 2») deja de aparecer en Mi lista. Hay que volver a marcarlo una vez.

## 2. Iconos: solo los de picons_dobleM que coinciden

- Cada canal busca el icono con **su nombre exacto**. «M+ Liga de Campeones 2» busca `M+ Liga de Campeones 2.png`.
- Si un canal numerado no tiene icono propio, **ya no se le pone el del canal sin número**, para que no parezca el mismo canal. Se usa el logo de la marca (Movistar, DAZN…) o el pictograma de señal.

## 3. Un minuto antes de pasar a otra fuente

Antes había varios caminos que cambiaban de fuente antes del minuto:

| Caso | Antes | Ahora |
|---|---|---|
| Sin imagen al empezar un canal | Otra fuente a los 30 s | Se espera 1 min |
| Sin emisores al empezar | Otra fuente a los 15 s | Se espera 1 min |
| Error del reproductor al empezar (p. ej. el 503 del motor a los 30 s) | Otra fuente al momento | Se reintenta la misma cada 3 s hasta el minuto |
| Error del reproductor mientras ves el canal | Otra fuente al momento | Cuenta como corte: se recarga la misma cada 15 s |
| El salto al directo no recuperaba la imagen | Otra fuente a los 20 s | Cuenta como corte: se recarga la misma cada 15 s |
| El directo se detenía | Otra fuente al momento | Cuenta como corte: se recarga la misma cada 15 s |

En todos los casos se pasa sola a otra fuente **al minuto** como pronto.
- El ajuste «Pasar sola a otra fuente si no hay imagen» (1 min / 2 min / Solo con el botón) vale ahora también al empezar un canal.
- En pantalla se ve cuánto lleva: «Buscando imagen · 34 s (otra fuente a los 60 s)».
- A los 8 s sale el botón «Probar otra fuente», para no tener que esperar.
- Con «Solo con el botón», la app no cambia nunca de fuente por su cuenta.

Una excepción: si el motor Outpace deja de responder, se sigue avisando al momento con «Reintentar», porque esperar no lo arregla.

## Compilación

- **Pruebas:** pasan las **182 pruebas** de los módulos JVM. Es nueva la de canales numerados; las de umbrales e iconos se han actualizado.
- **Código Android:** no se ha compilado aquí. Se comprueba en GitHub Actions.

---

# Entrega 3e.1: WARP, fuentes, iconos y grupos de canales

La 3e.1 corrige y amplía la 3e. Esta va al final del documento.

Se conserva todo lo anterior y el paquete `com.mivisor.tv`. La base de datos no cambia.

## A. WARP: copiar la firma de la v18 sin tener el archivo de la clave

No puedo «copiar la clave» yo:
- no está en el código de la v18;
- vive en los **secretos de GitHub** del repositorio de la v18;
- y GitHub no deja leer un secreto, ni a ti ni a mí.

Lo que sí puede hacerse es que un workflow del **repositorio de la v18** los lea y los escriba en el de MiVisor TV. Eso hace `tools/warp/copiar-firma-v18.yml`:

1. **Copiar el workflow.** Pon ese archivo en `.github/workflows/` del repositorio de la **v18**. Es el único cambio en la v18 y lo borras al terminar.
2. **Crear un token.** En GitHub › Settings › Developer settings › *Fine-grained tokens*, crea uno con acceso **solo** al repositorio de MiVisor TV y el permiso **Secrets: Read and write**. Guárdalo en el repositorio de la v18 como secreto `MIVISOR_TV_TOKEN`.
3. **Lanzarlo.** En el repositorio de la v18, ve a Actions › **Copiar firma a MiVisor TV** › *Run workflow* y escribe `usuario/repositorio-de-MiVisor-TV`.
4. **Comprobar la huella.** En el registro debe salir **«Huella SHA-256 … B2:9F:F0:71…»** y «Es la clave que acepta WARP».
5. **Compilar.** En MiVisor TV, lanza **MiVisor TV (APK)**. Ahora firma con la clave de la v18 y la APK sale **sin** `-sin-control-WARP`.
6. **Instalar.** **Desinstala MiVisor TV una vez** en la tele (cambia la firma) e instala la nueva. Ajustes › WARP debe decir «misma huella SHA-256».
7. **Limpiar.** Borra el workflow de la v18 y el token.

La comprobación SHA-256 sigue igual: si la clave no fuera la de WARP, la compilación fallaría en vez de publicar una APK que no funciona. No se ha generado ninguna clave nueva y nada secreto va en el ZIP.

## B. Fuentes: menos cambios precipitados, más control

**Antes:** con la imagen cortada, a los 26 s se pasaba sola a otra fuente aunque la caída fuera momentánea.

**Ahora, si la imagen se corta mientras ves un canal:**
- 8 s: salto al directo.
- 16 s: se recarga la **misma** fuente, y luego **cada 15 s** mientras se espera.
- **Solo pasa sola a otra fuente al minuto.** En Ajustes › Fuentes y recuperación puedes cambiarlo a **2 minutos** o a **«Solo con el botón»**.
- En pantalla, la Órbita y «Recargando la fuente · 32 s (otra fuente a los 60 s)», con dos botones:
  - **Seguir esperando**: tiene el foco, así que un OK sin querer no cambia nada.
  - **Probar otra fuente.**
- Pasar a otra fuente a mano **no penaliza** a la fuente que dejas.

**Al empezar un canal:**
- Se mantiene la regla: a los 30 s sin imagen (o 15 s sin emisores) se prueba la siguiente.
- **A los 8 s** aparecen los mismos botones para poder pasar antes.

**Volver al directo:**
- **Ya volvía solo:** tras un corte, si te quedas más de 3 s por detrás, salta al directo.
- **Nuevo en Ajustes:** «Volver solo al directo tras un corte» (activado). Si lo desactivas, te quedas donde estabas.
- **Nuevo botón** «Ir al directo» en la barra del reproductor, cuando vas más de ~8 s por detrás. Arriba ya se ve cuánto («−32 s»).

## C. Iconos de los canales: picons_dobleM

- **Fuente principal:** [picons_dobleM](https://github.com/davidmuma/picons_dobleM), carpeta `icon/`. Por ejemplo, «M+ LaLiga» → `icon/M+ LaLiga.png`.
- **Cómo se obtienen:** la app pide la lista de iconos a GitHub **una vez por semana** y la guarda. Las imágenes se descargan solo al verse.
- **Cómo se emparejan con tus canales:** con la misma normalización que agrupa los canales. «Movistar LaLiga 2 FHD» encuentra `M+ LaLiga 2.png`, porque se quita la calidad y M+ y Movistar se tratan como lo mismo.
- **Si un canal no está en picons,** se usan los logos de antes (catálogo de mivisor-datos, agenda, TheSportsDB) y, si no hay ninguno, el pictograma de señal.
- **Cómo se pintan:** tal cual, sin la placa clara, porque ya traen su propio fondo.

> No he podido descargar el repositorio desde aquí: el acceso a GitHub está bloqueado en este entorno. Lo que no se ha visto es el aspecto exacto de los iconos sobre el fondo oscuro. Si alguno queda mal, dímelo y uso otra carpeta (`color`, `blanco`…).

## D. Canales: proveedor → grupo → canal

En **Proveedores**, la columna central se llama ahora **«Grupos»** y junta los canales numerados:

- **Movistar Plus+** › **M+ LaLiga** (1, 2, 3…) · **M+ Liga de Campeones** (1, 2…) · …
- **DAZN** › **DAZN** (DAZN, 1, 2, 3, 4) · **DAZN LaLiga** (1, 2…) · …

Cómo funciona:
- **Dónde caen los demás:** los canales sin número ni hermanos van a **«Canales sueltos»**. «Todos» enseña el proveedor entero.
- **Orden:** dentro de cada grupo, natural (2 antes que 10).
- **Temática:** la clasificación por temas (Fútbol, Motor, Cine…) sigue en la pestaña **Categorías**.
- **Al volver del reproductor:** se recuerdan el proveedor, el grupo y el canal.

## Compilación

- **Pruebas:** pasan las **180 pruebas** de los módulos JVM. Son nuevas las de picons, grupos y orden natural.
- **Código Android:** no se ha compilado aquí. Se comprueba en GitHub Actions.

---

# Entrega 3e: Canales y reproductor del diseño 06.1

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
