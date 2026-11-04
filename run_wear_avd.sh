#!/usr/bin/env bash
set -e

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"
AVD_NAME="${AVD_NAME:-Wear_OS_Large_Round_API_34}"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

cd "$PROJECT_DIR"

echo "==> [1/4] Building debug APK (assembleDebug)..."
./gradlew assembleDebug

# Check if any Wear OS emulator or device is already connected
SERIAL=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')

if [ -z "$SERIAL" ]; then
    echo "==> [2/4] Starting Wear OS AVD: $AVD_NAME..."
    nohup "$EMULATOR" -avd "$AVD_NAME" -gpu auto >/tmp/wear_avd.log 2>&1 &
    echo "==> Waiting for ADB device connection..."
    "$ADB" wait-for-device
    SERIAL=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')
else
    echo "==> [2/4] Connected device/emulator detected: $SERIAL"
fi

echo "==> [3/4] Waiting for Wear OS boot completion ($SERIAL)..."
while [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 2
done

echo "==> [4/4] Installing and launching BARQ! Wear OS on $SERIAL..."
"$ADB" -s "$SERIAL" install -r "$APK_PATH"
"$ADB" -s "$SERIAL" shell am start -n "app.barq.wear/.MainActivity"

echo "==> Done! BARQ! Wear OS is running on $SERIAL."
