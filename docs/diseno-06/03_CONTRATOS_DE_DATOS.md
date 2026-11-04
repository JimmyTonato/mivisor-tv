# MiVisor TV · Contratos de datos, agrupación y dependencias

## 1. Principio de fidelidad
No hay inventario real de canales, fuentes ni APIs del desarrollador conectado a esta entrega. Los 40 canales y 14 eventos incluidos son **fixtures ficticios de revisión**, construidos para evaluar nombres, volumen, variantes, focos y estados. La presencia del nombre «DAZN 1», «Movistar LaLiga», «Atlético de Madrid» o cualquier otro **no implica acceso, derechos ni disponibilidad reales**. Los logos de la demo proceden de capturas aportadas por el usuario y algunos están en baja resolución. Para producción se deberán obtener las marcas autorizadas o un catálogo con URLs verificadas; nunca raspar logos de forma arbitraria.

## 2. Modelo normalizado sugerido
```ts
export type SourceStatus = 'unknown'|'untested'|'checking'|'ready'|'failed'|'offline';
export type EventStatus = 'unknown'|'upcoming'|'live'|'finished'|'postponed'|'cancelled';
export type Sport = 'football'|'formula1'|'motogp'|'tennis'|'other';
export interface ChannelSource {
  sourceId: string;                  // clave única y estable
  channelId: string;                 // canal lógico padre
  protocol?: string;                 // el motor define protocolos y compatibilidad
  quality?: 'SD'|'HD'|'FHD'|'4K'|'unknown';
  codec?: string; bitrateKbps?: number;
  status: SourceStatus;
  lastCheckedAt?: string;            // ISO8601 si está verificado
  errorCode?: string;                 // no mostrar crudos errores al usuario
}
export interface LogicalChannel {
  channelId: string;
  providerId?: string;               // no derivar de cadenas ambiguas
  providerLabel?: string;
  familyId?: string;
  displayName: string;
  knownAliases: string[];             // alias verificados, no coincidencia difusa
  categoryIds: string[];
  logoUrl?: string;                   // asset con derechos y resolución conocida
  isFavorite: boolean;
  sources: ChannelSource[];
  preferredSourceId?: string;         // decisión del motor, no UI
  availability: 'unknown'|'available'|'unavailable'|'no_sources';
}
export interface EventCommon {
  eventId: string; competitionId: string; competitionName: string;
  sport: Sport; startsAt?: string; timezone?: string;
  status: EventStatus; title: string;
  posterUrl?: string; metadataUpdatedAt?: string;
  channelIds: string[];              // la UI resuelve canales lógicos existentes
  reminderId?: string; resultVerifiedAt?: string;
}
export type SportEvent =
 | (EventCommon & {sport:'football';home?: Participant;away?: Participant;score?: {home:number;away:number}})
 | (EventCommon & {sport:'formula1'|'motogp';grandPrix?:string;venue?:string;session?:string;category?:string})
 | (EventCommon & {sport:'tennis'|'other';participants?:Participant[]});
export interface Participant {id?:string;name:string;crestUrl?:string}
export type PlaybackCapability = {isLive:boolean;canPause:boolean;canSeek:boolean;durationMs?:number;positionMs?:number;audioTracks:Track[];textTracks:Track[]};
export interface Track {id:string;label?:string;lang?:string;selected:boolean}
```

**Reglas de contrato:**
- `startsAt` debe almacenarse en UTC con zona y mostrarse con zona del dispositivo. No usar el texto `21:00` como verdad del backend.
- `live` requiere confirmación de proveedor EPG y `metadataUpdatedAt` reciente. Si la sincronización caduca, mostrar estado guardado / desconocido, no un directo confirmado.
- `score` y `minute` no aparecen si son null; si existe `score` debe tener procedencia fiable. No calcular el minuto del partido desde la hora de inicio.
- Disponibilidad del canal es distinta de disponibilidad de fuente: un canal lógico puede tener tres fuentes, una activa, otra sin comprobar y otra caída.
- El motor elige fuente y reintentos. La UI no selecciona automáticamente por `bitrateKbps` o por imagen de calidad.
- `channelIds` de evento siempre se resuelven contra el catálogo local; si faltan IDs, se muestra «Sin canales asociados», no un logo inventado.
- Resultados de búsqueda indexan `channelId`/`eventId`; componentes nunca dependen de índice.

