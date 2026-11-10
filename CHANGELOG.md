# Historial de cambios

Todas las versiones publicadas de Mega Equalizer.

## 1.2 — 2026-11-09

### Ecualización de todo el dispositivo

- El motor global sigue procesando la mezcla completa: multimedia, notificaciones, llamadas y el canal de accesibilidad que usa TalkBack, cuando el fabricante permite efectos globales.
- Nuevo bucle de salud del motor cada 20 segundos: vuelve a aplicar el efecto si Android lo pierde, lo bloquea o lo libera.
- El servicio de accesibilidad refresca el motor cada 15 segundos mientras está activo.
- El servicio en primer plano es persistente (`START_STICKY`) y se recupera tras reinicios, actualizaciones de la app o cierres agresivos mediante `JobScheduler` y un receptor de arranque.
- Se mantiene la regla: si la salida actual no tiene preset, no se aplica ningún efecto.

### Presets persistentes por salida concreta

- Cada salida de audio identificable tiene ahora su propia clave: altavoz, auriculares, USB, HDMI, auxiliar y cualquier otro dispositivo, además de Bluetooth.
- La asignación guarda el nombre, el tipo, la categoría, la dirección y la última vez visto del dispositivo.
- Nueva sección **Dispositivos recordados** en la pantalla Dispositivos: muestra las salidas desconectadas que conservan su preset y permite reasignarlas.
- El editor de presets incluye un selector de **dispositivos concretos** para todas las salidas, no solo Bluetooth.
- Nueva prioridad de resolución: salida concreta → categoría → «Cualquier dispositivo» → sin efecto.

### Actualizaciones accesibles

- El diálogo de actualización muestra el registro de cambios, la arquitectura del dispositivo, la arquitectura instalada y la arquitectura que se descargará, además del nombre y el número de versión.
- Nueva notificación de progreso de descarga en primer plano, con porcentaje, tamaño descargado y tamaño total.
- TalkBack anuncia el inicio de la descarga, el progreso (como mínimo cada 10 % o cada 20 segundos), la finalización, los errores y la cancelación.
- La descarga se puede cancelar desde la notificación.
- Al terminar y verificar el APK se muestra un diálogo para abrir el instalador de Android, además de la notificación de actualización lista.
- La primera vez que se abre una versión recién instalada se muestra una sola vez su registro de cambios. Las instalaciones nuevas no muestran este aviso.
- Se descarta automáticamente cualquier APK descargado que ya esté instalado o que pertenezca a una versión anterior.

### Persistencia y calidad

- Se guardan de forma definitiva presets, asignaciones, dispositivos, perfiles por app, ajustes, tema y estado de actualización.
- Los diálogos y anuncios de TalkBack se mantienen en español.
- Se publican exactamente tres APK: `arm64-v8a`, `armeabi-v7a` y universal con `x86_64` solo dentro del universal.
- Licencia GPLv3 sin cambios.

## 1.1 — 2026-11-08

### Licencia

- El proyecto adopta la **GNU General Public License, versión 3 o posterior**.
- El texto completo de la GPLv3 se incluye dentro del APK y en el archivo `LICENSE`.
- Nueva pantalla **Licencia GPL v3** en Ajustes, accesible con TalkBack.

### Accesibilidad y audio

- El servicio de accesibilidad ahora mantiene activo y refresca el motor global al conectarse.
- El motor global procesa la mezcla completa del dispositivo, incluido el canal de accesibilidad y la voz de TalkBack, cuando el fabricante permite efectos globales.
- Nuevas explicaciones en Ajustes, en la pantalla principal y en la descripción del servicio de accesibilidad.
- Se mantiene el comportamiento de no aplicar nada cuando la salida actual no tiene un preset asignado.

### Documentación y publicación

- README actualizado para la versión 1.1 y la licencia GPLv3.
- Registro de cambios y notas de la versión 1.1 actualizados.
- El workflow de release sigue publicando exactamente tres APK: `arm64-v8a`, `armeabi-v7a` y `universal`.

## 1.0 — 2026-11-08

Primera versión pública.

### Ecualización

- Ecualizador de 10 bandas con ganancia previa (preamp).
- Motor principal `DynamicsProcessing` global con 10 bandas (Android 9+).
- Respaldo con el ecualizador del sistema Android.
- Efectos por sesión de audio para reproductores compatibles cuando el efecto global no está disponible.
- Detección automática de cambios en la salida de audio.

### Presets y asignaciones

- Crear, editar, duplicar y eliminar presets.
- Plantillas: Plano, Más graves, Más agudos, Voz clara y Podcast.
- Asignación por altavoz, auriculares cableados, auxiliar, USB, HDMI, otros y «Cualquier dispositivo».
- Asignación a una categoría Bluetooth completa o a dispositivos Bluetooth concretos.
- Prioridad: dispositivo Bluetooth concreto → categoría Bluetooth → «Cualquier dispositivo».
- Sin preset asignado no se aplica ningún efecto.
- Perfiles por aplicación con el servicio de accesibilidad (opcionales y experimentales).

### Accesibilidad

- Interfaz completa en español.
- Compatible con TalkBack: etiquetas y descripciones de estado claras.
- Botones grandes de ganancia `−` / `+` y controles deslizantes con descripciones en decibelios.
- Anuncios hablados al guardar asignaciones y cambios.
- Navegación por encabezados y objetivos táctiles de tamaño adecuado.

### Sistema y actualizaciones

- Servicio en primer plano con notificación de estado y acción para desactivar.
- Comprobación de actualizaciones solo al abrir la app.
- Selección del APK según la arquitectura real del dispositivo.
- Verificación SHA-256, nombre de paquete y `versionCode` antes de instalar.
- Instalación mediante el instalador del sistema Android.
- Tema claro, oscuro o según el sistema.
- Registro de licencia y política de privacidad dentro de la app.
