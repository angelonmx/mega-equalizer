# Mega Equalizer 1.5.1

Esta versión añade un nuevo modo **"Todas las sesiones"** para ecualizar también accesibilidad (TalkBack), notificaciones, alarmas y cualquier otro canal de audio que el efecto global no cubra en tu dispositivo.

## Lo más importante

- **Nuevo modo "Todas las sesiones"**: desactiva el efecto global y aplica ecualizador a cada sesión de audio detectada, incluyendo accesibilidad/TalkBack, notificaciones, alarmas y cualquier otro canal. Es el modo más completo para dispositivos donde el efecto global no llega a ciertos volúmenes.
- **Modo "Solo llamadas y canales omitidos"**: desactiva el efecto global y aplica ecualizador solo a llamadas, accesibilidad, notificaciones y alarmas.
- **Modo Automático (recomendado)**: mantiene el efecto global para multimedia y añade efectos por sesión solo a las sesiones que suelen quedar fuera.
- **Sin ADB**: solo quedan Shizuku (preferido, sin root) y root como métodos de acceso para descubrir sesiones.
- **Actualizador corregido**: comprueba actualizaciones cada vez que se abre la interfaz y la descarga funciona con fallback de visibilidad.

## ¿Por qué no ecualiza accesibilidad y otros volúmenes?

En muchos dispositivos Android, el ecualizador global (session 0) no se aplica realmente a accesibilidad (TalkBack), notificaciones y alarmas porque esos flujos de audio se enrutan por separado. Además, las apps de terceros no siempre pueden crear efectos de audio en sesiones del sistema.

Si el modo Automático no ecualiza accesibilidad, notificaciones o alarmas en tu equipo, abre **Ajustes → Cobertura total** y elige **"Todas las sesiones"**. Esto desactiva el efecto global e intenta aplicar ecualizador a cada sesión detectada, incluyendo esos canales.

## Cómo activar la cobertura total

1. Abre **Ajustes → Cobertura total**.
2. Activa **Cobertura total** y elige el modo:
   - **Automático**: efecto global para multimedia + efectos por sesión a canales omitidos.
   - **Solo llamadas y canales omitidos**: sin efecto global, ecualizador solo a llamadas, accesibilidad, notificaciones y alarmas.
   - **Todas las sesiones**: sin efecto global, ecualizador a cada sesión detectada (incluyendo accesibilidad/TalkBack, notificaciones, alarmas).
3. Elige el método de acceso:
   - **Shizuku (sin root)**: instala Shizuku desde GitHub (última versión), ábrelo, actívalo y pulsa «Solicitar permiso».
   - **Root (avanzado)**: pulsa «Probar acceso root».
4. El estado mostrará las sesiones activas y cuántas tienen ecualizador.

## Requisitos y límites

- **Android 8.0 (API 27) o superior** hasta API 37.
- La cobertura total depende de que Android y el fabricante permitan crear efectos sobre cada sesión. Algunos dispositivos bloquean efectos en sesiones de accesibilidad, notificaciones y alarmas; en esos casos incluso el modo "Todas las sesiones" no podrá ecualizarlas.
- Ninguna app normal puede autoconcederse `MODIFY_AUDIO_ROUTING`; Shizuku o root son las vías privilegiadas. Mega Equalizer nunca eleva privilegios por su cuenta.
- Los sonidos que no crean una sesión de audio propia (algunos tonos del sistema) y los sonidos muy cortos pueden quedar sin ecualizar.
- La cobertura total es opcional. Sin ella, la app sigue funcionando exactamente como en 1.2 con el efecto global y los perfiles por salida.

## Actualización desde 1.5

`versionCode` 1006. La app comprueba la actualización al abrirse, elige el APK de la arquitectura real del dispositivo y verifica el SHA-256, el nombre del paquete y el `versionCode` antes de ofrecer la instalación. Los presets, asignaciones, dispositivos y ajustes existentes se conservan.

## APK incluidos

| Archivo | Arquitectura |
| --- | --- |
| `mega-equalizer-1.5.1-arm64-v8a.apk` | ARM de 64 bits (recomendado) |
| `mega-equalizer-1.5.1-armeabi-v7a.apk` | ARM de 32 bits |
| `mega-equalizer-1.5.1-universal.apk` | ARM64, ARMv7 y x86_64 |

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
