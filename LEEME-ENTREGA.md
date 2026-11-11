# MiVisor TV · Entrega 4b: usar la emisión ACTUAL del canal (como Ace Stream oficial)

La 4a está en `docs/entregas/4a.md`. Se mantiene todo.

## Qué se vio (captura de Ace Stream oficial con el mismo ID de ESPN)

1. Ace Stream oficial **no pide la ficha del canal** (`/gettorrent`): ya la tenía guardada.
2. Pregunta al servidor del catálogo `5.252.161.191:8081` (el mismo que nos da a nosotros la ficha):
   `GET /checktorrent?…&checksum=<huella de la ficha>&…`.
3. El servidor responde (respuesta firmada): para esa ficha, la emisión **actual** es `0dca83a7…`.
4. Usa `0dca83a7…`: 11 personas, 4 contestan y se ve.

Nuestro motor calculaba la emisión a partir de la ficha (`fe6ba458…`). En los canales **permanentes** (la ficha trae
el campo `permanent`), cada vez que el emisor reinicia sale una emisión nueva con la misma ficha. La calculada es la
primera, ya vacía. Ace Stream oficial y CanalPeer usan la actual; nosotros no.

## Qué cambia

- **Motor, parche `0007-checktorrent-emision-actual.patch`:** tras obtener la ficha, pregunta a los servidores del
  catálogo por la emisión **actual** (`/checktorrent`) y usa su infohash.
  - Se comprueba la firma de la respuesta y que el número aleatorio coincida, igual que con `/gettorrent`.
  - Si ningún servidor responde bien, se usa la calculada, como antes.
- **El esquema de firma se ha comprobado con la captura real de Ace Stream oficial:** la firma de su petición y la de
  la respuesta del servidor salen idénticas con el mismo cálculo. La firma genérica nueva da lo mismo que la de
  `/gettorrent` (prueba `mivisor_checktorrent_firma_generica_igual_que_gettorrent`).
- **App · Ajustes › Motor › Descriptor del último canal › Emisión actual:**
  - `0dca83a7… (el descriptor daba fe6ba458…, emisión antigua)`;
  - o `la del descriptor (al día)`;
  - o `el catálogo no contestó`.

## Compilación

- **Pruebas JVM:** pasan las **254** (1 nueva: emisión actual).
- **Parche 0007:** comprobado con `rustfmt` que la sintaxis es válida, y que los 7 parches aplican en orden. **No se ha
  podido compilar aquí** (sin acceso a crates.io): lo compila CI.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
- Firma, paquete `com.mivisor.tv` y comprobación SHA-256 de WARP: sin cambios.
