# MiVisor TV · Auditoría, correspondencia y traspaso de versión
**Fuentes analizadas:** `MiVisor TV · Propuesta visual 3e.html`, `MiVisor TV · Propuesta visual 3e (1).html`, capturas proporcionadas de Inicio, Canales por proveedor, Historial, Reproductor, Ajustes/Ace Stream/VPN/QR, la grabación de uso de MiVisor TV y las revisiones visuales 04 y 05. **No se ha recibido un APK, repositorio de código ni una grabación específica del reproductor v18 por separado**: su comportamiento nativo no puede verificarse con este material.

## 1. Problemas concretos detectados

| Pantalla / función | Evidencia de material | Problema de diseño | Decisión 06 |
|---|---|---|---|
| Inicio 3e (1) | Hero deportivo con múltiples carruseles y rail activo | Algunas filas tienen mismo peso que el directo principal | Hero envolvente con datos concretos, 4 accesos inmediatos y ritmo variado al bajar |
| Inicio de aplicación grabada | Más acceso a eventos, pero prioridad variable según datos | Primera pantalla no distingue claramente lo urgente de lo frecuente | Elección de destacado según estado y fallback offline, continuación de señal debajo |
| Agenda de prototipos / grabación | Fechas funcionales, múltiples competiciones, panel «Dónde verlo» | Panel de canales ocupa anchura incluso sin seleccionar evento; agrupar deportes puede romper cronología | Fecha → deporte → competencia filtrada → eventos globalmente cronológicos; panel contextual en ficha |
| Canales 3e (1) | Proveedores, grupos, tarjetas visualmente grandes | Nombres similares aparecen separados, mucha navegación vertical | Proveedor y familia filtrables, tarjetas 110 px y fuentes agrupadas bajo canal lógico |
| Ficha deportiva | Escudos y datos; grandes zonas vacías en captura real | Acciones y metadatos sin jerarquía | Cabecera de 285 px, detalles deportivos específicos, acción «Dónde verlo» contextual |
| Reproductor de prototipos | Controles ricos, audio/subtítulos/fuentes | Controles secundarios pueden tapar el vídeo; información no garantizada por fuente | Un solo Player Cinema, controles transitorios, panel anclado, timelines condicionados |
| Pantalla de carga | Nombre de canal e información de conexión | Riesgo de textos grandes en el centro y tiempos artificiales | Spinner centrado, canal discreto esquina y estado bajo indicador |
| Ajustes Servicios | Ace Stream, VPN/WARP, Football-Data, Live Tennis, Jolpica, Blacktop | Abundancia de técnica en la navegación principal afecta la sensación premium | Servicios y diagnósticos permanecen bajo Ajustes, agrupados, con estados explícitos |
| Proveedor QR / código | Dos vías de vinculación en capturas | Un QR ficticio en prototipo parece funcional sin serlo | Pantalla de flujo con estado pendiente; contrato de token y expiración real |
| Historial | Últimas señales / eventos en apartado separado | Duplica concepto con favoritos | Mi lista incluye «Vistos», conserva accesos |
| Búsqueda | Teclado para mando y resultados | Entradas de resultados mixtos pueden perder tipo | Resultados tipados para canal/evento, foco y Enter según tipo |
| Identidad 04/05 | Órbita, Arco, Pulso, paleta oscura | Demasiados finalistas y dos modos de reproductor | Órbita principal / Arco alternativo, un solo reproductor principal |

**Problemas adicionales:** capturas de marca de terceros con baja definición; logos escalados sin margen interior; un mismo color utilizado para foco y selección; un resultado de búsqueda que se abre como evento aunque representa un canal; navegación que no restaura scroll o foco; progresos de directos lineales no verificados; imágenes de partidos no necesariamente asociadas a la retransmisión. Todos son riesgos importantes de producto aunque el usuario no los hubiera enumerado explícitamente.

## 2. Tabla de correspondencia con aplicación actual

| Función de referencia | Ubicación propuesta | Estado en HTML | Trabajo Android nativo |
|---|---|---|---|
| Inicio y destacado | Inicio · hero | Navegable, datos simulados | Selección por EPG y caché |
| En directo | Inicio · bloque prioritario; Agenda · estado «live» | Navegable | EPG y frescura |
| Agenda por fechas | Agenda · selector horizontal | Navegable | Timezone, paginación, API |
| Deportes y competiciones | Agenda · filtro y rail | Navegable | Taxonomía real, persistencia de favoritos |
| Ficha partido / motor | Evento | Navegable y diferente por deporte | Contrato Event y assets reales |
| Dónde verlo | Evento · panel contextual | Navegable | Asociación de canales real |
| Recordatorios | Evento / Mi lista | Activación solo en sesión | AlarmManager/notifications/persistencia |
| Canales por proveedor | Canales → Proveedor | Navegable, catálogo fixture | Proveedor ID y mapeo |
| Familias y categorías | Canales → Familia / Categoría | Navegable | Clasificación y validación |
| Fuentes alternativas | Reproductor → Fuentes | Selección simulada | Media engine + fallbacks |
| Reproductor carga / fallo | Reproductor | Simulado | Media3/ExoPlayer/Ace |
| Audio / subtítulos | Reproductor → panel | Simulado | Tracks y selección real |
| Pausa / DVR | Reproductor con capacidad positiva | Estado visual de replay | Timeshift/seek real |
| Búsqueda | Buscar | Teclado y rutas reales en demo | Índice completo con 1.000+ canales |
| Favoritos | Mi lista → Favoritos | Toggle por sesión / enlace directo | Persistencia y sincronización |
| Historial | Mi lista → Vistos | Simulado | Historial local o cuenta |
| Añadir proveedor, QR y código | Ajustes → Canales → Añadir proveedor | Pantalla explicativa | Auth, token, código, expiración |
| Verificación de fuentes | Ajustes → Reproductor → Diagnóstico | Listado simulado | Comprobación real |
| Ace Stream | Ajustes → Servicios → Ace Stream | Pantalla y estado simulado | Adaptador/motor, permisos |
| VPN/WARP | Ajustes → Servicios → VPN | Pantalla y estado simulado | API VPN, permisos, errores |
| APIs deportivas / datos | Ajustes → Servicios → APIs | 4 conectores representados | Estados reales y reintentos |
| Ajustes Interfaz / General / Datos | Ajustes → categoría → detalle | Navegable, algunas fichas informativas | Persistencia y opciones nativas |
| Diagnóstico y pruebas | Ajustes → Pruebas | Estados de aceptación de la demo | Debug only, no producción |
| Splash / intro | Arranque · preparación | Animación con recursos locales y fallback | Startup coordinator nativo |

