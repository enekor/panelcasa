# Entorno

Datos recogidos en la fase 0 (19/11/2026). Lo marcado como «pendiente» necesita la tablet.

## Tablet

| Dato | Valor | Estado |
| --- | --- | --- |
| Modelo | ASUS P00C (ZenPad 10, Z300M), 2 GB de RAM | Aportado por Eneko |
| Android | 7.0.0 (API 24) | Aportado por Eneko |
| WebView | «Android System WebView», sin Chrome; última versión para Android 7.0 (previsiblemente 119) | Pendiente: la app la muestra en Ajustes → Diagnóstico |
| Resolución | 1280 × 800, previsiblemente | Pendiente: confirmar en la tablet |
| Alimentación | Siempre enchufada mientras está en uso | Aportado por Eneko |
| Paquete | `com.n3k0chan.panelcasa` | Acordado |

## Desarrollo

- Código y compilación en el contenedor Docker `3b42393586e1` (`ubuntu26`, Ubuntu 26.04, JDK 17), repositorio en `/root/panelcasa`.
- Android SDK: `/root/Android/Sdk` (plataformas 31–36, build-tools 34 y 35). Gradle 8.14 (wrapper), Android Gradle Plugin 8.11.1, Kotlin 2.2.20.
- Chrome 119 para capturas del panel: `/root/panelcasa-tools/chrome-linux64/` (Chrome for Testing 119.0.6045.105, fuera del repositorio).
- `adb` en Windows (Google Platform Tools instalado con winget).
- Sin emulador.

## Renfe Cercanías

- Horario (GTFS): `https://ssl.renfe.com/ftransit/Fichero_CER_FOMENTO/fomento_transit.zip`
  - 15,7 MB comprimido; `stop_times.txt` ocupa 273 MB descomprimido, con 1,8 millones de filas.
  - 1.168 paradas (925 con servicio), 131.208 viajes, 450 servicios, 15 redes de Cercanías.
  - Campos rellenos con espacios. Sin `direction_id`, `parent_station`, `calendar_dates` ni `trip_headsign`.
  - Un `service_id` por día concreto; cada versión cubre unos 30 días.
  - `stop_times.txt` viene agrupado por viaje (130.774 tramos = 130.774 viajes distintos), lo que permite una sola pasada.
  - El código de red es el prefijo de `route_id`: 10 Madrid, 20 Asturias, 30 Sevilla, 31 Cádiz, 32 Málaga, 40 Valencia, 41 Murcia/Alicante, 45 Cartagena, 46 Ferrol, 47 León, 51 Rodalies de Catalunya, 60 Bilbao, 61 San Sebastián, 62 Santander, 70 Zaragoza.
- Tiempo real (GTFS Realtime, CC BY 4.0):
  - Actualizaciones de viaje: `https://gtfsrt.renfe.com/trip_updates.pb` (~30 KB). También existe `trip_updates_LD.pb` (larga distancia, no se usa).
  - Avisos: `https://gtfsrt.renfe.com/alerts.pb` (~36 KB).
  - Hay versiones `.json` de ambos (~5 veces más grandes); solo se usan como referencia en las pruebas.
  - Los `trip_id` del tiempo real coinciden exactamente con los del horario (438 de 438 en la muestra).
  - El retraso viene a nivel de viaje (`TripUpdate.delay`, segundos), y también en la primera `StopTimeUpdate`.
  - Los avisos traen castellano y valenciano/catalán, ambos etiquetados como `es`; el castellano va primero. Algunos repiten identificador.

## MET Norway

- `https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=…&lon=…` (4 decimales como máximo).
- `User-Agent: pepe/1.0 pepe42rdh@hotmail.com`.
- ~40 KB por respuesta. Datos por horas durante unos 2,5 días y cada 6 horas después. `Expires` a unos 30 minutos.

## Certificados en Android 7.0

Comprobado en la JVM con el almacén de autoridades de AOSP `android-7.0.0_r1` (147 raíces):

| Dominio | Raíz de la cadena | Con Android 7.0 |
| --- | --- | --- |
| `ssl.renfe.com` | USERTrust RSA Certification Authority | Válido |
| `gtfsrt.renfe.com` | USERTrust RSA Certification Authority | Válido |
| `api.met.no` | HARICA TLS RSA Root CA 2021 (vía GEANT TLS RSA 1) | **Falla**: Android 7.0 solo trae HARICA 2011 |

Raíces empaquetadas en la app (`app/src/main/res/raw/`), como anclas adicionales a las del sistema:

| Raíz | SHA-256 | Motivo |
| --- | --- | --- |
| HARICA TLS RSA Root CA 2021 | `D9:5D:0E:8E:DA:79:52:5B:F9:BE:B1:1B:14:D2:10:0D:32:94:98:5F:0C:62:D9:FA:BD:9C:D9:99:EC:CB:7B:1D` | Necesaria para Met.no |
| HARICA TLS ECC Root CA 2021 | `3F:99:CC:47:4A:CF:CE:4D:FE:D5:87:94:66:5E:47:8D:15:47:73:9F:2E:78:0F:1B:B4:CA:9B:13:30:97:D4:01` | Variante ECC de la anterior |
| ISRG Root X1 | `96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6` | Por si algún dominio pasa a Let's Encrypt |
| ISRG Root X2 | `69:72:9B:8E:15:A8:6E:FC:17:7A:57:AF:B7:17:1D:FC:64:AD:D2:8C:2F:CA:8C:F1:50:7E:34:45:3C:CB:14:70` | Ídem, ECC |

Con ellas, los tres dominios validan en la JVM contra el almacén de Android 7.0.

**En la tablet real (20/11/2026), Met.no falló igualmente** con `CertPathValidatorException: Trust anchor for certification path not found`. El servidor envía HARICA 2021 firmada en cruzado por «Hellenic RootCA 2015» (que Android 7.0 no tiene), y el validador de Android 7.0 sigue la cadena tal como llega en vez de cortarla en el ancla HARICA 2021 empaquetada. El validador de Java sí la corta, por eso no se vio en la primera comprobación.

Solución: `net/.../BundledRootsTrustManager.kt`. Primero valida el sistema (con `network_security_config`); solo si falla, se repite una validación PKIX completa (firmas, fechas, uso de clave de servidor) contra las cuatro raíces empaquetadas, cortando la cadena en cuanto aparece una de ellas. El nombre del servidor lo sigue comprobando `HttpsURLConnection`. Comprobado con conexiones reales (`./gradlew :net:liveTls`): solo con las 147 raíces de Android 7.0, Met.no da `SSLHandshakeException`; con el respaldo, HTTP 200; Renfe valida por la vía normal.
