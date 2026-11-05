# MiVisor TV · Entrega 3g: rendimiento, competiciones y portada doble

Se conserva todo lo anterior y el paquete `com.mivisor.tv`. La 3f está en `docs/entregas/3f.md`.

## 1. Rendimiento en teles modestas (TV stick)

**Lo que pasaba:** con muchos proveedores, Inicio se quedaba en «Leyendo tus proveedores…» durante minutos y luego todo iba lento.

### Medición

Banco de pruebas en la JVM con **12 proveedores y 7.200 canales** (y 3.500 picons). Para parecerse a un TV stick, la JVM va en modo intérprete, sin compilar (`-Xint`).

| | Antes | Ahora |
|---|---|---|
| Montar la biblioteca de canales | **14,3 s** | **4,9 s** en frío, **1 s** las siguientes |
| Limpiar los nombres de los catálogos | 1,9 s | 0,8 s |
| Agrupar en series (pantalla Canales) | 0,7 s | 0,09 s |
| Veces que se monta la biblioteca al arrancar | **hasta 4** | **normalmente 1** |

Con la JVM normal (compilada), montar la biblioteca tarda **0,3 s** la primera vez y **0,05 s** después.

### Qué se ha cambiado

1. **Content ID sin expresiones regulares.** Se comprobaba con una expresión regular, dos veces por fuente. Ahora es una comprobación a mano.
2. **Nombres memorizados.** El mismo nombre llega de muchos proveedores y en cada recarga. La limpieza y la lectura de cada nombre se calculan una vez y se recuerdan. La memoria está acotada para no pesar en 1 GB.
3. **Ordenación lineal.** Antes buscaba en la lista dentro de cada comparación, lo que la hacía cuadrática.
4. **No reconstruir si nada cambió.** Al abrir la app, la copia guardada y la «nueva» suelen ser iguales. Una huella de lo leído lo detecta y la biblioteca no se vuelve a montar.
5. **Logos sin reagrupar.** Cuando llegan logos nuevos (catálogo, picons o logos de la agenda), solo se cambian los logos de los canales ya montados.
6. **Agenda ↔ canales con índice.** Para saber en qué canal de tus proveedores se ve cada evento, se recorrían todos los canales por cada nombre de la agenda, y en el hilo de la pantalla. Ahora hay un índice de palabras y los resultados se recuerdan. Inicio, Agenda y Buscar lo notan.

Hay pruebas nuevas en `LibraryPerfTest`: la huella, el cambio de logos sin reagrupar y que el índice da lo mismo que antes.

### Comprobación: el funcionamiento es el mismo

Las optimizaciones no cambian ninguna regla: solo evitan repetir cálculos y usan índices. Para demostrarlo, se ejecutaron **la versión anterior (3f) y la nueva** con los mismos datos y se compararon sus resultados línea por línea.

**Los datos:** 6 juegos de 12 proveedores con 400 canales cada uno, con nombres difíciles:
- prefijos «ES |» y banderas;
- mayúsculas;
- números pegados;
- calidades raras («1080p50», «(Backup)»);
- enlaces en mayúsculas, rotos y HTTP.

**Resultado:** 62.725 líneas comparadas.

| Qué se compara | Resultado |
|---|---|
| Catálogos de cada proveedor (nombre, enlace, tipo) | **Idéntico** |
| Biblioteca: 58.120 líneas de canales y fuentes (id, nombre, marca, familia, logo, categoría, calidad) | **Idéntico** |
| Apartados por marca | **Idéntico** |
| 1.692 búsquedas agenda → canal | **Idéntico** |
| Grupos (series) | Igual salvo 5 empates de orden |

Esos 5 empates no vienen de la optimización. Vienen del arreglo 3f.1 del orden natural, que evita que Android aborte con nombres raros: ahora, si dos canales se llaman igual salvo por mayúsculas («MOVISTAR PLUS+ 3» y «Movistar Plus+ 3»), siempre sale primero el mismo.

Los dos programas están en `tools/perf/` para repetir la comparación y la medición:
- `EquivalenceDump.kt`: la comparación;
- `LibraryBench.kt`: el banco de pruebas.

### Medición real en la tele

**Ajustes › Diagnóstico › «Rendimiento de este arranque»** enseña lo que tarda de verdad **ese aparato**.

