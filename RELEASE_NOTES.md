# Mega Equalizer 1.2

Esta actualización hace que la ecualización siga a cada salida de audio concreta, mantiene el servicio trabajando de forma persistente y añade un flujo de actualización completamente accesible con TalkBack.

## Lo más importante

- **Presets por salida concreta**: cada altavoz, auricular, adaptador USB, salida HDMI, entrada auxiliar o dispositivo Bluetooth tiene su propia asignación. El preset se guarda junto con el nombre, el tipo, la dirección y los detalles de la salida, y se vuelve a aplicar cuando esa salida regresa.
- **Dispositivos recordados**: la pantalla Dispositivos muestra las salidas que ya tienen un preset guardado aunque estén desconectadas.
- **Prioridad clara**: salida concreta → categoría → «Cualquier dispositivo» → sin efecto. Si la salida actual no tiene ningún preset, no se aplica nada.
- **Todo el dispositivo**: el motor global procesa multimedia, notificaciones, llamadas y el canal de accesibilidad de TalkBack cuando el fabricante permite efectos globales.
- **Servicio persistente**: bucle de salud cada 20 segundos, vigilancia del servicio de accesibilidad cada 15 segundos, recuperación tras reinicios y actualizaciones, y reinicio automático si Android detiene el servicio.
- **Actualización accesible**: el diálogo muestra el registro de cambios, la arquitectura de tu dispositivo, la arquitectura instalada y la que se va a descargar. TalkBack anuncia el progreso de la descarga y, al terminar, aparece el diálogo para abrir el instalador.
- **Novedades una sola vez**: al abrir por primera vez una versión recién instalada se muestra su registro de cambios. No vuelve a aparecer en las siguientes aperturas.
- **Persistencia completa**: presets, asignaciones, dispositivos, perfiles por aplicación, ajustes y tema quedan guardados de forma definitiva.

## Actualización desde 1.1

`versionCode` 1002. La app comprueba la actualización al abrirse, elige el APK de la arquitectura real del dispositivo y verifica el SHA-256, el nombre del paquete y el `versionCode` antes de ofrecer la instalación.

## APK incluidos

| Archivo | Arquitectura |
| --- | --- |
| `mega-equalizer-1.2-arm64-v8a.apk` | ARM de 64 bits (recomendado) |
| `mega-equalizer-1.2-armeabi-v7a.apk` | ARM de 32 bits |
| `mega-equalizer-1.2-universal.apk` | ARM64, ARMv7 y x86_64 |

Si instalaste la versión universal en un teléfono ARM64, la próxima actualización descargará automáticamente el APK `arm64-v8a`.

## Notas

- Android 8.0 o superior.
- Los efectos globales dependen del fabricante. Si el teléfono no los permite, la app puede ecualizar reproductores que admiten sesiones de efectos de audio; en ese caso el audio de accesibilidad no puede procesarse globalmente.
- La instalación de una actualización siempre la confirma el usuario mediante el instalador de Android.
- El servicio de accesibilidad es opcional y no lee la pantalla ni el contenido: solo recibe el nombre del paquete en primer plano y mantiene activo el motor global.
- Para que TalkBack anuncie el progreso, el lector de pantalla debe estar activo durante la descarga.

## Verificación de firma

Huella SHA-256 del certificado:

```text
4E:51:69:88:53:8B:3D:20:A8:FE:9B:F4:0A:14:ED:D4:E2:77:FE:EE:97:27:5A:AC:87:4F:0D:E4:95:57:87:17
```
