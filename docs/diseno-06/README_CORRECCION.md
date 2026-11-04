# MiVisor TV · corrección 06.1 del reproductor

Problema reproducido: la versión 06 entraba en `ready` con `playUI=false`, por lo que los botones no se mostraban al finalizar la carga. Además, se ocultaban después de 6,2 segundos sin interacción y la señal lineal carecía de control de pausa visual.

Solución: controles visibles al completar la carga, barra de acciones compacta con Info, Fuentes, Mi lista, Pausa/Reanudar, Audio, Subtítulos, Vídeo y Ocultar; indicador de estado superior; foco por defecto en Pausa; botón «OK · Mostrar controles» y clic en la imagen para restaurarlos. Carga y recuperación mantienen el vídeo despejado.

**Aclaración técnica:** La pausa mostrada en el HTML es una capacidad simulada (`playerCanPause=true`) para probar la distribución visual. En Android TV el botón debe depender de las capacidades reales del motor y deshabilitarse si no existe pausa; las señales en directo sin DVR no tienen barra de progreso. La demora de 20 s para ocultar controles es deliberada en esta versión de revisión para facilitar su inspección. En producto se recomienda evaluar 5–7 s con usuarios.

## Recorrido
1. Abrir Canales y pulsar OK sobre un canal.
2. Tras la carga, los controles **aparecen automáticamente**.
3. Flechas para moverse entre controles y Enter para activar cada uno.
4. Fuentes / Audio / Subtítulos / Vídeo abren panel derecho.
5. Escape cierra panel; Escape oculta controles; Enter los vuelve a mostrar.
6. Con controles ocultos, Escape regresa a la biblioteca.

Prototipo visual: no hay vídeo ni fuentes reales.
