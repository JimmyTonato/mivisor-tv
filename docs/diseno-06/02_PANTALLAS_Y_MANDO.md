# MiVisor TV · Comportamiento de pantallas, mando y estados
**Contrato UX 06** · Prioridad: cualquier estado visible tiene origen de datos y una acción de salida predecible.

## 0. Modelo de navegación y persistencia

### Jerarquía y orden

`Inicio | Canales | Agenda | Buscar | Mi lista | Ajustes`

- Rail compacto con **seis** accesos primarios. Cuando recibe foco, se expande **sobre el contenido**, sin reflow del Inicio/Agenda/Canales; al recuperar foco el contenido vuelve al tamaño compacto.
- Estado **activo**: superficie champán tenue y símbolo claro, persiste hasta cambiar de sección. Estado **enfocado**: contorno champán 3 px, halo oscuro y superficie reforzada, incluso sobre la sección activa. Estado **inactivo**: icono gris azulado, sin halo.
- Foco inicial de la app tras preparación: CTA del destacado del Inicio. Foco inicial de Canales: proveedor «Todos»; de Agenda: fecha seleccionada; de Buscar: campo de texto; de Mi lista: pestaña seleccionada; de Ajustes: categoría elegida.
- Flecha izquierda: primero busca un control vecino válido dentro de la región actual; si no existe y el contenido está en su borde izquierdo, entra en el rail. Una vez enfocado el rail, arriba/abajo recorren secciones; derecha devuelve el foco del contenido conservado en memoria para esa sección, no fuerza una posición genérica. **No mostrar/ocultar el rail al pulsar Atrás**.
- OK sobre sección: abre la sección y restaura su último control y desplazamiento si ya se había visitado. OK sobre canal: inicia reproducción inmediatamente. OK sobre fila de Agenda: ficha. OK sobre «Dónde verlo»: panel contextual. OK sobre un canal del panel: reproducción.
- Atrás: panel → ficha → sección anterior; player (controles visibles) → ocultar controles → pantalla de origen; subapartado de Ajustes → pestaña previa. En Inicio, Atrás debe delegar en el sistema Android para salir, sin bucle que vuelva a expandir el rail.
- Cada sección guarda `selectedId`, `focusKey`, `scrollOffsetPx`, filtros y posición de navegación. El `focusKey` es estable por ID lógico, no índice de lista.
- Al volver desde player iniciado en Canales, recuperar **el mismo canal lógico, proveedor y familia**, aunque la posición de scroll sea profunda. Al volver desde player iniciado en ficha, restaurar la **ficha**, dejando «Dónde verlo» cerrado (el usuario puede abrirlo otra vez).

### Tabla global de mando

| Contexto | OK | Arriba / Abajo | Izquierda / Derecha | Atrás |
|---|---|---|---|---|
| Rail enfocado | Abre sección | Mueve sección | Derecha vuelve al último foco del contenido | Vuelve en pila, no abre/cierra rail |
| Inicio, controles enfocados | Abre acción | Cambia módulo / desplaza con foco | Entre tarjetas o hacia rail desde borde | Navegación previa o salida del sistema |
| Agenda fecha | Aplica día | Cambia región cuando corresponda | Recorre días, sin seleccionar hasta OK | Volver a sección previa |
| Agenda filtros / competiciones | Aplica filtro | Recorre filas | Pasa a otra región según grafo | Retrocede |
| Agenda evento | Abre ficha | Evento vecino y scroll automático | Columnas o filtro accesible, sin perder estado | Regresa a la sección anterior |
| Ficha | Activa acción | Recorre acciones | Recorre campos si hay vecinos | Regresa a Agenda |
| «Dónde verlo» abierto | Elige canal | Recorre fuentes asociadas | Recorre controles del panel, nunca al fondo | Cierra panel, restaura foco en CTA |
| Canales proveedor | Selecciona proveedor | Recorre proveedores | Derecha a familia, izquierda a rail | Retrocede |
| Canales familia | Filtra por familia | Recorre familias | Derecha a canales, izquierda a proveedor | Retrocede |
| Canales canal | Inicia reproducción | Recorre celdas y realiza scroll | Recorre celdas o vuelve a familia | Retrocede |
| Buscar teclado | Introduce carácter | Vecino vertical | Vecino horizontal | Vuelve atrás, no borra por sorpresa |
| Buscar resultado | Abre evento/reproduce canal | Recorre resultados | Entre campo, teclado y resultados | Regresa a sección previa |
| Ajustes | Abre subapartado | Recorre categorías/filas | Cruza de menú a opciones | Regresa al nivel anterior |

