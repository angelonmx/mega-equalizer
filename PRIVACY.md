# Política de privacidad de Mega Equalizer

Última actualización: 10 de noviembre de 2026.

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

## Cobertura total, ADB y root

La cobertura total es opcional y solo se activa desde **Ajustes → Cobertura total**.

- **ADB inalámbrico**: si eliges este método, la app se conecta únicamente a `127.0.0.1`, en el puerto de conexión que indiques, usando la depuración inalámbrica que tú activas en Android. La conexión no se abre a la red local ni a Internet. La app intercambia el protocolo de emparejamiento y autenticación de ADB y solicita la salida de `dumpsys audio`, que describe las sesiones de audio activas.
- **Identidad ADB**: la app genera en el dispositivo un par de claves RSA y un certificado autofirmado y los guarda en su almacenamiento privado. La clave privada no sale del dispositivo ni se envía a Internet. El certificado se presenta a la depuración del propio equipo durante la autenticación, y Android puede guardar la clave pública como clave autorizada. Puedes eliminar la identidad borrando los datos de la app o desinstalándola.
- **Datos de sesiones**: la app lee identificadores de sesión, uso de audio, estado, UID/PID y nombre del paquete asociado. Solo muestra un resumen de estado y guarda el último resumen en sus preferencias; no guarda el volcado completo ni el audio.
- **Root**: si eliges este método, la app ejecuta `su -c dumpsys audio` localmente. No instala binarios ni modifica archivos del sistema. El superusuario puede pedir permiso la primera vez.
- La app no graba audio, no analiza el contenido de lo que escuchas y no envía ninguno de estos datos fuera del dispositivo.

ADB otorga acceso de nivel shell al dispositivo. Actívalo solo mientras uses la cobertura total y desactiva la depuración inalámbrica cuando no la necesites.

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

La única conexión a Internet que realiza la app es la comprobación de actualizaciones al abrirla. Las conexiones de la cobertura total son locales (`127.0.0.1` o el propio proceso) y no cuentan como acceso a Internet. Consulta el archivo público `version.json` del repositorio de GitHub:

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
