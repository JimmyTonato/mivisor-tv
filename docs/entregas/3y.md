# MiVisor TV · Entrega 3y: ver por qué no conecta con la gente que encuentra

La 3x está en `docs/entregas/3x.md`. Se mantiene todo.

## Qué se vio (ESPN HD [US])

- El descriptor trae 3 trackers oficiales de Ace Stream (`t1/t2/t3.torrentstream.org:2710`) y se usan bien.
- «Gente encontrada»: 3 por trackers y 1 por DHT. Es decir, **sí encuentra gente, pero no consigue sacar vídeo de
  nadie**.
- En el log antiguo pasaba lo mismo: casi todos «no contestan» a los 3 s y los que contestan cuelgan al saludar.

## Qué cambia

- **Ajustes › Motor › Conexiones** (y el informe): cómo acabaron los intentos de conectar con la gente encontrada
  desde que se abrió el canal. Por ejemplo `0 conectados · 3 no contestan · 1 cuelgan al saludar`.
  - **no contestan / tardan demasiado:** detrás de un router o ya no están. Lo que ayudaría es el P2P completo, que
    les permite conectar con nosotros.
  - **cuelgan al saludar:** contestan pero cierran en cuanto les hablamos; no nos aceptan.
  - **conectan pero no envían:** aceptan, pero no mandan nada.
- **Motor, parche `0005-conexion-6s.patch`:** 6 s en vez de 3 s para conectar con cada persona. Con canales de poca
  gente, lejos (ESPN US) o por redes móviles, 3 s cortaba conexiones que sí iban a abrirse.

## Compilación

- **Pruebas JVM:** pasan las **251** (1 nueva: recuento de intentos).
- **Parches:** los cinco aplican en orden sobre el commit fijado. El 0005 solo cambia una constante. Se compilan en CI.
- **Código Android:** no se ha compilado aquí ni se ha probado en ningún aparato. Se comprueba en GitHub Actions.
- Firma, paquete `com.mivisor.tv` y comprobación SHA-256 de WARP: sin cambios.
