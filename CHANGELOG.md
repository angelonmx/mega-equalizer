# Historial de cambios

Todas las versiones publicadas de Mega Equalizer.

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
