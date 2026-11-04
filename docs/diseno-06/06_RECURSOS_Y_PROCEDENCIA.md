# Recursos de MiVisor TV · procedencia, formatos y entrega

## Marca original de esta propuesta

| Archivo | Formato | Uso |
|---|---|---|
| `marca/orbita.svg` | SVG editable | Símbolo principal de MiVisor TV |
| `marca/orbita_mono.svg` | SVG editable | Símbolo monocolor |
| `marca/orbita_64.png`, `marca/orbita_512.png` | PNG | Pruebas de tamaños de launcher y UI |
| `marca/banner_orbita_1920x720.jpg` | JPG | Concepto de banner de Android TV |
| `marca/arco.svg`, `marca/arco_mono.svg` | SVG editable | Alternativa visual |
| `marca/arco_64.png`, `marca/arco_512.png` | PNG | Escalado de alternativa |
| `marca/banner_arco_1920x720.jpg` | JPG | Banner alternativo |

Todos los símbolos propios se entregan como propuestas originales del rediseño. **Antes de registrar o lanzar marca es necesario realizar búsqueda de similitudes y disponibilidad de signo**, que no forma parte de este paquete.

## Materiales de contenido de la demostración

| Archivo | Origen | Restricción |
|---|---|---|
| `assets/stadium.jpg` | Imagen ilustrativa aportada en revisiones previas del prototipo | No representa un partido real concreto; reemplazable |
| `assets/stadium_art_generated.png` | Ilustración sintética de estadio creada para esta serie de propuestas | Imagen conceptual, no fotografía factual |
| `assets/ath.jpg`, `assets/dep.jpg`, `assets/vill.jpg`, `assets/espa.jpg` | Miniaturas recortadas del material visual aportado | Baja resolución; sustitución necesaria |
| `assets/dazn_clean.png`, `assets/Mplus_clean.png` | Logos de canales extraídos de capturas de referencia | Marcas de terceros; verificar permiso y exportar masters |

Los recursos de terceros se usan exclusivamente en el contexto de revisión del prototipo. **No se otorga licencia de marca, fotografía, logo deportivo ni redistribución comercial**. La disponibilidad de logotipos debe ser metadato del catálogo, con reemplazo neutro cuando falte.

## Reglas de producción
- SVG de símbolos propios: preservar proporciones y área de seguridad. No aplicar gradientes arbitrarios a cada pantalla.
- PNG y JPG: generar densidades adecuadas y optimizar memoria; no cargar simultáneamente banners 4K en todas las celdas.
- Logos horizontales: contenedor fijo 65×65 (muestra de referencia), imagen con `contain`, padding interior, sin deformación.
- Logos sin imagen: icono neutro de señal y nombre del canal completo, sin letras inventadas como logotipo.
- La miniatura de estadio de Inicio es fondo de atmósfera. Nunca utilizar un jugador/estadio no identificado para afirmar cobertura de un partido concreto.
- La demo no distribuye fuentes tipográficas binarias.
