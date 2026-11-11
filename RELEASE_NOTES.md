# Mega Equalizer 1.4

Esta actualización añade **Shizuku** como nueva vía de conexión para descubrir sesiones de audio (sin root), amplía la compatibilidad de API 27 a API 37, y corrige el actualizador para que compruebe actualizaciones cada vez que se abre la interfaz y que la descarga funcione correctamente.

## Lo más importante

- **Shizuku (sin root)**: nuevo método de acceso en Cobertura total. La app lee `dumpsys audio` a través de Shizuku si lo tienes instalado y autorizado. En modo **Automático**, prefiere Shizuku cuando está disponible y usa ADB como fallback. Si Shizuku no está instalado, la app te ofrece descargarlo desde GitHub.
- **Cobertura total**: además del efecto global, Mega Equalizer puede ecualizar cada sesión de audio activa por separado (llamadas, alarmas, notificaciones y canales del fabricante), sin grabar el audio ni enviar datos a Internet.
- **Modo Automático (recomendado)**: mantiene el efecto global para toda la mezcla y añade efectos por sesión solo a llamadas y canales de voz.
- **Modo Solo por sesión (avanzado)**: libera el efecto global y aplica un ecualizador a cada sesión descubierta.
- **ADB inalámbrico sin root**: emparejamiento con la depuración inalámbrica del propio equipo a través de `127.0.0.1`.
- **Root opcional**: en equipos rooteados, ejecuta `su -c dumpsys audio` para descubrir sesiones.

## Actualizador corregido

- La app comprueba si hay actualizaciones **cada vez que se abre la interfaz gráfica** (`onStart`), no solo al iniciar el servicio en segundo plano.
- La descarga ahora funciona: si `VISIBILITY_HIDDEN` es bloqueada (por ejemplo en Android 15), se reintenta con notificación visible.
- Se añade el permiso `android.permission.DOWNLOAD_WITHOUT_NOTIFICATION` para descargas sin notificación duplicada.

## Cómo activar la cobertura total

1. Abre **Ajustes → Cobertura total**.
2. Activa **Cobertura total** y elige el modo **Automático**.
3. Elige el método de acceso:
   - **Shizuku (sin root)**: instala Shizuku desde GitHub (última versión), ábrelo, actívalo y pulsa «Solicitar permiso» en la app.
   - **ADB inalámbrico (sin root)**: activa **Opciones de desarrollador → Depuración inalámbrica**, toca **Emparejar dispositivo con código**, introduce el puerto y el código de emparejamiento, y escribe el puerto de conexión que muestra Android.
   - **Root (avanzado)**: pulsa **Probar acceso root** y acepta la solicitud del superusuario.
4. Pulsa **Emparejar y conectar** o **Probar conexión**. El estado mostrará las sesiones activas y cuántas tienen ecualizador.

## Requisitos y límites

- **Android 8.0 (API 27) o superior** hasta API 37.
- El emparejamiento inalámbrico necesita **Android 11 o superior**. En Android 8–10 puedes usar root o un puerto 5555 que ya esté autorizado.
- La cobertura total depende de que Android y el fabricante permitan crear efectos sobre cada sesión. Algunos equipos limitan los efectos de sesión o los procesan después de la ruta de audio; en esos casos la app recurre al ecualizador del sistema y lo indica.
- Ninguna app normal puede autoconcederse `MODIFY_AUDIO_ROUTING`; ADB, root o Shizuku son las vías privilegiadas. Mega Equalizer nunca eleva privilegios por su cuenta.
- ADB otorga acceso de nivel shell al dispositivo. La conexión de Mega Equalizer se limita a `127.0.0.1` y no se abre a la red.
- Los sonidos que no crean una sesión de audio propia (algunos tonos del sistema) y los sonidos muy cortos pueden quedar fuera del modo Solo por sesión.
- La cobertura total es opcional. Sin ella, la app sigue funcionando exactamente como en 1.2 con el efecto global y los perfiles por salida.

## Actualización desde 1.3

`versionCode` 1004. La app comprueba la actualización al abrirse, elige el APK de la arquitectura real del dispositivo y verifica el SHA-256, el nombre del paquete y el `versionCode` antes de ofrecer la instalación. Los presets, asignaciones, dispositivos y ajustes existentes se conservan.

## APK incluidos

| Archivo | Arquitectura |
| --- | --- |
| `mega-equalizer-1.4-arm64-v8a.apk` | ARM de 64 bits (recomendado) |
| `mega-equalizer-1.4-armeabi-v7a.apk` | ARM de 32 bits |
| `mega-equalizer-1.4-universal.apk` | ARM64, ARMv7 y x86_64 |

Si instalaste la versión universal en un teléfono ARM64, la próxima actualización descargará automáticamente el APK `arm64-v8a`.

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
