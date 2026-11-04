# MiVisor TV · Entrega 3d (diagnóstico, copias y fidelidad al diseño)

La 3d va encima de la 3c y la sustituye entera. No hay que configurar nada nuevo.

Se conserva todo lo anterior:
- reproducción, Outpace, WARP, Ace Stream, proveedores, favoritos, historial, agenda, logos, «Dónde verlo», competiciones y recordatorios;
- el paquete `com.mivisor.tv`;
- la firma y la comprobación SHA-256 de WARP. No se ha generado ninguna clave.

La base de datos no cambia de versión: instalar encima de la 3c conserva todo. Lo de las entregas anteriores está en `docs/entregas/`.

## 1. Lo nuevo de la 3d

### Diagnóstico e informes

Se entra desde **Ajustes › Diagnóstico › ¿Algo no va bien?** y desde los avisos del reproductor.

- **Lo último que ha pasado:** una tarjeta con la última incidencia al reproducir («Hace 4 min · Fuente…»), o «Sin incidencias recientes».
- **Últimos avisos:** de todos los servicios, con su hora y un punto de color.
- **Enviar el informe**, con tres pestañas:
  - **Red de casa:** un QR abre el informe en el móvil por la red local. Caduca en 5 minutos.
  - **Sin red:** el informe partido en varios QR («parte 1/4…»). Se escanean con la cámara, sin red.
  - **Compartir:** con otra app de la tele, si hay alguna. Si no hay ninguna, avisa.
- **Informe en la tele:** el mismo texto que se envía, en letra de máquina. Incluye:
  - versión, aparato, red, motor, Ace Stream y WARP;
  - proveedores (sin códigos), agenda, fuentes que descansan, memoria y espacio;
  - las últimas 40 líneas del registro.
- **Privacidad:** el informe pasa por el limpiador del registro, así que no lleva códigos, Content IDs completos, URLs privadas ni IPs públicas.

### «Probar todo»

Comprueba, en este orden y a la vista:
1. Motor Outpace: lo arranca de verdad y mide lo que tarda.
2. Red.
3. WARP, con la huella.
4. Ace Stream.
5. Proveedores.
6. Agenda (GitHub).
7. Servidor del móvil.

Cada resultado sale como «Bien», «Revisar», «Error» u «Opcional». Hay botones «Repetir» y «Enviar informe». El resumen queda en el registro.

### Sistema de QR ampliado

El mismo servidor local de «Escribir con el móvil» atiende ahora cinco usos. Funciona solo en la red de casa, con un token de un solo uso, y caduca en 5 minutos:

| Uso | Ruta en el móvil |
|---|---|
| Escribir un texto (códigos, nombres, búsquedas) | `/pair/…` |
| Editar título y código de un proveedor | `/edit/…` |
| Ver el informe | `/log/…` |
| Descargar una copia (.mvb) | `/copy/…` |
| Subir una copia y su código | `/restore/…`: como mucho 512 KB y un solo envío |

«Añadir proveedor» y el asistente muestran ahora el QR siempre a la derecha, como en el diseño. Cada texto recibido abre otra sesión para poder enviar más.

### Copias de seguridad cifradas

- **Exportar** (Ajustes › Móvil y copias):
  - Guarda los proveedores con su código, los favoritos, los recordatorios y los ajustes.
  - La copia se cifra con **AES-256-GCM**. La clave sale de un **código de 6 cifras** nuevo en cada copia (PBKDF2, 120 000 vueltas, sal aleatoria).
  - El móvil descarga el archivo `.mvb` con el QR.
  - El código se ve **solo en la pantalla**: no se guarda en ningún sitio ni viaja con el archivo.
- **Importar** (Ajustes › Móvil y copias, o «Importar copia» en el primer paso del asistente):
  1. El móvil sube el archivo y escribe el código.
  2. La tele **comprueba la copia antes de cambiar nada** y muestra «Copia comprobada · 3 proveedores, 5 favoritos…». Si el código no es correcto, o el archivo no es una copia, lo dice y genera otro QR.
  3. «Importar ahora» **no borra nada**:
     - añade los proveedores que falten, comprobando cada código como al añadirlo a mano;
     - junta favoritos y recordatorios;
     - aplica los ajustes.
