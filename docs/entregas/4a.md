# MiVisor TV · Entrega 4a: con el mismo ID, usar la emisión que tiene gente

La 3z está en `docs/entregas/3z.md`. Se mantiene todo.

## Qué se vio (mismo ID de ESPN en CanalPeer y en MiVisor)

| | CanalPeer | MiVisor |
| --- | --- | --- |
| De dónde saca el descriptor | Su propio servidor | Servidor del catálogo `5.252.161.191:8081` |
| Emisión (infohash) | `0dca83a7…` | `fe6ba458…` |
| Gente en el tracker | 11 (4 contestan) | 3–4 (ninguna contesta) |

Con el **mismo content-id**, el descriptor que nos dio ese servidor del catálogo es de **otra emisión**: seguramente la
anterior, de antes de que el emisor reiniciara, con gente que ya no está. La red, el motor y el saludo están bien; el
problema era qué emisión nos daban.

## Qué cambia

**Motor, parche `0006-catalogo-elige-emision-viva.patch`:**
- Pregunta a **todos** los servidores del catálogo **a la vez**. Tras el primer descriptor, espera 1,5 s más a los
  demás.
- Si dan **emisiones distintas**, pregunta a los trackers de cada una cuánta gente tiene (3 s como mucho) y usa la que
  más tenga. Si empatan, la del servidor que va antes en la lista.
- Lo escribe todo en el diagnóstico.

**App · Ajustes › Motor › Descriptor del último canal:**
- **Emisiones:** qué emisión dio cada servidor y con cuánta gente, por ejemplo
  `5.252.161.191:8081 → fe6ba458 (3 personas)` y `51.158.152.5:8081 → 0dca83a7 (10 personas)`.
- **Sin descriptor:** los servidores que fallaron. Ya no se borran cuando otro acierta.

## Si ningún servidor del catálogo tiene la emisión nueva

Saldrá en «Emisiones» con todos dando la misma. Entonces la emisión viva solo la tiene el servidor propio de CanalPeer.
No se usa, y lo práctico sería añadir el ID de la emisión nueva a la lista de fuentes.

## Compilación

- **Pruebas JVM:** pasan las **253** (1 nueva: emisiones por servidor; 1 ajustada: los fallos ya no se borran).
- **Parche 0006:** aplica en orden tras el 0001–0005. **No se ha podido compilar aquí** (sin acceso a crates.io): lo
  compila CI. Usa solo funciones que ya existen en Outpace (consulta al catálogo, lectura del descriptor y anuncio a
  trackers).
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
- Firma, paquete `com.mivisor.tv` y comprobación SHA-256 de WARP: sin cambios.
