# Panel de casa

Launcher para una tablet Android 7 (ASUS ZenPad 10) que muestra en una sola pantalla los próximos trenes de Cercanías de una estación, en ambos sentidos, y el tiempo, con un panel lateral para abrir apps. Sin servidor propio: la app descarga directamente de Renfe y de MET Norway. Con Android 10 o posterior se añadirá una columna de Google Home (fase 5, pendiente).

- Estado y medidas: [`docs/ESTADO.md`](docs/ESTADO.md)
- Entorno, feeds y certificados: [`docs/entorno.md`](docs/entorno.md)
- Pasos en la tablet: [`docs/tablet.md`](docs/tablet.md)

## Estructura

| Carpeta | Qué es |
| --- | --- |
| `net/` | Cliente HTTP mínimo (Kotlin puro) |
| `renfe/` | GTFS y GTFS Realtime de Renfe Cercanías, sentidos, próximos trenes, buscador (Kotlin puro) |
| `meteo/` | Previsión de MET Norway (Kotlin puro) |
| `panel/` | Panel web: `index.html`, `style.css`, `app.js`, `icons.js`; `dev.html` y `mock-data.js` solo para pruebas |
| `app/` | App Android: launcher, panel lateral, ajustes, diagnóstico |
| `tools/` | `screenshots.sh` (capturas del panel con Chrome 119) |
| `docs/` | Documentación |

## Compilar y probar

Todo se ejecuta en el contenedor `3b42393586e1`, en `/root/panelcasa`:

```sh
./gradlew test                       # todas las pruebas
./gradlew :app:assembleDebug         # APK de depuración
./gradlew :app:lintDebug             # análisis estático
./gradlew :renfe:benchmark -Pzip=/ruta/fomento_transit.zip -Pstation=70108   # tiempo y memoria del procesado
./gradlew :renfe:smoke -Pstation=70108                                       # prueba real contra Renfe (necesita red)
sh tools/screenshots.sh              # capturas del panel en tools/out/
```

El panel web también se puede abrir en cualquier navegador: `panel/dev.html?mode=A&alerts=1` (parámetros en `panel/mock-data.js`).

## Instalar en la tablet

Desde Windows, con `adb` y la tablet conectada:

```powershell
docker exec 3b42393586e1 sh -c "cd /root/panelcasa && ./gradlew :app:assembleDebug"
docker cp 3b42393586e1:/root/panelcasa/app/build/outputs/apk/debug/app-debug.apk $env:TEMP\panelcasa-debug.apk
adb install -r $env:TEMP\panelcasa-debug.apk
```

Sin adb y resto de pasos (pantalla de inicio, ASUS, métricas): [`docs/tablet.md`](docs/tablet.md).

## Uso habitual

- **Cambiar de estación:** rueda dentada (abajo en el panel lateral) → Estación de Cercanías. Usa el horario ya descargado, sin red.
- **Fijar apps en el panel lateral:** rueda dentada → Apps del panel lateral; marcar y ordenar con ▲ ▼. «Todas» abre la lista completa.
- **Presentación de los trenes:** automática (por sentidos en estaciones de paso, lista única en nudos y cabeceras), o forzada en ajustes.
- **Pantalla de noche:** rueda dentada → Pantalla. Oscurece con brillo mínimo y se ilumina 2 minutos al tocar.
- **Volver al launcher original:** rueda dentada → Cambiar la pantalla de inicio.
- **Diagnóstico:** rueda dentada → Diagnóstico → Exportar informe.

## Mantenimiento

- **Raíces de certificados:** si Renfe o Met.no cambian de autoridad y la tablet deja de conectar (el diagnóstico muestra «ERROR DE CERTIFICADO»), añadir la nueva raíz en `app/src/main/res/raw/` y en `app/src/main/res/xml/network_security_config.xml`, y publicar un APK nuevo. Comprobación previa en la JVM con el almacén de Android 7.0: ver `docs/entorno.md`.
- **Identificación ante MET Norway:** constante `PanelApp.USER_AGENT`.
- **Redes de Cercanías nuevas:** mapa de prefijos en `renfe/.../Model.kt` (`Networks`).
- **Google Home (fase 5):** pendiente; se conectará en `app/.../HomeColumn.kt`, que ya está detrás de la puerta de versión (`VersionGate`, API 29).

## Datos

Renfe Cercanías (horario y tiempo real, CC BY 4.0) y MET Norway (CC BY 4.0). La atribución aparece en el panel.