## 3. Normalización segura de canales
1. Si el proveedor suministra `providerId` y un `logicalChannelId` estables, agrupar todas las `sourceId` que comparten **esa pareja exacta**.
2. Si no existe ID de canal pero hay una tabla de equivalencias **aprobada manualmente** por proveedor (por ejemplo, `DAZN 1 HD` → `DAZN 1`), usar la tabla solo dentro del mismo proveedor y conservar la trazabilidad.
3. **No fusionar** `DAZN 1` y `DAZN 1 +` o dos canales llamados igual si no se puede verificar que comparten señal. Nombre, categoría, escudo y duración parecidos no son prueba suficiente.
4. Si el proveedor no está identificado, usar `Otros / sin identificar` y su ID de lista; no deducir propietario de palabras ambiguas.
5. Inferencia opcional de familia **solo desde catálogo revisado**, nunca desde algoritmo agresivo. Las familias sin canales se ocultan automáticamente. Las categorías transversales no duplican objetos: son filtros sobre la misma colección.
6. Usar orden natural con comparación numérica dentro de cada proveedor y familia: `DAZN 2` precede a `DAZN 10`, y las variantes quedan bajo el canal lógico cuando esté confirmado.
7. Si se han fusionado fuentes, el favorito y el historial apuntan a `channelId`, no a `sourceId`, para evitar entradas duplicadas.
8. Si falla la actualización del catálogo, mantener la última versión válida y marcar `stale`; no sustituirla por una lista vacía.

Archivo `catalogo_reglas.ts`: interfaces y algoritmo propuesto para backend. Archivo `datos_ejemplo.json`: fixtures del prototipo, no mapeo comercial.

## 4. Selección de fuente y señales

Propuesta de lógica de motor (no implementada en HTML):
- Si hay `preferredSourceId` verificado y el usuario lo ha elegido, intentar esa fuente primero.
- Si falla, consultar la política de failover: candidatos compatibles por historial de éxitos y recencia de comprobación; **no** comparar calidad ausente.
- Notificar a UI transiciones `connecting → playing`, `playing → recovering`, `recovering → playing` o `recovering → error`.
- Con un error persistente, presentar código de error mapeado a texto legible y acciones `Reintentar`, `Cambiar fuente` y `Volver a canales` si proceden.
- Cancelar intentos anteriores cuando cambia el canal o el usuario pulsa Atrás; evitar reproducir señal de la pantalla anterior.
- Subtítulos/audio deben cambiarse mediante APIs reales de tracks, manteniendo `trackId`, sin inventar español/inglés.
- Controles de pausa y timeline basados en `canPause`, `canSeek`, `positionMs`, `durationMs`; si faltan, la interfaz no debe dibujar timeline ni botón de pausa disponible.

## 5. Información necesaria por vista

| Pantalla | Contrato mínimo | Datos opcionales | Fallback |
|---|---|---|---|
| Preparación | cache del catálogo y tema | estado APIs, poster precargado | Inicio degradado o error crítico |
| Inicio | canales + últimos canales | agenda, eventos, logos, posters | portada genérica y accesos a biblioteca |
| Agenda | `eventId`, `competitionId`, `sport`, `status`, `title` | horarios, escudos, metadatos | evento pendiente sin marcador |
| Ficha evento | objeto de evento | participantes, sesión, circuito | título/estado y acciones habilitadas |
| Dónde verlo | `channelIds` validados | cantidad y estado de fuentes | «Sin canales asociados» |
| Canales | `channelId`, proveedor/unknown, displayName, sources[] | logo, categorías, verificaciones | icono genérico + nombre |
| Buscar | catálogo + eventos con ID | alias de búsqueda | sin resultados y acceso a biblioteca |
| Mi lista | favoritos y `lastWatchedAt` | recordatorios, metadatos | vacío con CTA útil |
| Reproductor | `channelId`, sourceId, PlaybackState | audio/subtracks, bitrate, DVR | pantalla carga/error sin inventar info |
| Ajustes / Servicios | adaptadores estado real | versión, autoinicio, conectividad | «No disponible / no conectado» |

## 6. Dependencias para desarrollo

**Con lo que existe en el prototipo HTML solamente:** composición, foco visual, navegación de ejemplo, simulación de agrupación de 40 entradas, tipos de evento, estados visuales, marca y exportación de recursos.

**Depende de implementación nativa existente o nueva (NO entregado como funcionalidad conectada):** Media3/ExoPlayer o adaptador Ace Stream, subtítulos y pistas reales, medición de fuentes, preferencia/failover, permisos VPN/WARP, activación de proveedores con QR, EPG, resultados y calendario, autenticación y derechos, favoritos persistentes, historial, recordatorios/alarma, caché offline y telemetría.

**Datos ausentes que deben pedir al desarrollador antes del merge:** esquema real de listas/proveedores, ejemplos de respuestas, identificadores fiables de canales, soporte de timeshift por protocolo, estructura de errores y de tracks de audio, persistencia disponible y librería de iconos/logos con permisos.

**Visuales de terceros:** marcas DAZN, Movistar, Atlético, Deportivo, Villarreal y Espanyol mostradas por su relación con el material aportado, pero sus derechos comerciales no se transfieren con esta demo. No redistribuir capturas extraídas como assets finales sin autorización. Estadio es imagen ilustrativa (no de un partido real concreto); evitar usar como prueba de cobertura de un evento.
