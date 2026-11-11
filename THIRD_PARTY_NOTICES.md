# Avisos de terceros

Mega Equalizer incluye las siguientes bibliotecas de terceros. Cada una se usa bajo su propia licencia. Los textos completos de las licencias que lo requieren se incluyen dentro de la app, en **Ajustes → Licencias**, y en `app/src/main/assets/third-party-notices.txt`.

| Componente | Titular | Licencia | Enlace |
| --- | --- | --- | --- |
| LibADB Android 3.1.1 | Copyright 2021 Muntashir Al-Islam | GPL-3.0-or-later o Apache-2.0; Mega Equalizer usa la opción GPL-3.0-or-later | [libadb-android](https://github.com/MuntashirAkon/libadb-android) |
| ADB stream (parte de LibADB) | Copyright 2013 Cameron Gutman | BSD-3-Clause | [AdbLib](https://github.com/cgutman/AdbLib) |
| SPAKE2-Java 2.2.1 | Copyright 2021 Muntashir Al-Islam | LGPL-3.0 | [spake2-java](https://github.com/MuntashirAkon/spake2-java) |
| Bouncy Castle 1.81 (`bcprov-jdk15to18`, `bcpkix-jdk15to18`, `bcutil-jdk15to18`) | Copyright (c) 2000-2026 The Legion of the Bouncy Castle Inc. | Bouncy Castle Licence (equivalente a MIT) | [bouncycastle.org](https://www.bouncycastle.org) |
| AndroidX AppCompat, Core, RecyclerView y Annotation | Copyright The Android Open Source Project | Apache-2.0 | [AndroidX](https://developer.android.com/jetpack/androidx) |
| Material Components for Android | Copyright Google LLC | Apache-2.0 | [material-components-android](https://github.com/material-components/material-components-android) |
| Shizuku API 13.1.5 | Copyright Rikka | Licencia propia de Shizuku (ver repositorio) | [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) |
| Shizuku Provider 13.1.5 | Copyright Rikka | Licencia propia de Shizuku (ver repositorio) | [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) |

## Notas

- LibADB se distribuye con licencia dual GPL-3.0-or-later o Apache-2.0. Mega Equalizer, que también es GPL-3.0-or-later, usa la primera opción.
- SPAKE2-Java se usa como biblioteca enlazada y no se modifica.
- La implementación de ED25519 de SPAKE2-Java deriva de EdDSA-Java, distribuida bajo CC0.
- Shizuku es una herramienta externa opcional para descubrir sesiones de audio; no es parte de Mega Equalizer y se distribuye bajo su propia licencia.
- El texto completo de la GPL v3 de Mega Equalizer está en [LICENSE](LICENSE). Mega Equalizer se distribuye bajo GNU GPL v3 o posterior.
