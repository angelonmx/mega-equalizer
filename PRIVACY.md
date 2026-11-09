# Política de privacidad de Mega Equalizer

Última actualización: 8 de noviembre de 2026.

Mega Equalizer es una aplicación local de ecualización de audio. No recopila, vende ni comparte datos personales.

## Qué datos se guardan

La app guarda únicamente en tu dispositivo:

- los presets y sus ajustes de audio;
- las asignaciones de presets a salidas y categorías de audio;
- las asignaciones de presets a aplicaciones;
- los nombres y direcciones de dispositivos Bluetooth que hayas elegido;
- tus preferencias (tema, actualizaciones automáticas, último estado del ecualizador);
- el estado de una descarga de actualización pendiente.

Estos datos se almacenan en el almacenamiento privado de la app. Se incluyen en las copias de seguridad del sistema solo si tienes activada la copia de seguridad de Android. Puedes borrarlos con **Ajustes → Datos → Restablecer todos los ajustes** o desinstalando la app.

## Acceso al audio

Mega Equalizer usa el permiso `MODIFY_AUDIO_SETTINGS` para aplicar efectos de audio del sistema sobre la mezcla de salida, incluido el audio de accesibilidad cuando el dispositivo lo permite. No graba audio, no guarda sonido y no analiza el contenido de lo que escuchas.

## Servicio de accesibilidad

El servicio de accesibilidad es opcional. Se usa para recibir el nombre del paquete de la aplicación que está en primer plano cuando activas los perfiles por aplicación y para mantener activo el motor global de ecualización. El motor global procesa la mezcla completa del dispositivo, incluido el canal de audio de accesibilidad, cuando el fabricante permite efectos globales.

- No lee el texto ni el contenido de la pantalla.
- No usa captura de pantalla.
- No controla otros dispositivos ni realiza acciones en tu nombre.
- No envía información a Internet.

Puedes desactivarlo en cualquier momento desde los ajustes de accesibilidad de Android.

## Bluetooth

El permiso `BLUETOOTH_CONNECT` se usa para mostrar dispositivos Bluetooth emparejados y para identificar la salida Bluetooth conectada. La app no empareja, no desconecta ni modifica dispositivos Bluetooth.

## Internet y actualizaciones

La única conexión de red que realiza la app es la comprobación de actualizaciones al abrirla. Consulta el archivo público `version.json` del repositorio de GitHub:

```text
https://github.com/angelonmx/mega-equalizer/releases/latest/download/version.json
```

GitHub puede registrar la petición según su propia política de privacidad. Mega Equalizer no envía identificadores, datos de uso ni información del dispositivo en esa petición.

La descarga de una actualización se realiza desde GitHub Releases y se verifica con SHA-256 antes de ofrecer la instalación. La app no instala nada sin tu confirmación.

## Anuncios y terceros

Mega Equalizer no incluye anuncios, rastreadores, SDK de analítica ni servicios de cuentas. No hay terceros que reciban datos desde la app.

## Menores

La app no está dirigida específicamente a menores y no recopila datos de ninguna persona.

## Cambios

Si esta política cambia, la versión actualizada se publicará en el repositorio junto con el código fuente.

## Contacto

Puedes abrir una incidencia en [github.com/angelonmx/mega-equalizer/issues](https://github.com/angelonmx/mega-equalizer/issues).
