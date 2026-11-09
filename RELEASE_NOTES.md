# Mega Equalizer 1.1

Esta actualización cambia la licencia del proyecto a **GPLv3** y refuerza la integración del servicio de accesibilidad con el motor global de audio.

## Lo más importante

- **Licencia GPLv3**: Mega Equalizer pasa a distribuirse bajo la GNU General Public License, versión 3 o posterior. El texto completo se incluye dentro de la app y en el repositorio.
- **Servicio de accesibilidad y audio global**: al conectarse, el servicio de accesibilidad mantiene activo y refresca el motor de ecualización global. La mezcla completa del dispositivo, incluido el canal de accesibilidad que usa TalkBack, pasa por el ecualizador cuando el fabricante permite efectos globales.
- **Nueva pantalla de licencia**: accesible con TalkBack, con el texto completo de la GPLv3 y navegación de vuelta.
- **Sin cambios en la lógica de presets**: si la salida actual no tiene un preset asignado, Mega Equalizer sigue sin aplicar ningún efecto.
- **Actualización desde 1.0**: `versionCode` 1001. La app comprueba la actualización al abrirse y elige el APK de la arquitectura real del dispositivo.

## APK incluidos

| Archivo | Arquitectura |
| --- | --- |
| `mega-equalizer-1.1-arm64-v8a.apk` | ARM de 64 bits (recomendado) |
| `mega-equalizer-1.1-armeabi-v7a.apk` | ARM de 32 bits |
| `mega-equalizer-1.1-universal.apk` | ARM64, ARMv7 y x86_64 |

Si instalaste la versión universal en un teléfono ARM64, la próxima actualización descargará automáticamente el APK `arm64-v8a`.

## Notas

- Android 8.0 o superior.
- Los efectos globales dependen del fabricante. Si el teléfono no los permite, la app puede aplicar el ecualizador a reproductores que admiten sesiones de efectos de audio; en ese caso el audio de accesibilidad no puede procesarse globalmente.
- La instalación de una actualización siempre la confirma el usuario mediante el instalador de Android.
- El servicio de accesibilidad es opcional y no lee la pantalla ni el contenido: solo recibe el nombre del paquete en primer plano y mantiene activo el motor global.

## Verificación de firma

Huella SHA-256 del certificado:

```text
4E:51:69:88:53:8B:3D:20:A8:FE:9B:F4:0A:14:ED:D4:E2:77:FE:EE:97:27:5A:AC:87:4F:0D:E4:95:57:87:17
```