- **Datos del aparato:** modelo, Android, RAM total y de la app, si Android lo considera «de poca memoria» y núcleos.
- **Tiempos:**

| Tiempo | Qué mide |
|---|---|
| Leer proveedores (guardados) | Leer los proveedores desde la tele |
| Leer proveedores (de Internet) | Descargar los proveedores |
| Montar canales | Juntar los canales; si nada cambió, dice «sin cambios: se reutiliza» |
| Cambiar logos | Poner los logos nuevos a los canales ya montados |
| Agenda guardada en pantalla | Desde que se abre la app hasta ver la copia guardada |
| Agenda de Internet | Descargar la agenda nueva |
| **Inicio con canales** | **Desde que se abre la app hasta ver Inicio con los canales** |

También van al principio del informe que se envía al móvil, en Diagnóstico › «¿Algo no va bien?». Con esos números del TV stick se puede decidir la siguiente optimización con datos reales.

## 2. Competiciones: elige cuáles ver

**Lo que guarda la app.** No hay un listado oficial de competiciones, así que **la app guarda las que llegan en cada agenda**, con su nombre real: Copa Libertadores, Liga MX, NBA… Están en `filesDir/competitions.json`. Las que llevan 90 días sin aparecer se olvidan, salvo las que hayas ocultado o seguido.

**Ajustes › Competiciones:**
- Están agrupadas por deporte, con cuántos eventos tiene cada una ahora.
- **OK** cambia el estado: **Visible → ★ Seguida → Oculta → Visible**.
  - **Oculta:** sus eventos desaparecen al momento de Inicio, Agenda y Buscar, sin descargar nada. Por ejemplo, desmarcar la Libertadores.
  - **Seguida:** sale en Inicio › «Competiciones seguidas».
- **«Mostrar todas»** quita todas las ocultaciones.
- Las ocultas y las seguidas se guardan en la tele y en la **copia cifrada**.

**Página de cada competición:**
- Funciona también con las competiciones reales. Desde la ficha de un partido de la Libertadores, el botón lleva a «Copa Libertadores», no al genérico «Más fútbol».
- Tiene el botón **Seguir competición**.

## 3. Portada con los dos partidos principales

Cuando hay al menos dos eventos destacados, la portada enseña **dos partidos lado a lado**:
- **Cuáles:** primero los directos y luego lo más importante de las próximas 24 horas.
- **Qué muestra cada uno:** su estado (EN DIRECTO / HOY · 21:00), competición, escudos y canal.
- **Mando:** **OK** lo reproduce en su canal más fiable (si no está en tus proveedores, abre su ficha). **Mantener OK** abre la ficha con todas las opciones.
- **Debajo:** «En emisión y a continuación» ya no repite esos dos.

Si solo hay un evento destacado, sale la portada grande de antes. «Seguir viendo» solo aparece cuando no hay ningún partido.

## 4. Qué probar

| # | Prueba | Debe pasar |
|---|---|---|
| 1 | Abrir la app en el TV stick con tus 12 proveedores (no la primera vez) | Tras la intro, Inicio con canales en pocos segundos; no se queda en «Leyendo tus proveedores…» |
| 2 | Moverse por Canales y Agenda | Más fluido que antes |
| 3 | Ajustes › Competiciones › Copa Libertadores › OK hasta «Oculta» | Desaparece de Inicio, Agenda y Buscar |
| 4 | Ajustes › Competiciones › una › OK hasta «★ Seguida» | Sale en Inicio › «Competiciones seguidas» |
| 5 | Ficha de un partido de la Libertadores › botón de la competición | Abre «Copa Libertadores» con «Seguir competición» |
| 6 | Inicio con partidos en directo | Portada con dos partidos; OK reproduce, mantener OK abre la ficha |
| 7 | Ajustes › Diagnóstico | Panel «Rendimiento de este arranque» con el aparato y los tiempos. **Pásame una foto** |

## Compilación

- **Pruebas:** pasan las **197 pruebas** de los módulos JVM.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions; si falla, pásame el log.
- **Tiempos:** los del banco de pruebas son de la JVM, no de la tele. El modo intérprete es una aproximación pesimista a un TV stick; en la tele real el código va compilado.
