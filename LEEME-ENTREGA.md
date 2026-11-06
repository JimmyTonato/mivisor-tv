# MiVisor TV · Entrega 3j: banners de competiciones y catálogo central de proveedores (GitHub)

La 3i está en `docs/entregas/3i.md`. Se mantienen el paquete, los datos, la firma y la verificación de WARP.

Todo se lee de `JimmyTonato/mivisor-datos` igual que la agenda: las ramas `banners` y `providers` se sacan de la dirección de la agenda. Solo HTTPS, con copia local y ETag. Nunca en segundo plano. La APK no lleva ningún código ni credencial.

## Banners (rama `banners`)

- **Al abrir la app:**
  - lee `manifest.json` (como mucho cada 6 h);
  - después baja los banners que falten, uno a uno;
  - los guarda en la memoria de la app y **no los vuelve a bajar** salvo que cambien. Si el manifest trae huella (sha256, size…), manda esa; si no, se pregunta a GitHub con ETag (igual = 304, no baja nada).
- **El manifest se lee de forma flexible:** vale una lista de rutas u objetos con `path`, `file`, `url`…
- **Competición → banner:**
  - `futbol-champions-league` para «UEFA Champions League» o «Liga de Campeones»; `combate-ufc` para «UFC 310»…
  - Es prudente: «AFC Champions League», «femenina», «Sub-19», «WTA»… **no** se quedan con el banner de otra.
  - Si no hay uno propio, usa el genérico del deporte (`futbol/futbol.webp`) si existe; si no, el fondo de siempre.
- **Dónde se ve:** portada de Inicio, «Partidos principales», tarjetas «En directo», cabecera de la Agenda y ficha del partido. Los escudos los sigue poniendo la app encima.
- **Memoria:** se decodifica al tamaño en que se pinta (1920 px en la portada; 1280 px como mucho en aparatos justos de memoria).

## Escudos e iconos

- Ahora se guardan en la memoria de la app (antes en la caché que Android puede vaciar). Se descargan una vez y, si ya están, no se piden. Se aprovechan los que ya había.
- Lo que no existe (404) no se vuelve a pedir en 3 días.
- Límite: 1500 ficheros / 80 MB (se borran los menos usados).

**Ajustes › Banners y escudos:**
- «Buscar banners nuevos»;
- «Borrar banners»;
- «Borrar escudos e iconos»;
- el espacio que ocupan.

## Catálogo central de proveedores (rama `providers`, `proveedores.json`)

- **Validación:** se descarga y se valida **antes** de tocar nada.
  - `schema_version` y `providers`, cada uno con `id` y `name` obligatorios;
  - el id es texto;
  - los duplicados se comparan sin mayúsculas;
  - lo que no cumple se ignora y se cuenta.
- **Aplicación:** los cambios se aplican en una transacción. Sin red, se sigue con lo guardado.
- **Primera vez:** se cargan todos (aunque el interruptor esté apagado).
- **Ajustes › Proveedores:**
  - **Actualizar automáticamente al iniciar** (Sí por defecto): añade los nuevos, renombra y quita los de GitHub que ya no están. Los que borraste a mano no se vuelven a añadir solos.
  - **Sincronizar ahora con GitHub:** deja los de GitHub igual que el catálogo y vuelve a traer los que borraste.
  - **Actualizar sin perder mis proveedores:** solo añade y renombra; no quita nada.
  - **Añadir proveedor manualmente**, editar y borrar, como siempre.
  - **Cada proveedor indica su origen:** **GitHub** o **Personal**, y el final del código (`…5dcfd`). El código entero no se enseña nunca.
  - **Última sincronización**, con su resumen.
- **Los personales no se borran nunca.** Si uno tuyo coincide con uno de GitHub, no se duplica: pasa a «GitHub» y manda el nombre central.

## Compilación

- **Pruebas:** pasan las **228 pruebas** JVM. Hay 15 nuevas:
  - emparejado con los 21 banners reales;
  - manifest flexible;
  - caché con ETag y 404;
  - catálogo y sincronización de proveedores con el ejemplo 1001–1003 + 9000.
- **No he podido leer `manifest.json` real** (desde aquí no hay acceso a GitHub). El lector acepta los formatos habituales; si no lo reconoce, la app sigue con su fondo de siempre.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
