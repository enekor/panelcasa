# Estado del proyecto

Última actualización: 19/11/2026.

| Fase | Estado |
| --- | --- |
| 0 — Reconocimiento | Hecha la parte sin tablet. Pendiente: medir en la tablet |
| 1 — Renfe | Hecha. Pendiente: comparación manual con la web de Renfe |
| 2 — Meteo | Hecha |
| 3 — Panel web | Hecha. Pendiente: confirmación visual en la tablet |
| 4 — App launcher | Hecha en el contenedor. Pendiente: verificación en la tablet |
| 5 — Google Home | Aplazada por decisión de Eneko. La columna está oculta en todas las versiones (`VersionGate.HOME_MODULE_READY = false`) |
| 6 — Robustez y entrega | Pendiente: necesita la tablet |

## Cambios tras la primera prueba en un móvil (19/11/2026)

- **Descarga doble del horario corregida.** Al abrir el buscador encima del panel se pausaban los ciclos y la descarga en curso se abandonaba a medias mientras el buscador lanzaba otra sobre el mismo fichero temporal. Ahora la comprobación del horario no se pausa nunca (solo tiempo real, avisos y meteo) y el buscador espera a la descarga en curso.
- **Progreso visible:** «Descargando el horario de Renfe: 6,2 de 15,0 MB…» y «Procesando el horario…», en el buscador y en el panel.
- **Reintento rápido sin datos:** si todavía no hay horario, reintenta a los 30 s, 1 min, 2 min… hasta 1 h.
- **Estación retirada por Renfe:** el horario nuevo se acepta igualmente y el panel pide elegir otra estación, en lugar de descartar cada día el horario nuevo.
- **Ajustes muestra el estado de los datos:** versión del horario, última comprobación, último tiempo real y última meteo.
- **Diagnóstico:**
  - Botón «Compartir informe» con el menú de compartir de Android.
  - Prueba del motor web medida dentro del WebView (CSS Grid, fetch, async/await, line-clamp, tamaño de pantalla en px CSS).
  - Memoria al inicio, máxima y cada hora, y CPU media (total y últimos 10 min), para la prueba de 24 horas sin adb.
- **APK de publicación firmado** con `/root/credenciales/app-key.jks` (SHA-256 `DF:71:94:E4:…:04:43`). R8 conserva los 18 serializadores (comprobado en `mapping.txt`) y el APK funcionó en un móvil con Android 17.

## Fase 0 — Reconocimiento

Hecho:

- Datos de entorno, feeds y certificados en `docs/entorno.md`.
- URL de tiempo real confirmada: `https://gtfsrt.renfe.com/trip_updates.pb`.
- Certificados comprobados contra el almacén de autoridades de Android 7.0 (AOSP `android-7.0.0_r1`): Renfe válido; **`api.met.no` falla** porque usa HARICA TLS RSA Root CA 2021 y Android 7.0 solo trae HARICA 2011. Solución: empaquetar HARICA 2021 (RSA y ECC) e ISRG X1/X2 como anclas adicionales. Con ellas los tres dominios validan.
- Medidas del GTFS: 15,7 MB comprimido, 273 MB de `stop_times.txt`, 1,8 millones de filas.

Abierto:

- Versión real del WebView y resolución de la tablet. La app las muestra en Ajustes → Diagnóstico.
- Confirmar los certificados en la tablet real (si fallan, el diagnóstico registra «ERROR DE CERTIFICADO»).

## Fase 1 — Renfe

Hecho:

- Módulo `:renfe` en Kotlin puro: procesado del GTFS en una sola pasada por streaming, índice de estaciones con redes y líneas, buscador sin tildes, horario de la estación con sentidos deducidos por la siguiente parada (con unión de las 8 paradas siguientes para que los semidirectos no creen sentidos falsos), modos A y B, tiempo real y avisos con un decodificador protobuf propio.
- Descarga segura: el horario nuevo se procesa antes de sustituir al anterior; un fichero corrupto no deja la app sin datos.
- 39 pruebas JUnit: paso, nudo, cabecera, semidirecto, viaje con `stop_sequence` desordenado, salida a las 24:30, festivo con `calendar_dates`, fin de semana, cambio de hora, retraso que reordena, tren suprimido, avisos filtrados, decodificación comparada con el JSON oficial de Renfe en el mismo instante, ficheros truncados, descarga corrupta y cambio de estación sin red.

Medidas en el PC (JVM con 256 MB de heap, fichero real):

| Estación | Tipo | Procesado | Pico de heap | Salidas guardadas | Modo |
| --- | --- | --- | --- | --- | --- |
| Coslada (70108) | Paso | 1,0 s | 84 MB | 5.573 (556 KB) | A |
| Madrid-Atocha (18000) | Nudo, 4 sentidos | 1,1 s | 107 MB | 27.230 (2,7 MB) | B |
| Guadalajara (70200) | Cabecera | 1,0 s | 79 MB | 1.412 (143 KB) | B |

Prueba de extremo a extremo contra Renfe (`./gradlew :renfe:smoke -Pstation=70108`): descarga y procesado en 9 s; 440 viajes en tiempo real; en Coslada, 17 de las 39 salidas de las 3 horas siguientes con retraso real; un tren suprimido detectado.

Abierto:

- **Comparación con `tiempo-real.renfe.com`:** diez salidas en dos franjas horarias. La web carga los datos con JavaScript, así que la comparación tiene que hacerla Eneko a ojo, con la app o con `./gradlew :renfe:smoke`.
- La vía de salida no aparece: el GTFS de Renfe no la publica.
- En la tablet el procesado será más lento que en el PC (estimación: 15–30 s). Se mide en Diagnóstico.

