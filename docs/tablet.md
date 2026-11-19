# Guía de la tablet

Pasos manuales en la ASUS ZenPad 10 (Android 7.0). Los nombres de menú pueden variar un poco según la versión de ZenUI.

## 1. Preparar la tablet

1. **Actualizar el WebView:** Play Store → buscar «Android System WebView» → Actualizar. Es el motor que pinta el panel.
2. **Hora automática:** Ajustes → Fecha y hora → activar «Fecha y hora automáticas» y «Zona horaria automática». Los minutos de los trenes dependen de la hora del dispositivo.
3. **Protector de pantalla:** Ajustes → Pantalla → Protector de pantalla (Daydream) → desactivado.
4. **Sincronización:** Ajustes → Cuentas → desactivar la sincronización de lo que no se use.
5. **Apps preinstaladas:** Ajustes → Aplicaciones → abrir cada app que no se use → «Desactivar». No desactivar nunca «Android System WebView», «Servicios de Google Play» ni el launcher original de ASUS (es la salida de emergencia).

## 2. Instalar la app

### Con cable y adb (recomendado)

1. En la tablet: Ajustes → Información del tablet → pulsar 7 veces «Número de compilación». Después Ajustes → Opciones de desarrollador → activar «Depuración USB».
2. Conectar por USB y aceptar en la tablet el aviso de «¿Permitir depuración USB?» (marcar «Permitir siempre desde este ordenador»).
3. En Windows (PowerShell):

   ```powershell
   docker exec 3b42393586e1 sh -c "cd /root/panelcasa && ./gradlew :app:assembleRelease"
   docker cp 3b42393586e1:/root/panelcasa/app/build/outputs/apk/release/app-release.apk $env:TEMP\panelcasa-release.apk
   adb install -r $env:TEMP\panelcasa-release.apk
   ```

   El APK de publicación se firma con `/root/credenciales/app-key.jks`. El de depuración (`assembleDebug`) lleva otra firma: para cambiar de uno a otro hay que desinstalar antes.

### Sin adb

1. Copiar `app-debug.apk` a la tablet (por cable, como un fichero cualquiera, a la carpeta Download).
2. Ajustes → Seguridad → activar «Orígenes desconocidos».
3. Abrir el APK con el gestor de archivos e instalar. Después se puede volver a desactivar «Orígenes desconocidos».

## 3. Hacerla pantalla de inicio

1. Pulsar el botón de inicio. Android pregunta qué aplicación usar: elegir **Panel de casa** → **Siempre**.
2. Si no pregunta: Ajustes → Aplicaciones → rueda dentada → Aplicaciones predeterminadas → Aplicación de inicio → Panel de casa.
3. **Gestor de inicio automático de ASUS:** si existe en la tablet (Administrador móvil → Gestor de inicio automático), permitir «Panel de casa». Si existe un modo de ahorro de energía que cierre apps, excluirla.
4. Reiniciar la tablet y comprobar que el panel aparece solo.

## 4. Primer uso

1. Al abrir por primera vez aparece el buscador de estaciones. La primera vez descarga el horario de Renfe (15 MB) y lo procesa; puede tardar alrededor de un minuto.
2. Escribir el nombre de la estación y tocarla. El tiempo usa las coordenadas de esa estación.
3. Rueda dentada (abajo en el panel lateral) → «Apps del panel lateral» para fijar apps. Por defecto se fijan los Ajustes de Android.

## 5. Botón de bloqueo

El candado del panel lateral apaga y bloquea la pantalla. La primera vez Android pide activar «Panel de casa» como administrador del dispositivo: solo con el permiso de bloquear la pantalla, sin ningún otro. Pulsar **Activar**. Después, cada toque bloquea.

Para desinstalar la app hay que quitar antes ese permiso: rueda dentada → «Quitar el permiso de bloqueo» (o Ajustes de Android → Seguridad → Administradores de dispositivos).

## 6. Volver al launcher original

- Desde la app: rueda dentada → «Cambiar la pantalla de inicio».
- Desde Android: Ajustes → Aplicaciones → rueda dentada → Aplicaciones predeterminadas → Aplicación de inicio.
- Si la app no arranca: Ajustes → Aplicaciones → Panel de casa → «Borrar valores predeterminados», y pulsar inicio.

## 7. Recoger métricas

### Sin cable

Rueda dentada → Diagnóstico → **«Compartir informe»** (WhatsApp, correo…). El informe incluye memoria al inicio, máxima y cada hora, CPU media, tiempos de arranque y de procesado, versión y capacidades del WebView y los últimos sucesos.

También se puede usar «Exportar informe»: el fichero queda en `Android/data/com.n3k0chan.panelcasa/files/` y se copia por USB.

### Con adb

```powershell
adb shell dumpsys meminfo com.n3k0chan.panelcasa          # memoria (TOTAL PSS)
adb shell top -n 1 | findstr panelcasa                      # CPU en ese momento
adb shell dumpsys package com.n3k0chan.panelcasa | findstr permission   # permisos
adb logcat -s PanelCasa                                     # sucesos de la app
adb pull /sdcard/Android/data/com.n3k0chan.panelcasa/files/ .           # informes exportados
```

Para la media de CPU de 5 minutos: `adb shell top -d 10 -n 30 | findstr panelcasa` y promediar la columna de CPU.

## 8. Batería

La tablet va a estar siempre enchufada. Para no tenerla al 100 % de forma continua, se recomienda un enchufe con temporizador que corte la carga varias horas al día (por ejemplo, cargar de 7:00 a 9:00 y de 19:00 a 21:00). La app no puede limitar la carga.
