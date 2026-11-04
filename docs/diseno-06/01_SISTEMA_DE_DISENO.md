# MiVisor TV · Sistema de diseño 06
**Estado:** especificación de entrega técnica **propuesta**, con prototipo HTML ejecutable y pruebas automatizadas. **No equivale a una implementación Android TV nativa validada.**

## 1. Dirección aprobable propuesta
**«Arena editorial»**: profundidad y fotografía de Cinema para el hero; jerarquía legible y composiciones asimétricas de Editorial; funciones rápidas y estructura informativa de Live Desk. Se conserva el nombre MiVisor TV y el tratamiento grafito / marfil / champán / verde agua de la revisión anterior. Las secciones comparten iconos de trazo, superficies, alineaciones, estados y comportamientos.

**Arquitectura elegida: rail lateral compacto expandible en superposición**. Comparativa:

| Alternativa | Ventaja | Coste | Decisión |
|---|---|---|---|
| Lateral expandido permanente | Claridad del texto | Reduce espacio para eventos y canales | Rechazada como diseño predeterminado |
| Rail solo iconos | Densidad y velocidad | Mayor carga de recuerdo; reduce descubribilidad | Rechazada sola |
| **Rail compacto que expande al recibir foco** | Foco estable, etiquetas bajo demanda, contenido sin reflujo | Necesita foco y animación robustos | **Elegida** |
| Híbrido rail + barra superior con secciones duplicadas | Accesos redundantes | Confusión y más pulsaciones | Rechazada |

**Orden canónico:** Inicio → Canales → Agenda → Buscar → Mi lista → Ajustes. Historial pertenece a Mi lista → Vistos; proveedores, Ace Stream, VPN y diagnósticos se encuentran en Ajustes. No se borra su funcionalidad: se reubica para reducir niveles primarios.

## 2. Base geométrica y pantallas

La demo utiliza lienzo **1600 × 900 px de diseño**, relación **16:9**, con escalado proporcional. Se prueba a 1280×720, 1920×1080 y 3840×2160. **Los píxeles del HTML no son valores Android dp.** Para la app real: construir desde un diseño lógico adaptable con `dp`, densidades y controles de enfoque nativos. Usar `LazyColumn`/`LazyRow` con claves estables y `FocusRequester`/traversal definidos.

| Elemento | Lienzo 1600×900 | Especificación / condición |
|---|---:|---|
| Zona segura para controles y texto crítico | 80 px horizontales / 45 px verticales (5%) | Fondos a sangre permitidos; comprobar overscan por modelo |
| Rail reservado | 170 px | Área interactiva empieza en x = 82; no desplaza contenido al abrir |
| Rail visual expandido | 338 px sobrepuesto | Duración 180 ms; no cambia anchura del cuerpo |
| Barra superior | 87 px | Información técnica en una sola línea; sin widgets extensos |
| Padding del contenido | 62 px laterales / 33 px superior | Línea común para títulos, filtros y listas |
| Rejilla general | 8 px base; múltiplos de 4 | Espaciados 8 / 12 / 16 / 20 / 24 / 32 / 40 |
| Hero Inicio | 480 px alto | Imagen y degradados, texto a la izquierda, 2 CTA máximo |
| Tarjeta directo Inicio | 124 px alto | Cuatro tarjetas por fila a 1600; mínimo lógico de 2 líneas de texto |
| Tarjeta de competición | 115 px alto | Solo nombre, seguimiento y navegación |
| Filas de agenda | 89 px mínimo | Hora, competición, título, escudos opcionales, estado |
| Tarjeta lógica de canal | 110 px alto | 3 columnas, logotipo 65×65 px, padding 12 px |
| Columna de proveedor | 205 px | 53 px por entrada; texto largo en 2 líneas |
| Columna de familia | 201 px | 53 px por entrada |
| Fila compacta de historial/búsqueda | 76 px mínimo | Se amplía por accesibilidad |
| Botón principal | 51 px alto | Padding 22 px horizontal |
| Botón secundario | 41 px alto | Padding 14 px |
| Filtro / chip | 40 px alto | Se expande horizontalmente sin cortar texto |
| Fecha agenda | 111×67 px mínimo | Tres niveles: día, fecha, foco/selección |
| Panel contextual | 470 px ancho | x = borde derecho −53 px; y =47 px; abajo=47 px |
| Logo en canal | Área 65×65 px | Fondo estable, imagen interior máximo 47×47, `contain`; nunca estirar |
| Escudos agenda | 30×30 px | 2 escudos separados por “VS”; si faltan, glifo neutro |
| Escudos ficha | 58×58 px | `object-fit: contain` |
| Foco | borde champán 3 px + anillo oscuro 3 px | Sin `scale()` que cruce a vecinos; sombra ≤24 px |

**Notas de implementación:** el rail del HTML ocupa más que un rail habitual por respetar la zona segura también en televisores antiguos. Si el dispositivo garantiza ausencia de overscan, puede estudiarse una variante de menor anchura **solo con validación de QA**; no aplicar el cambio de forma automática.

## 3. Tokens cromáticos

