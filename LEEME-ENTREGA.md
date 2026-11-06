# MiVisor TV · Entrega 3h.1: animaciones en la tele, carga sencilla, Inicio sin tirones y verificador

Corrige la 3h, que va justo debajo.

- **Animaciones que no se movían en la tele (carga e intro).**
  - **Por qué:** muchas teles traen las animaciones del sistema desactivadas y Compose las congela. Además, el modo ligero apagaba el giro.
  - **Ahora:** los indicadores giran con el **reloj de pantalla** (`rememberLoop`), también en modo ligero. Solo se redibujan, sin recalcular la pantalla, así que apenas cuestan.
- **Carga más sencilla:** fuera «Motor listo · Buscando emisores · Preparando la imagen». Queda un solo mensaje: «Buscando señal…», «Conectando…» y, si tarda, «Buscando señal · 34 s (otra fuente a los 60 s)».
- **Tirón en Inicio entre «Partidos principales» y «En emisión y a continuación».**
  - **Por qué:** la lista era perezosa. Al bajar construía de golpe toda la fila de tarjetas, con sus degradados y escudos; al subir reconstruía la portada.
  - **Ahora:** Inicio es una columna normal. Todo se construye una vez y moverse solo desplaza, con pivote.
- **Puntuación de fuentes.** Ya existía desde la fase 3:
  - **Nota de cada fuente:** cuántas veces arranca, lo que tarda, los cortes y cuándo funcionó por última vez.
  - **La última que funcionó en cada canal se prueba primero.** Si en un canal siempre va la 4.ª, se empieza por la 4.ª; si deja de ir, se pasa a la siguiente mejor.
- **Verificador (Ajustes › Verificador › Comprobar ahora):**
  - **Fuentes sin imagen:** ahora **también apunta las que no dan imagen**. Les baja la nota, sin pausa ni cuarentena por una sola prueba.
  - **Fallos de casa:** si el fallo es de la red, de WARP o del motor, no se apunta.
  - **Orden:** comprueba primero favoritos, luego lo último visto y después el resto, hasta 80 fuentes.
  - **Pruebas nuevas:** `ProbeTest`.

**Pruebas:** pasan las **205 pruebas** JVM. **Android:** no se ha compilado aquí; se comprueba en GitHub Actions.

---

# Entrega 3h: reproductor nuevo, vuelta al directo adaptativa e Inicio más fluido

La 3g está en `docs/entregas/3g.md`. Se conservan los datos y el paquete `com.mivisor.tv`.

## 1. Cortes y vuelta al directo (automático, sin ajustes)

El código está en `core/engine/CatchUp.kt` y sus pruebas en `CatchUpTest` (simulación segundo a segundo).

- **Un corte no te mueve.** La imagen sigue desde donde se paró. Antes, a los 8 s de corte saltaba al directo; ahora vuelve a pedir la imagen **desde donde estabas**.
- **La app mide todo el rato** cuánto lleva sin cortes y cuánta imagen tiene ya descargada por delante.
- **Solo se acerca al directo cuando ve la oportunidad:** al menos 30 s sin cortes, más 10 s por cada corte de los últimos 5 minutos, como mucho 90 s.
- **Recorta en proporción:** un tercio del retraso sobrante, o la mitad si lleva más de 3 minutos estable. Entre 2 y 30 s cada vez.
- **Deja siempre un colchón aprendido:** el doble de lo que duran sus cortes, y ×1,5 si hay cortes seguidos. Si los cortes son de 6 s, no baja de unos 12 s.
- **Un corte suspende la vuelta al momento** y exige más estabilidad antes de seguir.
- **Cómo adelanta:**
  - recortes de hasta 8 s, acelerando a ×1,1 (no se nota);
  - más grandes, saltando **dentro de lo ya descargado**, sin pantalla negra;
  - si no hay imagen descargada, no hace nada.
- **Botones en el reproductor:** «Ir al directo» y, si hay imagen descargada para ello, «Adelantar 15 s».
- **Ajustes:** «Acercarse solo al directo» sigue ahí por si quieres desactivarlo. Viene activado.

## 2. Reproductor más profesional

- **Controles:**
  - degradados suaves arriba y abajo, sin cajas;
  - el **logo del canal grande** (más alto que el texto), con el nombre y el partido;
  - la fuente en una pastilla y la hora;
  - la línea del directo: «EN DIRECTO» o «DIRECTO · −32 s», con la barra «Tú → Directo» y lo descargado;
  - botones en pastillas: pausa, Ir al directo, Adelantar 15 s, Fuentes, Audio, Subtítulos, Vídeo, Info y Mi lista.
- **Carga:**
  - fuera el spinner;
  - el logo grande en el centro con el canal y el partido;
  - una barra fina animada y las fases en texto (Motor ✓ · Emisores · Imagen);
  - «Seguir esperando / Probar otra fuente» abajo, solo si tarda.
- **Corte:** una tarjeta discreta abajo a la derecha. Dice «Recuperando la imagen · Sigues donde estabas · no te pierdes nada» y enseña el retraso acumulado.

## 3. Inicio

- **Quitados:** «Continuar viendo» y «Próximas citas».
- **Partidos principales:** solo salen partidos **con canal en tus proveedores**.
  - **OK abre su ficha** (quién juega, dónde verlo y todos los canales), como en la Agenda.
  - **Mantener OK** lo reproduce directamente.
- **Se mantienen** Favoritos, Competiciones y Agenda próxima.

## 4. Fluidez con el mando

- **Desplazamiento con pivote en Inicio**, como Google TV: la fila enfocada se queda siempre a la misma altura y es la lista la que se mueve. Eran los «saltos raros» al ir rápido.
- **Flecha mantenida:** como mucho unas 12 repeticiones por segundo. Antes llegaban 20-30, se encolaban y la lista avanzaba a tirones. La primera pulsación y el soltar no se tocan.

## Compilación

- **Pruebas:** pasan las **203 pruebas** JVM, con 6 nuevas de la vuelta al directo.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
