# MiVisor TV · Entrega 3r: lo más cerca del directo que permita la red, probando de 10 en 10 s

La 3q está en `docs/entregas/3q.md`. Se mantiene todo lo de la 3q: ventana del motor de 60 s, nunca salirse de ella y retomar con colchón tras un corte.

## Cómo decide ahora la vuelta al directo

1. **Revisa todo el rato si la descarga va bien.** Va bien si:
   - lo descargado llega hasta casi el directo (le faltan 6 s o menos), o
   - lo descargado crece al menos un 5 % más rápido de lo que se ve.

   Con la red a rachas (3 o más cortes en 5 min) se pide un 25 %.
2. **Si va bien, prueba a acercarse 10 s.** Es un salto dentro de lo ya descargado, sin pantalla negra; si es poco, acelera un pelín.
3. **Espera 30 s a ver qué tal.** Si no hay cortes y sigue yendo bien, otros 10 s, y así **hasta quedarse a ~10 s del directo**. Si la red ha tenido cortes, se queda en el colchón que haya aprendido de ellos.
4. **Si justo después de acercarse hay un corte, la prueba ha salido mal:**
   - el colchón crece;
   - la espera antes de volver a probar se dobla (30 s → 1 min → 2 min → 4 min);
   - cada prueba buena relaja esa espera.
5. **Si la descarga va justa, no prueba nada:** mantiene el retraso y la imagen va fluida.
6. **Los cortes de hace más de 5 min dejan de contar:** cuando la red mejora, el colchón baja solo y vuelven las pruebas.

Ejemplo con la red bien y 70 s de retraso: a los 30 s pasa a 60, luego a 50, 40, 30, 20… y se queda en ~10 s, en unos 4 minutos.

## Compilación

- **Pruebas:** pasan las **242 pruebas** JVM. Hay 3 nuevas:
  - con la red bien se acerca de 10 en 10 s hasta unos 10 s;
  - con la red justa no prueba nada;
  - una prueba que sale mal hace esperar el doble.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
