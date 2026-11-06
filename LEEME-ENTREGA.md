# MiVisor TV · Entrega 3i: Agenda para el mando, «Ahora» en Canales, ficha nueva y más rendimiento

La 3h / 3h.1 está en `docs/entregas/3h.md`. Se mantienen el paquete `com.mivisor.tv`, los datos, la firma y la verificación SHA-256 de WARP. M3U queda congelado (no se ha tocado).

## Diseño (lo de los prototipos)

- **Agenda:**
  - arriba, el partido enfocado en grande: estado, equipos, canal recomendado y cuánto suele tardar en arrancar, sobre los colores de los equipos;
  - días y deportes (con su número) en pastillas;
  - las competiciones visibles **sin rótulo**, las seguidas con ★ (las ocultas en Ajustes no salen);
  - una fila por competición, con el directo primero y luego por hora. Al final de cada fila, «Toda la competición».
  - Los nombres son los reales de la agenda (Champions League, etc.).
  - **OK** abre la ficha; **mantener OK** lo pone ya.
- **Canales:** cada tarjeta dice qué echan **ahora** (AHORA + barrita de progreso) o «A las HH:MM · partido». Nuevo filtro **«Solo con partido ahora»**.
- **Ficha del partido:**
  - escudos a la izquierda y los dos nombres;
  - «Dónde verlo» en tarjetas ordenadas por fiabilidad. La primera dice «Recomendado · arranca en ~N s»;
  - los canales anunciados que no tienes salen aparte;
  - la lista completa de canales sigue debajo.
- **Botones en pastilla en toda la app** (MvButton): el principal marfil y los demás translúcidos con borde.

## Algoritmos y rendimiento

- **Arranque instantáneo:** la biblioteca de canales se guarda en disco (`LibrarySnapshot`) y se enseña nada más abrir, mientras se comprueba si hay cambios. Si no los hay, no se reconstruye.
- **Canal preparado al pasar por encima:**
  - si te quedas 1,5 s sobre un canal, el motor ya le va pidiendo la imagen;
  - guarda 1 canal así (2 si el aparato va sobrado de memoria) durante 60 s como mucho, y nunca toca el que estás viendo;
  - si una fuente tarda más de 5 s en arrancar, se prepara también la siguiente;
  - se puede apagar en Ajustes › Fuentes › «Preparar el canal al pasar por encima».
- **Vuelta al directo según la red:** mide cuánto más rápido descarga de lo que se ve.
  - Con la red justa solo recorta si sigue quedando colchón descargado.
  - Con la red sobrada recorta más (la mitad del sobrante).
- **Compose:** configuración de estabilidad (`compose-stability.conf`) para no recomponer de más, y perfil base (`baseline-prof.txt`) para que arranque y se mueva más fino desde la primera vez.
- **No hecho a propósito, para no dañar:**
  - cambiar el modelo de puntuación de fuentes: rompería las pruebas de paridad;
  - agrupar canales «parecidos»: podría mezclar canales distintos;
  - pruebas de captura o de rendimiento en CI: podrían tumbar la compilación.

## Compilación

- **Pruebas:** pasan las **213 pruebas** JVM, con nuevas de la Agenda por competición, «qué echan ahora», la instantánea, el preparado de canales y la red.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
