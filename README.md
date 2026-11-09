# Mega Equalizer

Ecualizador de audio para Android, en español, pensado desde el principio para funcionar con lectores de pantalla como TalkBack. Sin anuncios, sin cuentas y sin recopilación de datos.

Mega Equalizer aplica ecualizaciones por dispositivo y, de forma opcional, por aplicación. Si una salida de audio no tiene un preset asignado, la app **no aplica ningún efecto**.

## Características

- **Accesible con TalkBack**: etiquetas claras, descripciones de estado, objetivos táctiles grandes, navegación por encabezados y anuncios hablados al guardar o asignar.
- **Presets de 10 bandas**: 31 Hz a 16 kHz, ganancia previa (preamp), plantillas rápidas y edición con botones `−` / `+` además de los controles deslizantes.
- **Asignación por salida de audio**:
  - altavoz del teléfono;
  - auriculares o entrada auxiliar cableados;
  - USB;
  - HDMI;
  - Bluetooth: categoría completa o dispositivos Bluetooth concretos, conectados o emparejados;
  - otros dispositivos;
  - comodín «Cualquier dispositivo».
- **Prioridad clara**: dispositivo Bluetooth concreto → categoría Bluetooth → «Cualquier dispositivo» → sin efecto.
- **Cobertura global**: el motor principal procesa la mezcla completa de audio, incluidos los canales de accesibilidad y la voz de TalkBack, cuando el fabricante permite efectos globales.
- **Perfiles por aplicación (opcionales y experimentales)**: usan el servicio de accesibilidad para detectar el paquete en primer plano y mantener activo el motor global.
- **Servicio en primer plano**: muestra el preset activo en una notificación y permite desactivar la ecualización sin abrir la app.
- **Actualizaciones integradas**: se comprueban únicamente al abrir la app, eligen el APK de tu arquitectura, verifican SHA-256 y abren el instalador del sistema.
- **Tema claro, oscuro o automático**.

## Requisitos

- Android 8.0 (API 26) o superior.
- Para instalar actualizaciones descargadas por la app, Android pedirá permitir la instalación de apps desconocidas para Mega Equalizer. La instalación siempre la confirma el usuario; no se puede instalar en silencio sin ser propietario del dispositivo.

## Instalación

