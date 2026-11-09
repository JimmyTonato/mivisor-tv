# MiVisor TV · Entrega 3t: trackers HTTP y diagnóstico de por dónde llega la gente

La 3s está en `docs/entregas/3s.md`. Se mantiene todo, incluido «Aceptar conexiones de otros (P2P completo)».

## Por qué

El descriptor de cada canal trae la lista de **trackers** (servidores que dicen quién lo está viendo). Outpace solo
usaba los `udp://` y tiraba los `http://`. En directo, Ace Stream oficial no usa la DHT: encuentra a la gente por esos
trackers. Con un descriptor de trackers HTTP, nosotros nos quedábamos sin nadie.

## Qué cambia

**Motor (parche `tools/outpace-patches/0002-http-trackers-diagnostico.patch`, se aplica en CI tras el 0001):**
- Anuncia también en los trackers `http://` y `https://` del descriptor:
  - pide respuesta corta (compacta) y entiende también el formato largo;
  - solo IPv4;
  - mismas protecciones de red que el resto (sin redirecciones, 5 s y 64 KB como mucho por tracker).
- Escribe en su salida:
  - qué trackers trae el descriptor, cuántos UDP y cuántos HTTP;
  - cuánta gente encuentra por trackers UDP, por trackers HTTP y por DHT.

**App:**
- **Ajustes › Motor** enseña dos datos nuevos mientras ves un canal: «Trackers del canal» y «Gente encontrada»
  (trackers UDP/HTTP y DHT). Se guardan siempre, aunque el registro detallado esté apagado.
- El informe de diagnóstico incluye esas dos líneas.

## Cómo leerlo con el canal que fallaba

| Ves | Significa |
| --- | --- |
| `http=1` o más en los trackers y gente por `http` | Era eso: ahora llega por los trackers HTTP |
| Trackers solo UDP y 0 en todo | Nadie anunciado: el canal no tiene gente a la vista, o va por otra vía (WebRTC) |
| Gente encontrada > 0 pero 0 emisores | Los encontramos pero no podemos conectar: P2P completo sin WARP (3s) |
| Sin trackers en el descriptor | Solo DHT/PEX; Ace Stream usaría otra vía |

## Compilación

- **Pruebas JVM:** pasan las **248** (2 nuevas sobre las líneas de diagnóstico).
- **Parche del motor:**
  - 5 pruebas nuevas del parche (`mivisor_http_tracker_*`): URL de anuncio, respuestas compacta y en lista, errores
    y resumen de trackers. Las funciones puras se han compilado y probado aquí con rustc.
  - El motor completo no se ha compilado aquí (sin acceso a crates.io). En CI se aplican los dos parches, se pasan sus
    pruebas (`cargo test ... mivisor_`) y se compila.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
- Firma, paquete `com.mivisor.tv` y comprobación SHA-256 de WARP: sin cambios.
