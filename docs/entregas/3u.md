# MiVisor TV · Entrega 3u: ver el descriptor del canal

La 3t está en `docs/entregas/3t.md`. Se mantiene todo (P2P completo, trackers HTTP, vías).

## Qué cambia

**Motor (parche `tools/outpace-patches/0003-descriptor-diagnostico.patch`, tras el 0001 y el 0002):** al abrir un canal
escribe en su salida el descriptor que ha recibido:
- qué servidor del catálogo lo dio, o cuáles no lo tenían o no respondieron;
- infohash, si está firmado (directo) y tamaño de pieza;
- título;
- la lista **completa** de trackers, con su esquema (`udp://`, `http://`…), o «ninguno».

**App · Ajustes › Motor:** panel nuevo **«Descriptor del último canal»** con todo lo anterior. Se rellena al abrir un
canal y se queda con el último. También va en el informe de diagnóstico.

## Cómo usarlo

1. Abre el canal que falla y espera a que salga su nombre.
2. Atrás → Ajustes › Motor → «Descriptor del último canal».
3. Haz una foto o manda el informe.

| Ves | Significa |
| --- | --- |
| Trackers: Ninguno | El canal no trae trackers: solo se puede encontrar gente por DHT/PEX. Ace Stream la encuentra por su vía propia (WebRTC) |
| Solo `udp://` y «Gente encontrada» a 0 | Los trackers no conocen a nadie en ese canal |
| Algún `http://` | Ya se usa (3t); mira «Gente encontrada» → `http=` |

## Compilación

- **Pruebas JVM:** pasan las **249** (1 nueva que lee el descriptor de las líneas del motor).
- **Parche 0003:** solo añade líneas de salida, sin lógica nueva. Se ha comprobado que los tres parches aplican en orden
  sobre el commit fijado de Outpace. Se compila en CI.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
- Firma, paquete `com.mivisor.tv` y comprobación SHA-256 de WARP: sin cambios.
