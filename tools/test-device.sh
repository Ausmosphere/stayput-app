#!/usr/bin/env sh
# Use only with a disposable emulator; enables the app's blocking permissions for the test.
set -eu
cd "$(dirname "$0")/.."
ADB=${ADB:-adb}
mkdir -p artifacts
"$ADB" wait-for-device
attempt=0
until [ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do
    attempt=$((attempt+1))
    if [ "$attempt" -gt 90 ]; then echo 'Emulator did not finish booting.'; exit 1; fi
    sleep 2
done
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$ADB" shell appops set app.stayput SYSTEM_ALERT_WINDOW allow
API=$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$API" -ge 33 ]; then "$ADB" shell pm grant app.stayput android.permission.POST_NOTIFICATIONS; fi
"$ADB" shell settings put secure enabled_accessibility_services app.stayput/app.stayput.FocusAccessibilityService
"$ADB" shell settings put secure accessibility_enabled 1
"$ADB" shell input keyevent KEYCODE_WAKEUP
"$ADB" shell wm dismiss-keyguard
"$ADB" shell am instrument -w app.stayput.test/app.stayput.SmokeInstrumentation > artifacts/device-tests.txt
cat artifacts/device-tests.txt
"$ADB" pull /sdcard/Android/data/app.stayput/files/screenshots artifacts/
if ! grep -Eq 'PASS: [0-9]+ device integration checks' artifacts/device-tests.txt; then exit 1; fi