- **Lo que no lleva:** lo aprendido de las fuentes ni el historial. Es propio de cada tele.

### Ajustes y opciones pendientes

Ajustes sigue el diseño: menú a la izquierda con icono y valor (Proveedores · 3, Motor · Outpace, WARP · Conectada, Verificador · No) y la página a la derecha con su explicación.

- **Audio y subtítulos** (nuevo):
  - idioma de audio preferido;
  - subtítulos sí o no;
  - tamaño de los subtítulos (normal, grande o muy grande);
  - «Recordar la elección en cada canal».

  Se aplican al abrir el siguiente canal.
- **Fuentes y recuperación:**
  - las reglas de pausa y cuarentena;
  - el acceso a **Fuentes que descansan**, con «Liberar» y «Liberar todas»;
  - «Olvidar lo aprendido».
- **Verificador:**
  - «Comprobar ahora» hace una pasada **manual** con la app abierta. Prueba hasta 60 fuentes de tus favoritos o de todos los canales, y **solo apunta las que funcionan**: nunca penaliza.
  - El automático sigue **desactivado**, como acordamos.
- **Pantalla y rendimiento:**
  - menú **Arriba** o **En un lateral**, que se despliega al enfocarlo;
  - Modo ligero;
  - **Texto más grande** (+15 %);
  - **Al abrir MiVisor**: Inicio, Agenda, Canales o el último canal;
  - información de la fuente.
- **WARP:** si la firma no coincide, sale la tarjeta roja del diseño con «Huella esperada» y «Huella de esta app», y los botones «Abrir WARP» y «Ver diagnóstico».
- **Motor:** botones de opción del diseño y el bloque «ESTADO».
- **Acerca de:** licencias y «Repetir la primera configuración», que no borra nada.
- **Agenda, Diagnóstico y Móvil y copias** tienen su página. El registro completo está dentro de Diagnóstico.

## 2. Correcciones visuales, pantalla por pantalla

La interfaz de TV mide 960 × 540 dp; las pantallas del prototipo, 1920 × 1080 px. Las medidas se han pasado a la mitad para que coincidan.

