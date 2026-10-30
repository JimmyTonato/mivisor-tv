# MiVisor TV · Entrega 3c (agenda, logos y recordatorios)

La 3c va encima de la 3b y la sustituye entera. Conserva todo lo anterior sin cambios de comportamiento: reproducción, Outpace, WARP, Ace Stream, favoritos, historial, la firma y la comprobación SHA-256 de WARP. No hay que configurar nada nuevo. Lo de las entregas anteriores está en `docs/entregas/`.

## Lo que trae

### 1. Logos reales de los canales

- **De dónde salen**, como en la v18:
  1. El catálogo `channel-logos.json` que se publica junto a `agenda.json` en `mivisor-datos`. Se descarga como mucho una vez al día, con ETag y copia en la tele.
  2. Los logos que trae la propia agenda (futbolenlatv).
  3. Los de TheSportsDB, que se guardan al abrir un evento.
- **Dónde se ven:** en las tarjetas de canal, los grupos de Canales, la ficha del canal, «Dónde verlo» y la parte de arriba del reproductor.
- **Cómo se pintan:** sobre una placa clara, porque casi todos están pensados para fondo blanco. Si un canal no tiene logo o no carga (sin red, imagen borrada, SVG), se pinta el **rótulo de texto de la 3b**. Ningún canal se queda en blanco.
- **Cargador:** el de la v18, sin librerías nuevas. Decodifica al tamaño de pantalla, con caché en memoria (máx. 6 MB) y en disco (máx. 300 imágenes).
- **Equipos y competiciones:** en la agenda se ven los escudos (o retratos en tenis y UFC) y los logos de competición que trae `agenda.json`. Sin imagen, las siglas sobre su color.

### 2. «Dónde verlo» completo

Cada evento tiene su ficha. Se abre desde la Agenda, desde una competición, desde las tarjetas «En directo» de Inicio, desde el buscador y desde los recordatorios.

- **Cabecera:** competición, escudos o retratos, título, y EN DIRECTO o día y hora.
- **Dónde verlo:**
  - Aparecen **todos** los canales anunciados, con dial y nota (país, «PPV»…).
  - Debajo de cada uno, los canales de **tus** proveedores que lo emiten, con su logo y su estado («Funciona», «Sin probar»…). Se pueden ver con OK.
  - Primero los que puedes ver, **de más a menos fiable** según lo aprendido. Al final, los que «No están en tus proveedores», para que sepas dónde se emite.
  - Si la agenda no trae canales de un evento, se consultan en TheSportsDB **solo al abrir la ficha**, con caché: lo mismo que hacía la v18.
- **Botones:** «Ver ahora» (el mejor canal tuyo), «Recordarme» y el acceso a su competición.
- **Inicio:** en la portada, «Otras opciones» pasa a ser «Dónde verlo».

### 3. Competiciones

- **Agenda:**
  - Arriba, la fila «Competiciones» con logo y lo que tiene cada una («3 hoy · 1 en directo», «Próxima carrera: 9 nov»…). LaLiga, Champions, Premier, F1 y MotoGP aparecen siempre.
  - Debajo, el filtro por deporte y los eventos **por días** («Hoy», «Mañana», «Sábado 2 de noviembre»…).
- **Página de cada competición:** logo, resumen y sus eventos por día; en F1 y MotoGP, las sesiones del fin de semana. Cada evento abre su ficha.
- **Clasificación de eventos:** cada evento se asigna a su competición con las reglas de la web y la v18 (LaLiga, Champions, Premier, Serie A, Bundesliga, Ligue 1, Europa y Conference League, Copa, Supercopa, F1, MotoGP, ATP, WTA, UFC, baloncesto, motor…).

### 4. Recordatorios

- **Poner y quitar:** «Recordarme» en la ficha de cualquier evento que no haya terminado. Los eventos con recordatorio llevan un reloj en la agenda.
- **Aviso:** **5 minutos antes**, MiVisor TV muestra un aviso encima de cualquier pantalla (también del reproductor), con «Ver ahora», «Dónde verlo» y «Cerrar».
- **«Deja la fuente lista»:** al saltar el aviso se arranca Outpace. «Ver ahora» abre el mejor canal tuyo por fiabilidad.
- **Sin segundo plano**, como acordamos: el aviso sale **con la app abierta**. Si estaba cerrada a esa hora, avisa al abrirla mientras el evento siga en curso. Cada recordatorio avisa una sola vez y se borra solo cuando el evento termina.
- **Mi lista:** pestaña «Recordatorios» (Favoritos · Recordatorios · Historial). Desde ahí se abre la ficha, y manteniendo OK se quita el recordatorio.
- **Datos:** se guardan en la base de datos de la tele. Su versión pasa de 1 a 2 con una migración que **no borra nada**, así que instalar encima de la 3b conserva proveedores, favoritos, historial y fiabilidad.

## Lista de comprobación en la tele (T)

| # | Comprobación |
|---|---|
| 1 | Canales e Inicio muestran logos reales; un canal sin logo muestra su rótulo de texto, nunca un hueco |
| 2 | Agenda › un evento › «Dónde verlo» lista los canales anunciados; los tuyos se pueden ver con OK y los demás dicen «No está en tus proveedores» |
| 3 | Un partido sin canales en la agenda los busca al abrirlo («Buscando en qué canales se emite…») |
| 4 | Agenda › Competiciones › LaLiga (o F1) abre su página con los eventos por día |
| 5 | Pon un recordatorio en un evento que empiece en menos de 10 min y deja MiVisor TV abierta: el aviso sale 5 min antes, y «Ver ahora» abre el canal |
| 6 | Pon otro recordatorio y cierra la app; ábrela cuando el evento ya haya empezado: sale el aviso «Empezó hace…» |
| 7 | Mi lista › Recordatorios los muestra; mantener OK quita uno |
| 8 | Instalada encima de la 3b, se conservan proveedores, favoritos, historial y lo aprendido de las fuentes |
| 9 | Con el mando se llega a todo lo nuevo (fichas, competiciones, avisos) y Atrás siempre vuelve |

## Qué queda incompleto o fuera

- **Avisos con la app cerrada:** no los hay, a propósito (sin segundo plano, como se acordó). Si en algún momento los queréis, harían falta las notificaciones del sistema y un permiso; sería un cambio de criterio.
- **Logos de canal: dependen del catálogo de `mivisor-datos`.** Si un canal de vuestros proveedores no está en él, en la agenda ni en TheSportsDB, se queda con el rótulo de texto. Para añadirlo basta con incluirlo en `channel-logos.json` (lo genera MiVisor Agenda), sin tocar la app.
- **Competiciones: no hay clasificaciones ni resultados.** La página muestra los eventos de la agenda; `agenda.json` no trae tablas ni marcadores.
- **Vista previa del canal** al pasar por encima: más adelante.
- **Ace Stream dentro de MiVisor TV** (su motor con nuestro reproductor): más adelante, como en la 3b.
- **3d** (copias, diagnóstico completo, verificador): no se ha empezado, como pediste.

## Compilación

Pasan las **170 pruebas del núcleo**; son nuevas las de «Dónde verlo» y los recordatorios. El código Android lleva las mismas revisiones de imports y referencias que en la 3a y la 3b, pero no se ha compilado en este entorno. Se comprobará en GitHub Actions; si falla, pasadme el log.

No se ha modificado nada de la Raspberry, MiVisor Web, MiVisor Agenda, `mivisor-datos`, la v18 ni Outpace en producción.
