# MiVisor TV · Entrega 3l: banners enteros y «En línea» en Canales

La 3k (con la 3k.1 y la 3k.2) está en `docs/entregas/3k.md`. Se mantienen el paquete, los datos, la firma y la verificación de WARP.

## Banners sin zoom y con mejor calidad

- **Antes:** en la ficha del partido, en la cabecera de la Agenda y en la portada grande, el banner se estiraba a todo el ancho. Eso hacía zoom: el logo salía enorme, cortado («Premier Lea…») y algo borroso.
- **Ahora:**
  - el banner se ve **entero (16:9)**, a la altura de la cabecera;
  - va pegado a la derecha y fundido hacia la izquierda, donde va el texto;
  - el logo se ve completo, igual en tele y en tablet;
  - en la portada grande, los escudos van en el hueco libre del banner.
- **Calidad:** solo se baja la resolución en aparatos con muy poca memoria. Antes se hacía en muchas teles y por eso se veían borrosos.
- **Sin cambios:** las tarjetas de «Partidos principales» y «En directo» siguen como estaban.

## «En línea» en Canales

- **Dónde está:** junto a Proveedores · Categorías · Favoritos, la pastilla verde **«En línea · N»**. Filtra lo que estés viendo (un proveedor, sus grupos, una categoría o favoritos); las pastillas de proveedor muestran cuántos tienen en línea.
- **Qué es un canal en línea:** alguna de sus fuentes **dio imagen en las últimas 12 horas** (al verlo o con el verificador) y:
  - no ha fallado después;
  - no está en pausa ni en cuarentena;
  - su nota es buena.

  Si lo abres, lo normal es que salga imagen enseguida.
- **Actualización:** la lista se recalcula al entrar y cada minuto. Para llenarla: Ajustes › Verificador, por proveedor.

## Compilación

- **Pruebas:** pasan las **229 pruebas** JVM (nueva: qué cuenta como «en línea»). Todas las importaciones del proyecto existen.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
