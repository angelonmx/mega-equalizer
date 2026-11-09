# Mega Equalizer 1.0

Primera versión pública de Mega Equalizer, un ecualizador accesible para Android pensado para usarse con TalkBack.

## Lo más importante

- **Ecualizador de 10 bandas** con preamp y plantillas rápidas.
- **Presets por dispositivo**: altavoz, auriculares cableados, auxiliar, USB, HDMI, Bluetooth y otros.
- **Bluetooth concreto**: asigna un preset a un dispositivo Bluetooth específico o a toda la categoría.
- **Sin asignación, sin efecto**: Mega Equalizer no aplica nada si la salida actual no tiene un preset.
- **Perfiles por aplicación** opcionales mediante el servicio de accesibilidad.
- **Interfaz en español y compatible con TalkBack**, con botones grandes `−` / `+` y descripciones en decibelios.
- **Actualizaciones al abrir la app**, con verificación SHA-256 y selección automática de la arquitectura.

## APK incluidos

| Archivo | Arquitectura |
| --- | --- |
| `mega-equalizer-1.0-arm64-v8a.apk` | ARM de 64 bits (recomendado) |
| `mega-equalizer-1.0-armeabi-v7a.apk` | ARM de 32 bits |
| `mega-equalizer-1.0-universal.apk` | ARM64, ARMv7 y x86_64 |

Si instalas la versión universal en un teléfono ARM64, la próxima actualización descargará automáticamente el APK `arm64-v8a`.

## Notas

- Android 8.0 o superior.
- Los efectos globales dependen del fabricante. Si el teléfono no los permite, la app puede aplicar el ecualizador a reproductores que admiten sesiones de efectos de audio.
- El servicio de accesibilidad es opcional y solo detecta el nombre del paquete en primer plano; no lee la pantalla ni el contenido.

## Verificación de firma

Huella SHA-256 del certificado:

```text
4E:51:69:88:53:8B:3D:20:A8:FE:9B:F4:0A:14:ED:D4:E2:77:FE:EE:97:27:5A:AC:87:4F:0D:E4:95:57:87:17
```