## Fase 2 — Meteo

Hecho:

- Módulo `:meteo`: cliente de Locationforecast 2.0 con `User-Agent: pepe/1.0 pepe42rdh@hotmail.com`, coordenadas a 4 decimales, `If-Modified-Since`, `Expires` y como máximo una petición por hora. Una respuesta no válida no sustituye a la anterior.
- Actual, 6 horas y 4 días (hoy incluido) en hora local; símbolo diario del periodo más cercano al mediodía; precipitación sin solapes.
- 11 pruebas JUnit con una respuesta real de Met.no.

## Fase 3 — Panel web

Hecho:

- `panel/`: HTML, CSS y JavaScript sin dependencias, 20 KB en total (límite: 150 KB). Ninguna petición de red: recibe los datos con `window.panel.update()`.
- Modos A y B, cuenta atrás local cada 30 s, reloj por minuto, marca de horario teórico, suprimidos, retrasos con colores, avisos (2 líneas), aviso «Sin conexión desde HH:MM» a los 3 minutos, atribución a Renfe y MET Norway.
- Sin animaciones, transiciones, sombras ni filtros; solo se actualizan los nodos que cambian.
- Capturas con Chrome 119 a 1184 × 800 (ancho completo) y 789 × 800 (dos tercios): `tools/screenshots.sh`, salida en `tools/out/`.

## Fase 4 — App launcher

Hecho:

- App `com.n3k0chan.panelcasa`, `minSdk 24`, `targetSdk 35`. Permisos: solo `INTERNET` y `RECEIVE_BOOT_COMPLETED` (comprobado en el APK y en una prueba).
- Launcher (`HOME`), atrás neutro, modo inmersivo, pantalla siempre encendida mientras se ve el panel, refrescos pausados al abrir otra app y reanudados al volver.
- Panel lateral nativo de 96 dp con apps fijadas, «Todas» y la rueda de ajustes; se actualiza al instalar o desinstalar apps.
- Ajustes: estación, modo de presentación, ubicación del tiempo (estación o manual), apps fijadas con orden, horario nocturno (capa negra con brillo mínimo, se aparta al tocar 2 minutos), brillo, diagnóstico con exportación, acceso a Ajustes de Android y a cambiar la pantalla de inicio.
- Primer arranque: buscador de estaciones.
- Puerta de versión: columna de casa de pruebas solo con API 29 o superior.
- `network_security_config` con las raíces de la fase 0; sin tráfico en claro ni excepciones.
- Recreación del WebView si su proceso muere (Android 8+) y reintento cada 30 s si el panel no carga.
- 15 pruebas (JUnit y Robolectric), incluida la disposición con Android 7.0, 9 y 10, el primer arranque, el botón de atrás, los permisos, la lista de apps y el contrato JSON con `panel/app.js`.
- Lint: 0 errores.
- APK de depuración: 2,6 MB. APK de publicación con R8: 300 KB (sin firmar).

Abierto (necesita la tablet):

- Arranque solo tras reiniciar en menos de 60 s.
- Comportamiento del gestor de inicio automático de ZenUI.
- Aspecto real, legibilidad a distancia y tamaño táctil de los iconos.
- Memoria, CPU y tiempo de procesado del horario.

## Fase 5 — Google Home

Aplazada por decisión de Eneko (19/11/2026). La columna de casa no se crea en ninguna versión de Android mientras `VersionGate.HOME_MODULE_READY` sea `false`; la regla de Android 10 sigue debajo, probada. Ya hay dispositivo de pruebas (móvil con Android 17). Para retomarla hacen falta:

1. El inventario de dispositivos (marca, modelo, habitación, cuáles van al panel, si hay Nest Hub o similar).
2. El visto bueno explícito de Eneko para registrar el proyecto en la consola de Google Home y crear el cliente OAuth.

La puerta de versión y la columna de pruebas ya están, así que el módulo se conectará en `HomeColumn`.

## Fase 6 — Robustez y entrega

Pendiente de la tablet: prueba de 24 horas, cortes de wifi, bloqueo de dominios y vuelta al launcher tras una hora en otra app.

## Presupuesto: situación

| Criterio | Límite | Estado |
| --- | --- | --- |
| Puerta de versión | Sin columna ni clases de Google Home en Android < 10 | Cumplido en Robolectric (API 24 y 28); falta logcat en la tablet |
| Peso del panel web | < 150 KB, cero peticiones | 20 KB, cero peticiones |
| Memoria | < 250 MB tras 24 h | Pendiente (tablet) |
| Procesado del GTFS | < 60 s, sin bloquear la interfaz | 1 s en PC, en segundo plano; pendiente en tablet |
| CPU en reposo | < 5 % | Pendiente (tablet) |
| Tráfico en reposo | RT cada 60 s, avisos cada 5 min, meteo ≤ 1/h | Por diseño: ~30 KB/min + ~36 KB/5 min + ~40 KB/h ≈ 50 MB/día |
| Exactitud de Renfe | 9 de 10 con 1 min de margen | Pendiente (comparación manual) |
| Recuperación | Vuelve sola tras corte de 5 min | Pendiente (tablet) |
| Arranque | < 60 s | Pendiente (tablet); la app lo mide |
| Ubicación | Ningún permiso | Cumplido |
| Certificados | Nada que acepte errores | Cumplido |
