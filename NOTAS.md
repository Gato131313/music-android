# MUSIC para Android — NOTAS

App nativa para el hermano de Yael (Android 8+, ~1 100 canciones y ~650 videos en su teléfono). Sin cuenta y sin nube.

## Reglas
- Trabajar SOLO en `C:\GitHub\music-android` (repo propio `Gato131313/music-android`).
- yum-music (`C:\Users\Asus\Documentos\yum-music`) solo se LEE, salvo el endpoint público de letras (fase 5).
- Yael no hace pruebas ni usa terminal: todo se verifica con pruebas automáticas y el emulador de GitHub Actions.
- Responder en español. Commit por fase.

## Entorno
- Android SDK en `C:\Android\sdk` (platform 35, build-tools 35). Gradle 8.11.1 (wrapper). JDK 17 en `C:\Program Files\Java\jdk-17`.
- Compilar local: `JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew testDebugUnitTest assembleDebug`.
- **El emulador NO corre en esta PC**: la virtualización está apagada en la BIOS ("Se habilitó la virtualización en el firmware: No").
  Por eso la verificación visual corre en GitHub Actions (`.github/workflows/android.yml`, job `emulador`, Android 14 x86_64 con KVM):
  1. `tools/make_testlib.py` genera ~1 780 archivos con nombres sucios tipo Snaptube (mp3 con ID3 sucio, m4a, flac, ogg, wav, mp4, notas de voz que deben ignorarse).
  2. `tools/ci_emulator.sh` los sube al emulador, instala la app y corre `CapturasTest` (UiAutomator).
  3. Artefacto `capturas`: PNG de cada pantalla, `informe.txt` (rendimiento gfxinfo, tiempos), logcat, meminfo.
  Descargar: `gh run download <id> -n capturas -D capturas-tmp/<fase>`.

## Arquitectura
- Kotlin 2.1 + Compose (BOM 2024.12.01) + Media3 1.5.1 + Room 2.6.1 + DataStore + Coil 2.7 + Haze 1.1.1 (cristal líquido).
- `data/clean/NameCleaner.kt`: limpieza de nombres (Kotlin puro, pruebas en `NameCleanerTest`).
- `data/clean/LibraryBuilder.kt`: une artistas por clave (sin mayúsculas/acentos/signos/sufijos), elige el mejor nombre, detecta dudas, empareja Canción+Video (mismo artista+título limpios, duración ±max(15 s, 12 %)).
- `data/LibraryRepository.kt`: caché Room del escaneo (arranque instantáneo) + MediaStore + ContentObserver + "Escanear medios".
- Datos del usuario por clave = ruta del archivo en minúsculas: ediciones, alias de artistas, rechazos, decisiones de separar, ocultos, favoritos, estadísticas, playlists.
- Nunca se modifican los archivos del teléfono (salvo "Borrar", que pide confirmación del sistema).

## Estado por fase
- Fase 1 (proyecto base, escaneo, biblioteca, limpieza): en curso.