Descarga el APK desde la página de [Releases](https://github.com/angelonmx/mega-equalizer/releases):

| Archivo | Arquitectura | Cuándo usarlo |
| --- | --- | --- |
| `mega-equalizer-1.1-arm64-v8a.apk` | ARM de 64 bits | La mayoría de teléfonos actuales |
| `mega-equalizer-1.1-armeabi-v7a.apk` | ARM de 32 bits | Teléfonos antiguos o de gama baja |
| `mega-equalizer-1.1-universal.apk` | ARM64, ARMv7 y x86_64 | Emuladores o si no estás seguro de tu arquitectura |

Puedes consultar la arquitectura de tu teléfono en **Ajustes → Acerca del teléfono → Información del procesador**, o instalar la versión universal.

### Actualizaciones entre arquitecturas

La app detecta su arquitectura real mediante la biblioteca nativa `libmega_abi.so`. Si instalaste la versión universal en un teléfono ARM64, la próxima actualización descargará automáticamente el APK `arm64-v8a`, no otro universal. Lo mismo ocurre con ARMv7.

Cada actualización se verifica antes de instalarse:

1. SHA-256 del archivo descargado;
2. nombre del paquete;
3. `versionCode` esperado.

### Verificar la firma

Todos los APK publicados se firman con la misma clave. Huella SHA-256 del certificado:

```text
4E:51:69:88:53:8B:3D:20:A8:FE:9B:F4:0A:14:ED:D4:E2:77:FE:EE:97:27:5A:AC:87:4F:0D:E4:95:57:87:17
```

También puedes comprobarlo con Android SDK Build Tools:

```bash
apksigner verify --print-certs mega-equalizer-1.1-arm64-v8a.apk
```

## Primeros pasos

1. Abre **Presets** y crea uno con el botón **Crear preset**.
2. Ajusta el preamp y las bandas. Los botones `−` y `+` permiten cambios precisos; los controles deslizantes son más rápidos.
3. Entra en **Dispositivos** y elige a qué salida se aplica el preset. También puedes asignarlo desde el editor del preset.
4. Vuelve al inicio y activa **Activar ecualización**.
5. Si la salida actual no tiene preset, el estado lo indicará y no se aplicará ningún efecto.

### Prioridad de asignaciones

```text
Dispositivo Bluetooth concreto
        ↓
Categoría Bluetooth
        ↓
Cualquier dispositivo
        ↓
Sin efecto
```

Una asignación concreta siempre gana. «Sin preset» en un nivel inferior no anula un preset de un nivel superior; para dejar una salida sin efecto, no asignes nada en ningún nivel que la afecte.

### Perfiles por aplicación

Los perfiles por aplicación son **opcionales y experimentales**:

1. Activa el servicio de accesibilidad de Mega Equalizer en los ajustes de Android.
2. Activa **Perfiles por aplicación** en la pantalla **Aplicaciones**.
3. Elige una app y asígnale un preset, «Usar el preset del dispositivo» o «No aplicar ningún efecto».

El servicio de accesibilidad solo recibe el nombre del paquete de la ventana activa y mantiene activo el motor global. No lee el contenido de la pantalla, no usa captura de pantalla y no envía información a Internet. El motor global se aplica a toda la mezcla, así que también cubre el audio de accesibilidad y TalkBack cuando el dispositivo lo permite.

## Cómo funciona el audio

Mega Equalizer intenta, en este orden:

1. **DynamicsProcessing** global de 10 bandas (Android 9+);
2. **Equalizer** del sistema Android sobre la mezcla global;
3. efectos de sesión para reproductores que abren sesiones de efectos de audio compatibles.

Los efectos globales se aplican a la mezcla completa del dispositivo: audio multimedia, notificaciones, llamadas y el canal de accesibilidad que usa TalkBack. Dependen del fabricante: algunos teléfonos limitan o bloquean los efectos sobre la mezcla global; en esos casos la app lo indica y puede seguir funcionando con reproductores compatibles. El ecualizador no puede procesar audio de apps que no exponen una sesión de audio al sistema.

## Privacidad

Mega Equalizer no recopila datos, no muestra anuncios y no requiere una cuenta. Todo se guarda localmente. La única conexión de red es la comprobación de actualizaciones al abrir la app, que consulta GitHub Releases. Más detalles en [PRIVACY.md](PRIVACY.md).

## Compilar

### Requisitos

- JDK 17.
- Android SDK Platform 36 y Build Tools recientes.
- Gradle se descarga automáticamente mediante el wrapper incluido.

### Depuración

```bash
./gradlew assembleDebug
```

El APK de depuración usa el sufijo de paquete `.debug` y puede convivir con la versión de release.

### Release firmado

Crea `keystore.properties` en la raíz (está ignorado por Git):

```properties
storeFile=C:/ruta/a/tu_firma.keystore
storePassword=TU_CONTRASEÑA
keyAlias=TU_ALIAS
keyPassword=TU_CONTRASEÑA_DE_CLAVE
```

O define las variables de entorno:

- `MEGA_KEYSTORE_FILE`
- `MEGA_KEYSTORE_PASSWORD`
- `MEGA_KEY_ALIAS`
- `MEGA_KEY_PASSWORD`

Después:

```bash
./gradlew assembleRelease
```

Salidas:

```text
app/build/outputs/apk/release/app-arm64-v8a-release.apk
app/build/outputs/apk/release/app-armeabi-v7a-release.apk
app/build/outputs/apk/release/app-universal-release.apk
```

### Bibliotecas nativas

`libmega_abi.so` solo identifica la arquitectura instalada. Los binarios precompilados ya están en `app/src/main/jniLibs/`. Para regenerarlos con el NDK:

```powershell
./scripts/build-native-libs.ps1
```

## Publicar una versión

1. Actualiza `VERSION_NAME` y `VERSION_CODE` en `version.properties`.
2. Actualiza `CHANGELOG.md` y `RELEASE_NOTES.md`.
3. Crea un tag y súbelo:

```bash
git tag v1.1
git push origin v1.1
```

El workflow `.github/workflows/release.yml` compila y firma los tres APK, genera `version.json` con sus SHA-256 y publica el release en GitHub. La app consulta:

```text
https://github.com/angelonmx/mega-equalizer/releases/latest/download/version.json
```

Secretos necesarios en GitHub Actions:

- `MEGA_KEYSTORE_BASE64`: keystore codificado en base64;
- `MEGA_KEYSTORE_PASSWORD`;
- `MEGA_KEY_ALIAS`;
- `MEGA_KEY_PASSWORD`.

El keystore y las contraseñas nunca se guardan en el repositorio.

## Estructura del proyecto

```text
app/src/main/java/com/angelonmx/megaequalizer/
├── audio/          Detección de rutas y motor de efectos
├── data/           Presets, asignaciones, perfiles y estado
├── service/        Servicio en primer plano
├── accessibility/  Detección del paquete en primer plano y vigilancia del motor
├── update/         Comprobación, descarga y verificación de APK
├── ui/             Adaptadores y controles accesibles
└── util/           Accesibilidad, hash y ABI
app/src/main/assets/ Texto completo de la licencia GPL v3
scripts/             Metadatos del release y bibliotecas nativas
```

## Licencia

Mega Equalizer se distribuye bajo la **GNU General Public License, versión 3 o posterior** (GPLv3). Consulta [LICENSE](LICENSE).

## Aviso

Mega Equalizer no está afiliado a Google ni a Android. «Android» es una marca de Google LLC.