| Pantalla | Qué se ha corregido |
|---|---|
| **Icono y banner** | Icono definitivo del diseño: cuadrado redondeado con degradado azul `#3D8BFF → #7CC4FF` y la «M» oscura `#061226` en Inter Black. Va en todas las densidades y como icono adaptativo (fondo en degradado y primer plano). Banner de Android TV con el icono y «MiVisor TV» sobre `#07090D` |
| **Introducción (asistente)** | Cinco pasos con barra de progreso:<br>1. Bienvenida, con «Empezar» e «Importar copia».<br>2. Proveedores, con el QR del móvil siempre visible.<br>3. Comprobación rápida, con la fila del aparato («Modesto» si tiene poca memoria).<br>4. Estilo del menú, con tarjetas Arriba / En un lateral y Modo ligero.<br>5. Todo listo.<br><br>«Atrás» vuelve al paso anterior |
| **Inicio** | Fondo con los colores de los dos equipos del destacado.<br>Destacado con escudos grandes, título en dos líneas, «canal · lista, arranca en ~N s» y los botones Ver ahora / Dónde verlo / Favorito.<br>Filas: En directo / Empieza pronto, Seguir viendo («por donde lo dejaste»), Esta noche (recordatorios), Tus favoritos y grupos.<br>Avisos con el estilo del diseño |
| **Menú** | Barra superior o menú lateral desplegable (opción nueva). Insignia «M» con los colores del icono |
| **Reproductor** | **Arriba:** logo del canal, nombre, **evento que se emite ahora** (de la agenda), EN DIRECTO / EN PAUSA, **«Retraso N s»** (el retraso real que mide el motor) y la hora.<br>**Abajo:**<br>• datos de la fuente y una barra hasta «Directo»;<br>• **pausa redonda**, Audio y subtítulos, «Fuente x de y», **Canales**, Canal anterior, **corazón redondo** y ajuste de imagen;<br>• el audio activo («Español · subtítulos no»).<br>**Paneles laterales:**<br>• *Audio y subtítulos* con botones de opción, códec y canales de cada pista («E-AC-3 · 5.1»); las pistas que el aparato no puede reproducir salen apagadas;<br>• sección SUBTÍTULOS y «Se recuerda tu elección en este canal»;<br>• *Fuentes*;<br>• *Canales*, nuevo: los canales del grupo con su logo, el evento que emiten y la etiqueta «Ahora».<br>**Zapping:** tarjeta abajo a la izquierda con logo, nombre y «DAZN · 3 de 6».<br>**Avisos de fallo como en el diseño:**<br>• «El motor no ha arrancado» → Reintentar / Probar con Ace Stream / Ver diagnóstico;<br>• «X no tiene fuentes disponibles» → Probar igualmente / Ver cuarentena / Cancelar.<br>**Otros:** el tamaño de subtítulos se aplica al vídeo. Al abrir la app directamente en el último canal, espera a que carguen los proveedores |
| **Canales (grupo)** | Cabecera con el rótulo del grupo, nombre y «6 canales · 5 listos».<br>Rejilla de tres columnas.<br>Panel del canal enfocado con su logo, el evento EN DIRECTO y su estado |
| **Agenda** | Días arriba (HOY 30, SÁB 31…) y filtros Todo / Fútbol / Tenis…<br>Filas «equipo contra equipo» con escudos, la pastilla DIRECTO o la hora, y la competición con el mejor canal.<br>Panel **DÓNDE VERLO** a la derecha para el evento enfocado: tus canales del mejor al peor, con «Mejor», estado y «OK: ficha del evento · con/sin recordatorio».<br>Las competiciones de la 3c siguen, al final de la lista |
| **Ficha del evento («Dónde verlo»)** | Cabecera a todo el ancho sobre los colores de los equipos: EN DIRECTO + COMPETICIÓN, título grande, «Ver en <canal>», Recordarme, la competición y los escudos con «vs» a la derecha.<br>«Dónde verlo» como fila de tarjetas de tus canales («Mejor opción» la primera).<br>Debajo, la lista completa de canales anunciados de la 3c, también los que no están en tus proveedores |
| **Mi lista** | Pestañas del diseño, sin contadores.<br>**Ordenar** (como los añadiste, A-Z o los que mejor funcionan).<br>Rejilla de 4.<br>Historial con logo, «Hoy, 21:02 · 32 min» y «Ver».<br>Estado vacío con icono |
| **Buscar** | Teclado en pantalla a la izquierda (a–z, ñ, +, Espacio, Borrar, Móvil) y resultados a la derecha (Eventos y Canales). No abre el teclado del sistema.<br>«Nada para «x»» con sugerencias. Mantener OK en «Borrar» vacía la búsqueda |
| **Ajustes** | Ver el apartado 1. Las filas pasan a blanco con texto oscuro al enfocarlas, como en el diseño. Esto se aplica a todas las listas de la app |
| **Componentes** | Interruptor verde al encenderse, botón de opción, insignias, avisos, estado vacío con icono, botón principal blanco con anillo azul claro. Nuevos iconos del menú de Ajustes: altavoz, ajustes, pulso, check, caja y chip |
| **Foco y transiciones** | Anillo y escala de foco del diseño, fundido de 180 ms entre pantallas (sin él en Modo ligero) y la zona segura de 48 × 27 dp |

## 3. Elementos del diseño que siguen pendientes