**Regla importante:** la demo utiliza proximidad geométrica en navegador como una *aproximación* al mando. El proyecto Android debe asignar un grafo explícito de vecinos por pantalla y `FocusRequester`, y probarlo con tamaños de texto grandes. La navegación por teclado incluida no es prueba automática de equivalencia con todas las APIs del mando real.

## 1. Inicio · jerarquía exacta

**Primer viewport:** hero cinematográfico 480 px, información verificable, 1 CTA principal y como máximo 1 secundario, seguido de «En emisión y a continuación» (4 tarjetas). El CTA principal en evento `live` abre su ficha para confirmar canal; si solo hay próximos muestra «Ver encuentro» o «Recordarme» según disponibilidad; jamás afirma una emisión no verificada.

**Debajo del fold**, en este orden:
1. **Continuar viendo**: panel de recuperación con dos canales recientes en filas de 55 px; no requiere EPG.
2. **Próximas citas**: lista vertical compacta que muestra recordatorio activado/no activado, acción reversible.
3. **Favoritos**: hasta cuatro accesos en la primera línea, un OK inicia señal. Ver la lista completa en Mi lista.
4. **Competiciones seguidas**: tarjetas de navegación a Agenda con el filtro aplicado.
5. **Agenda próxima**: líneas cronológicas con hora, competición, evento y detalle.

**Fallback del hero:** elegir contenido por estas reglas, no por un nombre fijo: evento con estado confirmado en directo y datos suficientes → próximo relevante → continuación del último canal → portada sin agenda. `poster` ausente: estadio genérico con degradado y escudos autorizados. En offline con caché: utilizar datos cacheados, marcar caducidad, no exhibir «EN DIRECTO» como confirmado actualmente si el estado ya no es verificable. La demo contiene datos estáticos que ilustran el contrato, no un ranking automático completo.

**Estados:** sin agenda, favoritos vacíos, desconexión, datos desactualizados, recuperación de caché. No bloquear Inicio por falta de EPG, marcas de terceros o APIs de deporte.

## 2. Agenda · modelo por deporte

**Jerarquía efectiva:** fecha horizontal (máximo 7 visibles) → filtro deporte (única taxonomía) → competiciones dentro del deporte → eventos cronológicos. Los filtros de deporte son `Todos`, `Fútbol`, `Motor`, `Tenis`, `Otros`; en producción derivarlos de los valores válidos del backend, con normalización y taxonomía controlada. Evitar duplicados «Fútbol» y «TV Fútbol» salvo diferencia funcional real.

- Vista predeterminada: `startAt` ascendente en zona horaria del dispositivo, **sin agrupar todos los directos arriba**. Cuando se agrupe por franja horaria, mantener orden global.
- Fútbol: hora, escudos `contain`, equipos, competición, estado, resultado **solo si viene validado**; si no se conoce el marcador no reservar un hueco falso.
- F1: gran premio, circuito si existe, sesión `FP1/FP2/FP3/Clasificación/Sprint/Carrera`, categoría, horario, estado. Sin campos falsos «equipo local/visitante».
- MotoGP: gran premio, categoría `MotoGP/Moto2/Moto3`, sesión, circuito, horario y estado. Cada sesión es evento distinto con `eventId` estable.
- Tenis y otros: lista flexible `participants` 0..n, con título de respaldo y competencia. Un participante no obliga a tarjeta «VS».
- `status`: `unknown | upcoming | live | finished | postponed | cancelled`. No inferir finalización de la hora; usar fuente de datos y caducidad explícita.

**Ficha:** cabecera 285 px, título, competición, fecha, hora, estado, escudos si hay, información específica del deporte y acciones «Dónde verlo», «Recordar» y «Seguir competición». El botón «Dónde verlo» no abre nada si no hay datos? **Sí abre** panel contextual con estado vacío: así el usuario recibe una explicación clara; no se simula una señal.

**Dónde verlo:** panel anclado a 53 px del borde derecho, 47 px superior/inferior; lista de **canales lógicos asociados**, nunca fuentes duplicadas. OK sobre canal → el motor seleccionará fuente según estrategia de preferencia; volver → ficha con foco en acción. Sin canales → «Sin canales asociados» y posibilidad de recordatorio, sin botón de reproducción falsa.

**Estados QA:** fecha sin eventos; deporte sin eventos; competición retirada; evento sin equipos; sin escudos; `unknown`; `postponed` / `cancelled` / `finished`; datos antiguos; sin conexión. No borrar ninguno de los seis estados del enum.

## 3. Canales · jerarquía, fichas compactas y duplicación

