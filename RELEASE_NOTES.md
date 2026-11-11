# Mega Equalizer 1.3

Esta actualización amplía la ecualización a los canales que el efecto global suele dejar fuera —llamadas, alarmas, notificaciones y canales del fabricante— mediante la **Cobertura total**. La app descubre las sesiones de audio activas del propio dispositivo y les añade un ecualizador independiente, sin grabar el audio ni enviar datos a Internet.

## Lo más importante

- **Cobertura total**: además del efecto global, Mega Equalizer puede ecualizar cada sesión de audio activa por separado. Esto permite llegar a llamadas de voz, alarmas, notificaciones y canales propios del fabricante en equipos donde la mezcla global no los procesa.
- **Modo Automático (recomendado)**: mantiene el efecto global para toda la mezcla y añade efectos por sesión solo a llamadas y canales de voz, que son los que suelen quedar fuera.
- **Modo Solo por sesión (avanzado)**: libera el efecto global y aplica un ecualizador a cada sesión descubierta. Útil si el efecto global no cubre notificaciones, alarmas o accesibilidad. Los sonidos sin sesión propia y los sonidos muy cortos pueden quedar sin ecualizar; la app lo advierte antes de activarlo.
- **ADB inalámbrico sin root**: la app se empareja con la depuración inalámbrica del mismo equipo a través de `127.0.0.1`, sin PC ni cable. Solo necesitas introducir el puerto y el código de emparejamiento de 6 dígitos que muestra Android, más el puerto de conexión.
- **Identidad ADB propia**: Mega Equalizer genera un par de claves RSA y su certificado en el dispositivo y los guarda en su almacenamiento privado. Esa identidad es la que autoriza la conexión local con la depuración inalámbrica.
- **Conexión persistente**: una sola conexión y un flujo de shell de larga duración entregan las instantáneas de `dumpsys audio`. La app reutiliza la conexión, espera los datos con tiempo límite y se recupera sola si la depuración se reinicia o falla.
- **Root opcional**: en equipos rooteados, el método **Root (avanzado)** ejecuta `su -c dumpsys audio` para descubrir sesiones. No instala nada ni modifica archivos del sistema; el superusuario puede pedir permiso la primera vez.
- **Accesible con TalkBack**: la pantalla de cobertura incluye pasos, estados, advertencias y anuncios hablados en español, con la misma cobertura de accesibilidad que el resto de la app.

## Cómo activar la cobertura total

1. Abre **Ajustes → Cobertura total**.
2. Activa **Cobertura total** y elige el modo **Automático**.
3. Elige el método de acceso:
   - **ADB inalámbrico (sin root)**: activa **Opciones de desarrollador → Depuración inalámbrica**, toca **Emparejar dispositivo con código**, introduce el puerto y el código de emparejamiento, y escribe el puerto de conexión que muestra Android.
   - **Root (avanzado)**: pulsa **Probar acceso root** y acepta la solicitud del superusuario.
4. Pulsa **Emparejar y conectar** o **Probar conexión**. El estado mostrará las sesiones activas y cuántas tienen ecualizador.

El puerto de conexión cambia si reinicias el equipo o desactivas la depuración inalámbrica; si eso ocurre, vuelve a escribir el puerto actual. La app reintenta automáticamente y no necesita que la mantengas abierta.

## Requisitos y límites

- Android 8.0 o superior.
- El emparejamiento inalámbrico necesita **Android 11 o superior**. En Android 8–10 puedes usar root o un puerto 5555 que ya esté autorizado.
- La cobertura total depende de que Android y el fabricante permitan crear efectos sobre cada sesión. Algunos equipos limitan los efectos de sesión o los procesan después de la ruta de audio; en esos casos la app recurre al ecualizador del sistema y lo indica.
- Ninguna app normal puede autoconcederse `MODIFY_AUDIO_ROUTING`; ADB, root o una herramienta como Shizuku son las vías privilegiadas. Mega Equalizer usa ADB o root, nunca eleva privilegios por su cuenta.
- ADB otorga acceso de nivel shell al dispositivo. Actívalo solo mientras uses la cobertura total y desactiva la depuración inalámbrica cuando no la necesites. La conexión de Mega Equalizer se limita a `127.0.0.1` y no se abre a la red.
- Los sonidos que no crean una sesión de audio propia (algunos tonos del sistema) y los sonidos muy cortos pueden quedar fuera del modo Solo por sesión.
- La cobertura total es opcional. Sin ella, la app sigue funcionando exactamente como en 1.2 con el efecto global y los perfiles por salida.

## Actualización desde 1.2

`versionCode` 1003. La app comprueba la actualización al abrirse, elige el APK de la arquitectura real del dispositivo y verifica el SHA-256, el nombre del paquete y el `versionCode` antes de ofrecer la instalación. Los presets, asignaciones, dispositivos y ajustes existentes se conservan.

## APK incluidos

| Archivo | Arquitectura |
| --- | --- |
| `mega-equalizer-1.3-arm64-v8a.apk` | ARM de 64 bits (recomendado) |
| `mega-equalizer-1.3-armeabi-v7a.apk` | ARM de 32 bits |
| `mega-equalizer-1.3-universal.apk` | ARM64, ARMv7 y x86_64 |

Si instalaste la versión universal en un teléfono ARM64, la próxima actualización descargará automáticamente el APK `arm64-v8a`.

## Notas

- La instalación de una actualización siempre la confirma el usuario mediante el instalador de Android.
- El servicio de accesibilidad es opcional y no lee la pantalla ni el contenido: solo recibe el nombre del paquete en primer plano y mantiene activo el motor.
- Para que TalkBack anuncie el progreso, el lector de pantalla debe estar activo durante la descarga.
- Los avisos de licencias de terceros están disponibles dentro de la app, en **Ajustes → Licencias**.

## Verificación de firma

Huella SHA-256 del certificado:

```text
4E:51:69:88:53:8B:3D:20:A8:FE:9B:F4:0A:14:ED:D4:E2:77:FE:EE:97:27:5A:AC:87:4F:0D:E4:95:57:87:17
```
