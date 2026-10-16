#!/usr/bin/env bash
# Deploy Kartrid ke KPA (atau device adb apa pun yang nyambung).
#   ./deploy.sh                 -> build + install + jalanin
#   ./deploy.sh connect <ip:port>  -> adb connect dulu (wireless debugging)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/android/env.sh"

if [ "${1:-}" = "connect" ]; then
  adb connect "${2:?pakai: ./deploy.sh connect 192.168.x.x:5555}"
  adb devices -l
  exit 0
fi

cd "$HERE/android"
bash ./gradlew :app:assembleDebug --console=plain -q
APK="app/build/outputs/apk/debug/app-debug.apk"
ls -la "$APK"

if ! adb devices | grep -qw device; then
  echo "!! nggak ada device adb yang kedeteksi. Jalankan: $0 connect <ip:port>"
  adb devices -l
  exit 1
fi

adb install -r "$APK"
adb shell am start -n id.iqbaldev.kartrid/.MainActivity
echo "== terpasang. Screenshot: adb exec-out screencap -p > shot.png =="