**Primario:** proveedor (identificado mediante ID fiable) → familia (Fútbol, Deportes, Motor, General, Cine, etc., solo si contiene entradas) → canal lógico. **Alternativo:** categoría transversal por Deportes, Fútbol, Motor, Cine y Generalistas. **Favoritos:** filtro de canal lógico, independiente del proveedor; la misma señal no aparece duplicada dentro de un listado.

- Selección de proveedor **no reproduce**; selección de familia **no reproduce**. Solo el canal inicia señal inmediatamente con OK.
- Canales sin proveedor confiable van a `Otros / sin identificar` y mantienen el `displayName` original, no se atribuyen a DAZN/Movistar basándose únicamente en un prefijo ambiguo.
- Orden natural: `DAZN 2` antes de `DAZN 10`. Comparación numérica sobre tokens de la misma familia y base normalizada; nunca ordenar por el número más grande alfabéticamente.
- HD / FHD / 4K o diferentes URLs de un mismo ID lógico: fuentes asociadas, nunca tarjetas duplicadas. **No fusionar** si solo coinciden parcialmente nombre, logo o categoría; colisiones se documentan para revisión.
- Estado `no_sources` se representa incluso sin imagen; OK puede abrir pantalla de error/recuperación pero **no puede fingir que reproduce**.
- «Canal favorito» usa estrella de accesibilidad y persistencia por ID lógico; elegir fuente alterna no crea otro favorito.
- Logotipo ausente: pictograma genérico de señal, texto completo y contenedor de 65 px. No iniciales aleatorias.
- Búsqueda: sobre `displayName`, nombres de proveedor y alias aprobados; resultados por canal lógico, no por URL. Aplicar debounce 150–250 ms a las bibliotecas de miles de canales; virtualizar resultados.
- Bibliotecas grandes: cargar categorías por demanda, mantener IDs estables, usar lista virtualizada, scroll automático para que el elemento enfocado no esté fuera de pantalla. Evitar cargar 1.000 logotipos en memoria simultáneamente.

**Los 40 canales de esta demo son inventario de *prueba visual* a partir de familias y ejemplos citados por el usuario, no la lista efectiva del proveedor de MiVisor.** El traspaso incluye un modelo normalizado para sustituirlos por datos reales.

## 4. Reproductor único · tabla exacta

**Una solución principal, no dos modos seleccionables de producto.** Se elimina la variante Broadcast como experiencia paralela. La base es Cinema minimalista con panel anclado; el stream manda.

| Estado | Elementos | Comportamiento |
|---|---|---|
| Preparando fuente | Fondo de vídeo si existe; spinner centrado; nombre y logo esquina superior izquierda; estado técnico discreto | Si falla, transicionar a recuperación o error; si usuario pulsa Atrás, salir |
| Vídeo normal, sin controles | Solo contenido a pantalla completa | OK muestra controles; flechas no hacen nada ni cambian de canal; Atrás sale al origen |
| Controles visibles | Logo+nombre pequeño arriba; EN DIRECTO si fiable; zona inferior con fuentes/audio/subs/info/favorito | Autocierre ~6 s tras inactividad, **no cerrar si panel está abierto** |
| Grabación o timeshift confirmado | Controles + barra de progreso real y pausa | Barra usa `positionMs`/`durationMs` declarados. Nada de progreso ficticio en lineal |
| Directo lineal sin pause/seek | Sin pausa ni barra de progreso | El comando pausa no es operable. No presentar un icono que no funcionará |
| Fuentes | Panel anclado a bordes seguros; fuente activa, disponibilidad, opción enfocada | Al seleccionar: mostrar recuperación y probar la nueva fuente sin salir |
| Audio | Lista de pistas del motor | Solo `tracks.audio` reales; pista seleccionada y `unknown` tratados |
| Subtítulos | Off + `tracks.text` reales | «Sin subtítulos» si vacío; no inventar idiomas |
| Error | Mensaje acotado + Reintentar + Volver a Canales | Sin bucles indefinidos; mantener origen |
| Recuperación | Vídeo detenido / fondo, spinner centrado, estado | Reconexión por algoritmo; nunca abrir paneles espontáneamente |

| Tecla | Player sin controles | Player con controles | Panel lateral |
|---|---|---|---|
| OK | Muestra controles, **no pausa lineal** | Activa el control enfocado | Selecciona pestaña, pista o fuente |
| Flecha arriba/abajo | Sin acción; nunca cambia de canal | Desplaza foco entre controles | Desplaza foco en lista |
| Flecha izq/dcha | Sin acción en lineal sin timeshift | Recorre controles | Cambia elemento de foco; nunca cambia de canal |
| Atrás | Regresa a lista o ficha de origen | Oculta controles | Cierra panel y enfoca botón Fuentes |
| Cambio de canal | Volver a Canales y OK en otro canal | Igual | Igual; no atajos ocultos |

