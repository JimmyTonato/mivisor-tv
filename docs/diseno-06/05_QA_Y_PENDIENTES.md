# MiVisor TV · Evidencias de QA y dependencias sin resolver

## Pruebas ejecutadas

Se ejecutaron **39/39 comprobaciones superadas** en Chromium headless, sin errores de JavaScript en los flujos probados (`tests/resultado.json`). Se incluyeron: navegación entre seis secciones, OK desde canal, agrupación DAZN por familias, búsqueda mediante teclado, Agenda Motor, ficha de F1, abrir/cerrar Dónde verlo, carga del reproductor, Fuentes/Audio/Subtítulos, Atrás en tres niveles, preservación de canal/proveedor/familia/foco, ausencia de cambio accidental de canal al usar flechas con controles ocultos, Inicio sin conexión, agenda vacía, error de fuente, replay con progreso y escalado a 720p/1080p/4K.

**Advertencia:** las capturas 720p/4K prueban solo el escalado de un lienzo fijo del navegador. No prueban densidad `dp` del sistema, sobreescaneo real ni interfaces Android Compose. El resultado 39/39 no reemplaza pruebas instrumentadas y con usuarios.

## Matriz de aceptación formal antes de aprobar implementación

| ID | Prueba en televisor | Criterio |
|---|---|---|
| TV-01 | Arranque frío con datos cacheados | Entrada a Inicio sin tarjetas vacías intermedias |
| TV-02 | Arranque frío offline | Intro termina en Inicio degradado o error crítico explicado |
| TV-03 | Arranque con API secundaria que no responde | No bloquea el catálogo ni la selección de señal |
| TV-04 | 720p a 3 m | Texto, márgenes, foco y tarjetas legibles, nada cortado |
| TV-05 | 1080p y 4K | Misma jerarquía, no multiplicar dp según número de píxeles |
| TV-06 | Overscan activado | Ninguna acción o etiqueta indispensable fuera del 5% seguro |
| TV-07 | 1.000 canales con 3.000 fuentes | Scroll sin bloqueo, virtualización de logo y canal lógico estable |
| TV-08 | Nombres extensos / idiomas de distinta longitud | 2 líneas y acceso a nombre completo, sin superposiciones |
| TV-09 | Seis secciones de rail | Focus graph y pulsaciones previsibles sin bucles |
| TV-10 | Canal -> player -> Atrás | Regresa exactamente a selección y scroll del canal |
| TV-11 | Ficha -> Dónde verlo -> player -> Atrás | Vuelve a ficha; panel no reaparece accidentalmente |
| TV-12 | D-pad oculto del player | Ninguna flecha cambia el canal ni abre panel espontáneamente |
| TV-13 | Emisión lineal sin DVR | Sin pausa operable ni timeline ficticio |
| TV-14 | Timeshift/archivo válido | Pausa y progreso basados en capacidades reales |
| TV-15 | Fuentes a medias / fallando | Recuperación con cancelación; no oculta error persistente |
| TV-16 | Audio/subs sin pistas | Panel vacío/disabled correcto, no idioma inventado |
| TV-17 | Eventos cancelados/aplazados | Estado explícito; no CTA de directo si no hay canal |
| TV-18 | Motociclismo/F1 | Sesión, circuito, GP; sin equipos enfrentados |
| TV-19 | Lectura con texto grande / accesibilidad | Foco no queda fuera del viewport; labels semánticos |
| TV-20 | Vinculación QR real y VPN permisos | Seguridad, caducidad y errores explicados |

## Límites actuales y riesgo

| Prioridad | Elemento | Riesgo | Mitigación |
|---|---|---|---|
| Bloqueante producción | Inventario de proveedor/canales y source IDs | Fusiones falsas o duplicados | ID fiable y reglas de alias aprobadas |
| Bloqueante producción | Media3/Ace y pista/estado verdaderos | Controles que prometen funciones inexistentes | API de capacidades y state machine |
| Bloqueante producción | API EPG y asociaciones evento-canal | Badges en directo incorrectos | Timestamps y `stale`/`unknown` |
| Bloqueante producción | Licencias logos/escudos/fotos | Uso comercial no autorizado | Material oficial con permisos/alternativas neutras |
| Alto | Test hardware remoto | Foco inesperado o textos ilegibles | Matriz TV-01…20, 3 marcas de televisores |
| Alto | Intro parcialmente conectada | Bloqueos de arranque | Cache-first, deadline y retries |
| Alto | Persistencia favoritos/recordatorios | Pérdida de selección | IDs estables y persistencia |
| Medio | Teclado TV y voz | Latencia/posición foco | Componente nativo de teclado y teclado D-pad |
| Medio | Optimización de recursos | Caída de frames/consumo de RAM | Imágenes optimizadas, reciclado de lista, sin autoplay en Inicio |

## Checklist de recursos

- Iconos propios: SVG y PNG exportados en `marca/`; uso comercial sujeto a revisión final de marca.
- Banner propia: jpg 1920×720 ilustrativo; comprobar políticas del dispositivo y recortes reales.
- Escudos: miniaturas de las capturas aportadas, **no son masters**. Reemplazar por SVG/PNG de calidad con autorización.
- DAZN y Movistar: logos extraídos del material de referencia; marcas de terceros, no recursos de producción con licencia incluida.
- Fondo del estadio: imagen conceptual y genérica. No se debe presentar como foto del Atlético–Deportivo o cualquier encuentro concreto.
- Fotografías de eventos: **pendientes de backend/CDN y permisos**. No generar falsas capturas de partidos.

## Ajustes solicitados que el prototipo todavía NO implementa completamente

- Pantallas nativas de idioma, zona horaria y accesibilidad: documentadas; algunas fichas en HTML son **explicativas** porque no hay Android Settings API conectada.
- Pistas reales de Audio/Subtítulos: la demo muestra variantes inventadas para probar UI, y un estado sin pistas, pero el catálogo final debe proceder del motor.
- Gestión segura de proveedores, QR y credenciales: pantallas de flujo y contratos, sin conexión real.
- Historial persistente, recordatorios del sistema y favoritos entre sesiones: interacciones de demo, no base de datos.
- Métricas de accesibilidad (TalkBack, tamaño grande) y rendimiento 4K: no validadas.
- El archivo de reproductor v18 independiente aún no está disponible para revisar eventos de teclado y lógica original con total fidelidad.

**Resultado:** material suficientemente preciso para que desarrollo comience estructura de UI y contratos, pero **no debe considerarse aceptado para producción** sin cerrar los bloqueos enumerados.
