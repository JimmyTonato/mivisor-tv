# MiVisor TV · Entrega 3o: servidores del catálogo de Ace Stream actualizables

La 3n está en `docs/entregas/3n.md`. Se mantienen el paquete, los datos, la firma y la verificación de WARP.

## El problema

Outpace convierte cada ID en su descriptor preguntando al catálogo de Ace Stream. Lo hacía solo a **4 IPs fijas en su código**, copiadas del motor oficial 3.2.11 en julio. Ace Stream ya usa otras (por ejemplo `51.158.152.5:8081`). Resultado: hay IDs que en Ace Stream o CanalPeer van y en MiVisor dan «0 pares», con WARP o sin ella.

## Lo que cambia

- **Parche de Outpace** (`tools/outpace-patches/0001-catalog-hosts-env.patch`):
  - lee la variable `OUTPACE_CATALOG_HOSTS` (`ip:puerto,…`, solo IPv4 públicas, máximo 16) y prueba esos servidores primero;
  - los 4 de serie quedan detrás, como respaldo;
  - GitHub Actions aplica el parche al compilar el commit fijado de siempre y pasa su prueba antes de compilar. Si el parche no encaja, la compilación se para.
  - **Licencia:** el código modificado está en el repo y el aviso de la licencia AGPL está en `assets/licenses/outpace-MODIFICACIONES.txt`.
- **Lista en mivisor-datos, rama `catalogo`** (`catalog-hosts.json`):
  - la app la descarga al abrirse (como mucho cada 6 h, con ETag), la valida y la guarda;
  - el motor la usa al arrancar;
  - sin red, o si el fichero está roto, sigue con la última lista buena;
  - mientras no exista la rama, se usa la de serie de MiVisor: `51.158.152.5:8081`.
- **Ajustes › Motor:**
  - «Servidores del catálogo»: la lista en uso, de qué fecha es y el último aviso de catálogo del motor. OK para buscar una lista nueva;
  - botón «Reiniciar motor», para aplicar una lista nueva al momento;
  - en Diagnóstico › Motor, una línea de RESOLUCIÓN dice qué servidores se pasan al motor.

## Primer commit de la rama `catalogo`

Va aparte, en `MiVisor-datos-rama-catalogo.zip`: la lista, el README y dos scripts (comprobar los servidores y descubrir los que usa el motor oficial).

## Compilación

- **Pruebas JVM:** pasan las **236 pruebas** (nuevas: la lista y el paso al motor).
- **Parche de Outpace:** aquí solo se ha probado la función que lee la lista (aislada, con rustc). Encaja en el commit fijado, pero no se ha compilado Outpace entero porque desde aquí no hay acceso a crates.io. Lo compila y lo prueba GitHub Actions.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato.