**Conflicto de plataforma:** la recomendación general de Android TV para medios con seek permite izquierda/derecha para avance/retroceso. Aquí prevalece la especificación del producto para **lineales sin DVR**, para evitar cambios inesperados. En contenidos grabados o con timeshift probado, se podrá utilizar seek tras validación, sin afectar la semántica de navegación de los paneles.

**Punto de integración:** UI escucha `MediaSession/Media3` (o adaptador Ace Stream) y su `PlaybackState`, capacidades de `seek`, audio/text tracks, fuente en uso y errores. UI no decide la calidad ni inventa estado de fuentes. Estrategia de elección y failover pertenece al motor.

## 5. Preparación · no es una intro publicitaria

Secuencia: leer datos guardados + tema + catálogo persistido → predecodificar imagen principal / modelo mínimo de Inicio → abrir Inicio. Animación de Órbita solo mientras esas tareas ocurren, sin duración fija ni botón «Saltar». Tras ~900 ms en carga lenta: mensaje «Preparación más lenta de lo habitual». Si un servicio secundario falla: abrir con caché, aviso discreto en Inicio y opción de reintento; no esperar a API deportiva, VPN o verificación de todas las fuentes para permitir navegación. Si falla el modelo crítico y no hay caché, mostrar una pantalla de error de arranque con acción Reintentar y diagnóstico; no crear una pantalla vacía.

La demo puede forzar la espera prolongada en **Ajustes → Pruebas → Intro prolongada**. El arranque inicial termina al recibir el recurso y modelo necesarios, sujeto a un límite de seguridad de la demo. En Android el cierre debe depender de estados reales, no de estos temporizadores ilustrativos.

## 6. Buscar / Mi lista / Ajustes

- **Buscar:** foco inicial en el campo, teclado de televisión 7 columnas y resultados tipados canal/evento. Nombre largo máx. 2 líneas por resultado; Enter reproduce canal o abre ficha del evento. Con consulta vacía: favoritos y próximos eventos como punto de partida.
- **Mi lista:** tres pestañas Favoritos, Recordatorios, Vistos (Historial trasladado aquí). Un OK en favorito/visto reproduce; un OK en recordatorio abre ficha. Vacíos con explicación y CTA de siguiente acción.
- **Ajustes:** siete grupos General, Reproductor, Interfaz, Servicios, Canales, Datos y Pruebas; subapartados restauran foco al volver. «Pruebas» se **elimina de producción** o se protege detrás de modo desarrollador.
- **Proveedores:** código y QR como rutas de vinculación existentes en referencia. QR real debe generarlo servidor autorizado con caducidad; el prototipo muestra intencionadamente un estado pendiente para no presentar credenciales inventadas.
- **Ace Stream:** iniciar/detener/reiniciar y autoinicio, estado y versión en integración nativa, nunca inferir éxito desde el color del botón. **VPN / WARP:** estados conectado, desconectado, autorizado, pendiente, error. APIs deportivas: estatus y fecha de última sincronización por proveedor.
- **Diagnóstico:** verificaciones de fuentes, reintentos, sin probar y errores; debe estar disponible, pero no ocupar la portada de streaming.

## 7. Rutas de aceptación (sin desvíos de ejemplo)

1. `Inicio → Canales → DAZN → Fútbol → DAZN 1 → Reproductor → Atrás → DAZN 1` (mismo foco, scroll y familia).
2. `Inicio → Agenda → Motor → Fórmula 1 → GP de Japón / Clasificación → Dónde verlo → DAZN F1 → Reproductor → Atrás → ficha`.
3. `Inicio → Buscar → teclear DAZ → DAZN 1 → Reproductor → Atrás → Buscar` (consulta retenida).
4. `Inicio → Mi lista → Favorito → Reproductor → Atrás → Mi lista`.
5. `Inicio → Ajustes → Servicios → Ace Stream → Atrás → Servicios` (foco restaurado).
6. `Player abierto → Fuentes → Audio → Subtítulos → Atrás → controles → Atrás → vídeo → Atrás → origen`.
7. `Agenda vacía`, `Inicio sin conexión`, `Fuentes inexistentes`, `Falta de logo`, `Replay con progreso`, `Grabación sin progreso no soportada`: visibles o reproducibles vía modo Pruebas.

El prototipo HTML simula todos estos contextos con datos locales. Las pruebas automatizadas que los recorren se encuentran en `tests/regresion.py`; la validez real de Android TV todavía requiere aceptación con mando físico y motor multimedia.
