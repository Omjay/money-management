#!/usr/bin/env bash
set -euo pipefail

APK=.github/project/app/build/outputs/apk/debug/app-debug.apk
OUT=.github/project/build/demo-screenshots
mkdir -p "$OUT"
adb install "$APK"
adb shell am start -n com.bhaipaisa.moneymanager.debug/com.bhaipaisa.moneymanager.MainActivity --ez hisaab_synthetic_demo true
sleep 5

size="$(adb shell wm size | tr -d '\r' | sed -n 's/^Physical size: //p')"
width="${size%x*}"
height="${size#*x}"
test -n "$width" && test -n "$height"
nav_y=$((height - 150))

for entry in 'home:0' 'money:1' 'cards:2' 'people:3' 'insights:4'; do
  name="${entry%:*}"
  index="${entry#*:}"
  x=$(((2 * index + 1) * width / 10))
  adb shell input tap "$x" "$nav_y"
  sleep 2
  adb shell screencap -p "/sdcard/${name}.png"
  adb pull "/sdcard/${name}.png" "$OUT/${name}.png" >/dev/null
done

# The public release is separate from the debug demo. Smoke-test installation
# and launch without attempting to bypass its real device-lock requirement.
if [ -f Hisaab.apk ]; then
  adb install Hisaab.apk
  adb shell am start -n com.bhaipaisa.moneymanager/com.bhaipaisa.moneymanager.MainActivity
  sleep 3
  adb shell pidof com.bhaipaisa.moneymanager >/dev/null
fi