| Elemento | Por qué no está |
|---|---|
| **Vista previa en vídeo** en Canales y en el grupo («Sin sonido») | Hace falta un segundo reproductor a la vez, y eso cambia la reproducción. En el panel del grupo se ve el logo y el estado en su lugar. El interruptor sale como «Todavía no disponible» |
| **Marcador y minuto** («1–0 · 50'», «2.º set») en Agenda, Inicio y ficha | `agenda.json` no los trae. Se muestra DIRECTO o la hora |
| **Jornada** («LALIGA · JORNADA 11») | `agenda.json` no la trae |
| **Barra de tiempo con retroceso** en el reproductor | No hay *timeshift*. La barra indica el directo y el retraso real |
| **Ajustes de tiempos** (retraso objetivo, «reintentar a los 15 s», «cambiar a los 30 s») | Cambiarlos tocaría la lógica de reproducción y Outpace, que pediste no alterar. Se muestran como «Automático» o «Fijo» |
| **Verificador automático** | Desactivado, como acordamos. Solo existe «Comprobar ahora» |
| **Código corto del informe** («MV-7K4Q») | El QR ya lleva un token seguro. Se muestra la hora de caducidad en su lugar |
| **Ace Stream dentro de MiVisor TV** | Más adelante, como en la 3b y la 3c |
| **«Escribir con el móvil» como ajuste propio** | Aparece junto a cada campo. Desde Ajustes lleva a «Añadir proveedor» |

## 4. Limitaciones técnicas

- **Sin trabajo en segundo plano**, como siempre: el verificador manual, los avisos y el servidor del móvil solo funcionan con la app abierta.
- **Las copias van por el móvil:** exportar e importar necesitan que la tele esté en la red de casa (Wi-Fi o cable). Sin esa red, la pantalla lo dice.
- **El archivo `.mvb` sin su código no sirve.** Si se pierde el código, hay que hacer otra copia.
- **«Compartir» del informe** depende de que la tele tenga alguna app que reciba texto. En la mayoría de teles no hay ninguna, y lo avisa.
- **«Comprobar ahora»** para el reproductor mientras prueba: cada fuente usa el motor. Como mucho prueba 60 fuentes y se para con OK.
- **El icono adaptativo** se usa en Android 8 o posterior. En Android 6 y 7 se ve el icono redondeado normal.

## 5. Comprobaciones en la tele

| # | Comprobación |
|---|---|
| 1 | El icono y el banner del lanzador son los del diseño. Si el lanzador guarda el antiguo, reinicia la tele |
| 2 | Primera configuración (Ajustes › Acerca de › Repetir la primera configuración): se pueden recorrer los 5 pasos con el mando y «Atrás» vuelve al anterior |
| 3 | Reproductor: con un evento en directo en la agenda, arriba sale su título y «Retraso N s». Abajo funcionan pausa, Audio y subtítulos, Fuente x de y, Canales, Canal anterior y el corazón |
| 4 | Panel de audio: las pistas muestran el códec; elegir una la recuerda la próxima vez en ese canal |
| 5 | ▲▼ con los controles ocultos cambian de canal y sale la tarjeta abajo a la izquierda |
| 6 | Ajustes › Diagnóstico › Probar todo termina con los 7 resultados |
| 7 | ¿Algo no va bien? › Red de casa: el QR abre el informe en el móvil. Sin red: los QR se leen con la cámara |
| 8 | Exportar copia: el móvil descarga el `.mvb` y la tele muestra el código de 6 cifras |
| 9 | Importar copia en otra tele (o en esta): con un código incorrecto avisa y no cambia nada. Con el correcto muestra «Copia comprobada» y, al importar, se conserva lo que había |
| 10 | Ajustes › Pantalla: menú en un lateral, Texto más grande y «Al abrir MiVisor» cambian la app al momento o al volver a abrirla |
| 11 | Agenda: los días y los filtros cambian la lista. Al moverse por los eventos se actualiza «DÓNDE VERLO» |
| 12 | Buscar con el teclado en pantalla y con «Móvil» |
| 13 | Fuentes que descansan: «Liberar» devuelve la fuente a la lista |
| 14 | Con la firma de desarrollo, Ajustes › WARP muestra la tarjeta roja con las dos huellas |
| 15 | Instalada encima de la 3c se conservan proveedores, favoritos, historial, recordatorios y lo aprendido |

## Compilación

- **Pruebas:** pasan las **174 pruebas** de los módulos JVM. Son nuevas las de copias cifradas y las de la descarga y subida de copias por el servidor del móvil.
- **Código Android:** lleva las mismas revisiones de imports y referencias que en las entregas anteriores, pero **no se ha compilado en este entorno ni se ha probado en una tele**. Se comprobará en GitHub Actions; si falla, pasadme el log.
- **Fuera de este proyecto no se ha tocado nada:** ni la Raspberry, ni MiVisor Web, ni MiVisor Agenda, ni `mivisor-datos`, ni la v18, ni Outpace en producción.