| Token | HEX | Uso exclusivo recomendado |
|---|---|---|
| `background.0` | `#080F19` | Vista completa / plano negro azulado |
| `navigation.0` | `#091421` | Rail y barra superior |
| `surface.1` | `#132232` | Tarjetas secundarias |
| `surface.2` | `#1A2A39` | Contenedor con interacción |
| `surface.focus` | `#223546` | Superficie enfocada |
| `stroke.default` | `#304555` | Bordes no activos |
| `text.primary` | `#F7F0E6` | Información principal |
| `text.secondary` | `#B8C5CE` | Metadatos, siempre no esenciales |
| `text.tertiary` | `#8D9FAA` | Pistas de menor relevancia |
| `action.primary` | `#E9C99E` | CTA / foco, no en badges decorativos |
| `action.focus` | `#FFE1B8` | Foco alto contraste |
| `status.good` | `#9FD6C8` | Disponibilidad informada, activo real |
| `status.live` | `#E58C83` | Emisión en directo cuando lo confirma la fuente |
| `status.error` | `#EF9185` | Error o cancelación |

Los colores de estado nunca son el único portador del significado: acompañar de texto y semántica de accesibilidad. El fondo de imagen necesita degradado izquierdo oscuro al menos hasta que el contraste sea legible; revisar contraste en imágenes extremas con una prueba automática o una máscara definida.

## 4. Tipo, iconografía, materiales

- **Tipografía:** Inter / Roboto / alternativa licenciada según plataforma; pesos 400, 600, 700, 800. En demo se usan fuentes del sistema y no se distribuyen archivos de fuentes.
- Escala del lienzo: display hero 61 px / título de página 41 px / título de sección 24 px / título de tarjeta 16–19 px / cuerpo 15–17 px / metadata 12–14 px. **Para producción, validar todo texto de 12 px con 3 metros reales y modo de texto grande; elevar si no supera prueba.**
- Titulares: máximo 2 líneas en hero, sin cortar equipos. Si no caben, reducir el tamaño dentro del rango del componente o pasar a un layout de 2 líneas explícito, jamás `overflow:hidden` indiscriminado.
- Nombres de canal: 2 líneas máximo en tarjeta, siempre accesibles completos en la vista de información. EPG con nombres largos: 2 líneas y fila de altura variable. No truncar información crítica de acción / error.
- Iconografía: trazo 1.8–2.0 px en rejilla SVG de 24×24; familias equivalentes visualmente, nunca mezclar emoji ni iconos tridimensionales con el mismo nivel de acción.
- Radios: controles 9–11 px; tarjetas 12 px; paneles 17 px; no aplicar distintos radios por pantalla sin componente específico.
- Sombras: elevar con foco, no colorear todas las tarjetas con neón. Superficies separadas por borde `#304555` y pequeña diferencia de luminancia.
- Fotografía: tratar como *poster art*, nunca presentar foto de otro encuentro como un evento real. Si la foto carece de trazabilidad, usar estadio genérico con identificadores y escudos autorizados. Imagen de hero preferida en ancho >1600 px, comprimida y cargada antes de mostrar Inicio.

## 5. Estados y movimiento

| Estado | Forma | Tiempo |
|---|---|---|
| Reposo | Superficie normal / texto principal | — |
| Foco | Borde 3 px y doble halo, cambio de fondo | 120–160 ms |
| Presionado | Escala `0.992`, respuesta inmediata | ≤80 ms |
| Seleccionado | Fondo persistente y color champán; **distinto** de foco | — |
| Deshabilitado | Opacidad reducida + texto o razón explícita | — |
| Panel de fuentes | Desplazamiento opcional + opacidad; márgenes fijos | 180–220 ms |
| Cambio de vista | Fundido leve sin recargar estructura superior | 160–220 ms |
| Intro / preparación | Trazo orbital y aparición de marca | Se interrumpe cuando Inicio está listo; sin retardo mínimo |
| Error / recuperación | Spinner solo en carga, mensaje discreto y accesible | Reintento según motor, nunca bucle infinito |
| Movimiento reducido | Sin parallax, sin zoom y transiciones instantáneas | 0–50 ms |

La demo representa las animaciones CSS; el render nativo debe medir caídas de frames en TVs de baja potencia. No se recomienda vídeo de hero en autoplay.

## 6. Logo / marca

**Principal: Órbita.** No es una M ni un icono Play. Representa señal, continuidad y punto de atención deportiva. Versiones de color, monocroma, 64 px, 512 px, 1920×720; símbolo separado del logotipo. Reutiliza el mismo gesto gráfico en el arranque y en el rail.

**Alternativa: Arco.** Entrada al estadio y un umbral visual. Se mantiene la tipografía y la paleta. Ambos están incluidos en `/marca`. No se recomienda introducir un tercer icono en la app; tras escoger uno, el otro queda como propuesta descartada/documentada.

### Precisión de producción
- `banner_orbita_1920x720.jpg` y `banner_arco_1920x720.jpg` son **propuestas**; verificar requisitos vigentes del launcher, relación de aspecto, máscara/crop y adaptative icon de destino.
- Comprobar legibilidad de ambos a 48, 64, 128 y 512 px, fondo claro/oscuro, sin depender de pequeñas líneas o halo exterior.
- El símbolo UI no es un proveedor, canal ni campeonato y nunca debe sustituir sus logos.

## 7. Matriz de legibilidad pendiente

Para cerrar producción: televisor 32″ 720p, 43″ 1080p y 55″ 4K; visionado a 2,5–3,5 m; textos ×1,2 y ×1,4; overscan; fondo hero muy luminoso; nombre de canal >60 caracteres; subtítulos; zoom del sistema; alto contraste; daltonismo; foco tras scroll de 500 entradas.

**Referencias públicas de plataforma:** Android Developers *Focus system*, *TV navigation*, *Build adaptive apps for TV*, *Create scrollable layouts for TV*, *Playback controls on TV* (enlaces en README). Estas reglas son recomendaciones específicas de MiVisor apoyadas en dichas guías, no valores normativos oficiales de Google.
