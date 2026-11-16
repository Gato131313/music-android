#!/usr/bin/env bash
# Corre dentro del emulador de GitHub Actions: carga la biblioteca de prueba, instala la app,
# ejecuta el recorrido con capturas y junta informes en out/.
set -x
PKG=io.github.gato131313.music.debug
OUT=out
mkdir -p $OUT
adb wait-for-device
adb shell getprop ro.build.version.release
for d in tools/testlib/*; do adb push "$d" /sdcard/ >/dev/null; done
adb shell content call --uri content://media --method scan_volume --arg external_primary || true
sleep 25
echo "audio en MediaStore: $(adb shell content query --uri content://media/external/audio/media --projection _id | grep -c Row)" | tee $OUT/mediastore.txt
echo "video en MediaStore: $(adb shell content query --uri content://media/external/video/media --projection _id | grep -c Row)" | tee -a $OUT/mediastore.txt
adb install -r -g apks/debug/app-debug.apk
adb install -r -g apks/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb shell am instrument -w -e class io.github.gato131313.music.CapturasTest $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee $OUT/instrument.txt
adb pull /data/local/tmp/capturas $OUT/ || true
adb pull /sdcard/Android/data/$PKG/files/informe.txt $OUT/informe.txt || true
adb logcat -d -s CAPTURAS > $OUT/capturas_log.txt
adb logcat -d > $OUT/logcat.txt
adb shell dumpsys meminfo $PKG > $OUT/meminfo.txt || true
adb shell dumpsys batterystats $PKG > $OUT/batterystats.txt || true
exit 0
