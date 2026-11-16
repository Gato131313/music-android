# MUSIC para Android — NOTAS

App nativa para el hermano de Yael (Android 8+, ~1 100 canciones y ~650 videos en su teléfono). Sin cuenta y sin nube.
Repositorio PÚBLICO `Gato131313/music-android` (aprobado por Yael el 16 nov 2026): permite el emulador gratis en
GitHub Actions y "Buscar actualizaciones" sin claves.

## Reglas
- Trabajar SOLO en `C:\GitHub\music-android`.
- yum-music (`C:\Users\Asus\Documentos\yum-music`) solo se LEE, salvo el endpoint público de letras.
- Yael no hace pruebas ni usa terminal: todo se verifica con pruebas automáticas y el emulador de GitHub Actions.
- Responder en español. Commit por fase.

## Entorno
- Android SDK en `C:\Android\sdk` (platform 35, build-tools 35). Gradle 8.11.1 (wrapper). JDK 17 en `C:\Program Files\Java\jdk-17`.
- Compilar local: `JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew testDebugUnitTest assembleDebug`.
- **El emulador NO corre en esta PC**: la virtualización está apagada en la BIOS. La verificación visual corre en
  GitHub Actions (`.github/workflows/android.yml`, job `emulador`, Android 14 x86_64 con KVM):
  1. `tools/make_testlib.py` genera ~1 780 archivos con nombres sucios tipo Snaptube (mp3 con ID3 sucio y portadas,
     m4a, flac, ogg, wav, mp4 en pareja y sueltos, videos de cámara, notas de voz que deben ignorarse).
  2. `tools/ci_emulator.sh` espera a que el almacenamiento acepte archivos, los sube, instala la app y corre `CapturasTest`.
  3. Artefacto `capturas`: PNG de cada pantalla, `informes/informeN.txt` (uno por fase), logcat, meminfo.
  Descargar: `gh run download <id> -n capturas -D capturas-tmp/<carpeta>` (capturas-tmp está en .gitignore).
- Ojo: en el emulador de CI a veces sale "Pixel Launcher isn't responding"; la prueba lo cierra sola (Wait).

## Arquitectura
- Kotlin 2.1 + Compose (BOM 2024.12.01) + Media3 1.5.1 + Room 2.6.1 + DataStore + Coil 2.7 + Haze 1.1.1 + Glance 1.1.1.
  (Versiones más nuevas existen —AGP 9, Compose 2026— pero se eligió una combinación estable conocida.)
- `data/clean/NameCleaner.kt`: limpieza de nombres (Kotlin puro). Memoria en disco `files/limpieza.tsv`;
  subir `RULES_VERSION` al cambiar reglas. Pruebas: `NameCleanerTest`, `PerfProbeTest`.
- `data/clean/LibraryBuilder.kt`: une artistas por clave (sin mayúsculas/acentos/signos/sufijos; "&"/"and"/"y" = "n"),
  elige el mejor nombre, detecta dudas, empareja Canción+Video (mismo artista+título limpios, duración ±max(15 s, 12 %)).
  3 000 archivos: ~350 ms en frío, ~120 ms al reconstruir (JVM).
- `data/LibraryRepository.kt`: caché Room del escaneo + MediaStore + ContentObserver + "Escanear medios".
- Datos del usuario por clave = ruta del archivo en minúsculas. Nunca se modifican archivos (solo "Borrar",
  que pide confirmación del sistema).
- Reproducción: `playback/PlaybackService.kt` (MediaSessionService; notificación, bloqueo, Bluetooth, reanudación,
  conteo de reproducciones como MUSIC web, fusión con segundo reproductor), `PlayerController` (MediaController → UI).
- Efectos: `fx/FxEngine.kt` = procesador propio dentro de ExoPlayer (EQ 10 bandas biquad, graves low-shelf, 3D M/S,
  reverberación tipo Freeverb, volumen extra, normalizador, limitador) + FFT para el anillo de barras.
  `FxController` elige: preset de la canción > preset de la salida (audífonos/bocina/Bluetooth) > global.
  Pruebas: `FxProcessorTest` (mide la respuesta real).
- Letras: `lyrics/Lyrics.kt` pide a `https://yum-music.vercel.app/api/public/lyrics` y guarda en Room.
- Respaldo/importar: `data/backup/` (CSV de la app anterior, respaldo JSON, copia diaria en Descargas/MUSIC).
- Actualizaciones: `update/UpdateChecker.kt` (GitHub Releases `latest`, asset .apk, instalador de Android).

## Endpoint público de letras (en yum-music)
- `web/src/app/api/public/lyrics/route.ts` + `web/src/lib/publiclyrics.ts` + prueba `web/e2e/letras-publicas.spec.ts`.
- GET `?title&artist&album&duration&device&translate=1`. Sin sesión. Solo devuelve letra y traducción; no toca tablas de cuentas.
- Tablas propias creadas al usarse: `public_lyrics` (caché compartida) y `public_hits` (contador por día).
- Límites (variables opcionales en Vercel): 600 peticiones/día por dispositivo, 1 500 por IP;
  Gemini: 40/día por dispositivo, 80 por IP, 150 en total. Sin cupo: traductor básico gratuito y se mejora después.
  Respeta la cuota diaria compartida de Gemini (`geminiRestUntil` / `markDayOut`).

## Firma y entrega
- Llave: `C:\Users\Asus\.music-android-keys\music-release.jks` + `keystore.properties` (contraseña aleatoria).
  FUERA del repositorio. Alias `music`. SHA-256 5E:7D:1A:0C:…:DC:53:FC:6F. **Hacer copia de esa carpeta** (USB o Drive
  privado): sin ella no se pueden publicar actualizaciones que se instalen encima.
- Release: `./gradlew assembleRelease -PversionName=X.Y.Z -PversionCode=N` y `gh release create vX.Y.Z MUSIC-X.Y.Z.apk`.
  El versionCode SIEMPRE debe subir.

## Estado por fase (16 nov 2026)
- Fase 1 ✔ base, escaneo, biblioteca, limpieza (+ optimización 17 s → <1 s).
- Fase 2 ✔ reproductor y segundo plano.
- Fase 3 ✔ efectos.
- Fase 4 ✔ Inicio, listas, menús, temas, ajustes, ocultos.
- Fase 5 ✔ portadas (disco/cuadrada/aurora), letras, modo conductor, widget, estadísticas, Canción+Video.
- Fase 6 ✔ importar CSV, respaldo, actualizaciones, APK firmado.
