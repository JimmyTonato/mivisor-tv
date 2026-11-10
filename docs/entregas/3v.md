# MiVisor TV · Entrega 3v: el motor usaba 0 de los 21 trackers del canal

La 3u está en `docs/entregas/3u.md`. Se mantiene todo.

## Qué se vio con el panel de la 3u (M+ Golf 1080)

- El descriptor trae **21 trackers** (18 UDP y 3 HTTP), pero «Trackers del canal» decía **1** y «Gente encontrada»
  `udp=0 http=0 (de 1 trackers)`.
- Causa: el descriptor trae **todos los trackers en una sola cadena**, separados por espacios. Outpace la tomaba como
  una única URL de cientos de caracteres y la descartaba por larga (máx. 256).
- Resultado: el canal se quedaba **sin ningún tracker**. Solo encontraba gente por DHT (25), que en directo suele ser
  gente vieja o inalcanzable. Ace Stream oficial sí usa los 21 trackers, y por eso se ve.

## Qué cambia

- **Motor:** parche `tools/outpace-patches/0004-trackers-en-una-cadena.patch` (sobre el 0001–0003). Al leer el
  descriptor separa los trackers por espacios, comas y saltos de línea; sin repetidos, máx. 64. Vale también cuando
  `trackers` viene como cadena suelta en vez de lista.
- **CI:** se pasan también las pruebas del parche en `ace-wire` (`cargo test -p ace-wire --lib mivisor_`).

## Cómo comprobarlo

En Ajustes › Motor, con el mismo canal:
- «Trackers del canal» tiene que decir **21 (udp=18 http=3 …)**;
- «Gente encontrada» tiene que dar números en `udp=` y/o `http=`.

## Compilación

- **Parche 0004:** su prueba (`mivisor_trackers_en_una_sola_cadena`) se ha compilado y pasado aquí con rustc. Se ha
  comprobado que los cuatro parches aplican en orden sobre el commit fijado. El motor completo se compila en CI.
- **Pruebas JVM:** siguen pasando las 249.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
- Firma, paquete `com.mivisor.tv` y comprobación SHA-256 de WARP: sin cambios.
