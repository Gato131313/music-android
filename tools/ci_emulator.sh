#!/usr/bin/env bash
# Corre dentro del emulador de GitHub Actions: carga la biblioteca de prueba, instala la app,
# ejecuta el recorrido con capturas y junta informes en out/.
set -x
PKG=io.github.gato131313.music.debug
OUT=out
mkdir -p $OUT
adb wait-for-device
adb shell getprop ro.build.version.release
# Esperar a que el almacenamiento compartido acepte archivos (tras el arranque tarda un poco).
for i in $(seq 1 60); do
  if adb shell "touch /sdcard/Download/.probe && rm /sdcard/Download/.probe" 2>/dev/null; then echo "almacenamiento listo ($i)"; break; fi
  sleep 3
done
adb shell wm dismiss-keyguard || true
adb shell settings put global window_animation_scale 0.5 || true
adb shell settings put global transition_animation_scale 0.5 || true
for d in tools/testlib/*; do adb push "$d" /sdcard/ | tail -1; done
adb shell content call --uri content://media --method scan_volume --arg external_primary || true
sleep 25
echo "audio en MediaStore: $(adb shell content query --uri content://media/external/audio/media --projection _id | grep -c Row)" | tee $OUT/mediastore.txt
echo "video en MediaStore: $(adb shell content query --uri content://media/external/video/media --projection _id | grep -c Row)" | tee -a $OUT/mediastore.txt
adb install -r -g apks/debug/app-debug.apk
adb install -r -g apks/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb shell am instrument -w -e class io.github.gato131313.music.CapturasTest $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee $OUT/instrument.txt
adb pull /data/local/tmp/capturas $OUT/ || true
adb pull /sdcard/Android/data/$PKG/files/ $OUT/informes || true
adb logcat -d -s CAPTURAS > $OUT/capturas_log.txt
adb logcat -d > $OUT/logcat.txt
adb shell dumpsys meminfo $PKG > $OUT/meminfo.txt || true
adb shell dumpsys batterystats $PKG > $OUT/batterystats.txt || true

# ---- Rendimiento con la versión SIN depuración (igual al APK final) ----
B=io.github.gato131313.music.bench
ACT=$B/io.github.gato131313.music.MainActivity
adb install -r -g apks/benchmark/app-benchmark.apk
adb shell am force-stop $PKG
adb logcat -c
adb shell am start -W -n $ACT > $OUT/arranque_1_primera_vez.txt
sleep 30
adb shell am force-stop $B
adb shell am start -W -n $ACT > $OUT/arranque_2_en_frio.txt
sleep 10
adb logcat -d -s Library:I > $OUT/biblioteca_release.txt
mide () {
  adb shell am start -n $ACT --es route "$1" > /dev/null
  sleep 6
  adb shell dumpsys gfxinfo $B reset > /dev/null
  for i in $(seq 1 14); do adb shell input swipe 540 1900 540 600 150; sleep 0.5; done
  for i in $(seq 1 4); do adb shell input swipe 540 700 540 1900 150; sleep 0.5; done
  adb shell dumpsys gfxinfo $B | grep -E "Total frames|Janky|percentile|Slow UI|deadline" > $OUT/fluidez_$2.txt
}
mide songs canciones
mide artists artistas
adb shell am start -n $ACT --es estilo solido > /dev/null; sleep 3
mide songs canciones_solido
adb shell am start -n $ACT --es estilo music > /dev/null; sleep 3
adb shell am start -n $ACT > /dev/null; sleep 4
adb shell dumpsys gfxinfo $B reset > /dev/null
for i in $(seq 1 8); do adb shell input swipe 540 1900 540 700 150; sleep 0.5; adb shell input swipe 540 700 540 1900 150; sleep 0.5; done
adb shell dumpsys gfxinfo $B | grep -E "Total frames|Janky|percentile|Slow UI|deadline" > $OUT/fluidez_inicio.txt
adb shell dumpsys meminfo $B | grep -E "Java Heap|Native Heap|Code:|Private Other|TOTAL PSS" > $OUT/memoria_release.txt
adb shell dumpsys batterystats --charged $B | grep -iE "cpu|wake" | head -20 > $OUT/bateria_release.txt || true
exit 0