### Qué se ha conservado
- Todas las áreas funcionales detectadas en referencias.
- Entrada con OK directo al canal en biblioteca.
- Fecha horizontal de Agenda.
- Reproductor audiovisual a pantalla completa con fuentes, audio y subtítulos.
- Estados de servicios y proveedores.

### Qué se ha modificado
- Hero de portada: ya no se presenta dentro de un rectángulo independiente.
- Menú: rail seguro expandible por foco, seis destinos de primer nivel.
- «En directo» pasa a Inicio + Agenda (no a pantalla vacía separada).
- «Historial» se integra en Mi lista para reducir navegación principal.
- Grupos de canal pasan de listas de tarjetas por nombre a proveedor → familia → canal lógico; versiones de calidad no se duplican si hay confirmación.
- Agenda conserva cronología aunque haya filtro por competición.
- Pantallas de error y carga no implican datos falsos.
- Reproductor con un solo patrón de controles (Cinema), sin Broadcast paralelo.

### Qué se ha eliminado deliberadamente del diseño de primer nivel
- Panel fijo «Dónde verlo» de la Agenda (se reemplaza por un panel contextual).
- Cambio de canal implícito con flechas dentro del reproductor (requiere selección deliberada en biblioteca).
- Progreso ficticio y marcador supuesto en directos lineales.
- Pantalla «Saltar introducción» y duración mínima de splash.
- Duplicación de secciones en navegación primaria. **Estas decisiones eliminan UI redundante, no funciones esenciales.**

## 3. Orden de implementación recomendado

| Etapa | Entrega de ingeniería | Dependencia de aceptación |
|---|---|---|
| P0 - 1 | Tokens + Rail + Focus Graph + navegación Back | Aceptación con mando en 720/1080/4K |
| P0 - 2 | Catálogo normalizado proveedor/familia/canal, búsqueda/favoritos | 1.000+ canales, cero fusiones falsas |
| P0 - 3 | Player Cinema + Media3/Ace adapter + fuentes + pistas + error/recuperación | Pruebas reales de reproducción y cambio de fuente |
| P0 - 4 | Agenda + ficha + dónde verlo + estados de datos | Horas correctas, eventos sin metadatos y canal asociado |
| P1 - 5 | Inicio dinámico, caché, seguir viendo y recordatorios | No bloquear navegación por API externa |
| P1 - 6 | Ajustes/servicios, QR/proveedor, diagnósticos | Permisos, autenticación y fallos parciales |
| P1 - 7 | Marca final, lanzador e introducción funcional | Validación de tamaño, contraste y arranque frío |
| P2 - 8 | Accesibilidad extendida, idiomas, instrumentación | QA físico + usuarios a 3 m |

**P0, P1 y P2 representan prioridad de integración, no plazos ni un contrato económico.**

## 4. Requisitos de aceptación pendientes del lado de desarrollo

- Confirmar esquema real de `providerId/channelId/sourceId`, reglas de URLs, metadatos y aliases en la app actual.
- Confirmar si Ace Stream y otros protocolos soportan pause/seek y cómo obtienen pistas.
- Confirmar forma de obtener logos/escudos y permisos para uso comercial.
- Probar `d-pad` en dispositivos reales; lo verificado en Chromium es una aproximación.
- Determinar el tamaño mínimo de texto legible de modo objetivo con usuarios; no aprobar solo por preview.
- Aclarar los estados de las APIs de deporte y caducidad local; en la demo toda la programación es ficticia.
- Aclarar permisos y cuenta para QR, WARP/VPN, servicios de terceros y recordatorios.

**Conclusión de traspaso:** El HTML, los SVG y los documentos fijan la dirección, las reglas de navegación, el catálogo lógico de prueba y las decisiones visuales sin necesidad de inventar otra interfaz. La adaptación a Android, la extracción de fuentes, la sincronización de agenda y la verificación de recursos siguen siendo tareas de implementación; no hay forma responsable de declararlas resueltas solo con el prototipo.
