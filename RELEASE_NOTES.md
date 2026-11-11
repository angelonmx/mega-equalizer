# Mega Equalizer 1.6.1

Esta versión mejora la integración con el servicio de accesibilidad para facilitar la obtención del permiso de Shizuku y la ecualización de TalkBack.

## Lo más importante

- **Servicio de accesibilidad mejorado**: ahora detecta cuándo aparece el diálogo de permiso de Shizuku e intenta hacer clic automático en los botones "Permitir", "Allow", "Aceptar", "OK" o "Conceder" para facilitar la autorización.
- **Ecualizador directo a la estrategia de accesibilidad (TalkBack)**: al iniciar la cobertura con Shizuku, la app obtiene el `AudioProductStrategy` id de `USAGE_ASSISTANCE_ACCESSIBILITY` mediante reflexión dentro del proceso de Shizuku (uid 2000, system API) y aplica el ecualizador directamente a esa estrategia.
- **Método Shizuku (sin root)**: la app lee `dumpsys audio` y ejecuta comandos privilegiados a través de Shizuku si lo tienes instalado y autorizado.
- **Método Root**: en equipos rooteados, ejecuta `su -c dumpsys audio`.
- **Cobertura total**: descubre las sesiones de audio activas y les añade un ecualizador propio, sin grabar el audio ni enviar datos a Internet.
- **3 modos de cobertura**:
  - **Automático** (recomendado): efecto global para multimedia + efectos por sesión a canales omitidos.
  - **Solo llamadas y canales omitidos**: sin efecto global, ecualizador solo a llamadas, accesibilidad, notificaciones y alarmas.
  - **Todas las sesiones**: sin efecto global, ecualizador a cada sesión detectada.
- **Actualizador corregido**: comprueba actualizaciones cada vez que se abre la interfaz y la descarga funciona con fallback de visibilidad.

## Cómo activar la cobertura total

1. Abre **Ajustes → Cobertura total**.
2. Activa **Cobertura total** y elige el modo.
3. Elige el método de acceso:
   - **Shizuku (sin root)**: instala Shizuku desde GitHub (última versión), ábrelo, actívalo y pulsa «Solicitar permiso». Si el servicio de accesibilidad está activo, la app intentará hacer clic automático en el botón de conceder permiso del diálogo de Shizuku.
   - **Root (avanzado)**: pulsa «Probar acceso root».
4. El estado mostrará las sesiones activas y cuántas tienen ecualizador.

## Requisitos y límites

- **Android 8.0 (API 27) o superior** hasta API 37.
- La estrategia de accesibilidad vía Shizuku requiere que Shizuku esté instalado, activo y autorizado para Mega Equalizer.
- El clic automático en el diálogo de Shizuku es experimental y puede no funcionar en todos los dispositivos.
- La cobertura total depende de que Android y el fabricante permitan crear efectos sobre cada sesión/estrategia. Algunos dispositivos bloquean efectos en sesiones de accesibilidad; en esos casos incluso este método puede no funcionar.
- Ninguna app normal puede autoconcederse `MODIFY_AUDIO_ROUTING`; Shizuku o root son las vías privilegiadas. Mega Equalizer nunca eleva privilegios por su cuenta.
- Los sonidos que no crean una sesión de audio propia (algunos tonos del sistema) y los sonidos muy cortos pueden quedar sin ecualizar.
- La cobertura total es opcional. Sin ella, la app sigue funcionando exactamente como en 1.2 con el efecto global y los perfiles por salida.

## Actualización desde 1.6

`versionCode` 1601. La app comprueba la actualización al abrirse, elige el APK de la arquitectura real del dispositivo y verifica el SHA-256, el nombre del paquete y el `versionCode` antes de ofrecer la instalación. Los presets, asignaciones, dispositivos y ajustes existentes se conservan.

## APK incluidos

| Archivo | Arquitectura |
| --- | --- |
| `mega-equalizer-1.6.1-arm64-v8a.apk` | ARM de 64 bits (recomendado) |
| `mega-equalizer-1.6.1-armeabi-v7a.apk` | ARM de 32 bits |
| `mega-equalizer-1.6.1-universal.apk` | ARM64, ARMv7 y x86_64 |

## Notas

- La instalación de una actualización siempre la confirma el usuario mediante el instalador de Android.
- El servicio de accesibilidad es opcional y no lee la pantalla ni el contenido: solo recibe el nombre del paquete en primer plano y mantiene activo el motor.
- Para que TalkBack anuncie el progreso, el lector de pantalla debe estar activo durante la descarga.
- Los avisos de licencias de terceros están disponibles dentro de la app, en **Ajustes → Licencias**. Mega Equalizer se distribuye bajo **GNU GPL v3 o posterior**.

## Verificación de firma

Huella SHA-256 del certificado:

```text
4E:51:69:88:53:8B:3D:20:A8:FE:9B:F4:0A:14:ED:D4:E2:77:FE:EE:97:27:5A:AC:87:4F:0D:E4:95:57:87:17
```
