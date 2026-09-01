#!/bin/bash
# Double-clickable launcher: boots the LogEZ_Pixel7 Android emulator (or reuses a
# running one) and opens logEZ on it. Safe to re-run anytime; see docs/EMULATOR.md.

set -u
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
AVD="LogEZ_Pixel7"
APP_ID="com.enil.logez"

if [ ! -x "$ADB" ] || [ ! -x "$EMULATOR" ]; then
    echo "✗ Android SDK not found at $ANDROID_HOME — install it or set ANDROID_HOME."
    exit 1
fi

"$ADB" start-server >/dev/null 2>&1

running_serial=""
for serial in $("$ADB" devices | awk '/^emulator-/{print $1}'); do
    name=$("$ADB" -s "$serial" emu avd name 2>/dev/null | head -1 | tr -d '\r')
    if [ "$name" = "$AVD" ]; then
        running_serial="$serial"
        break
    fi
done

if [ -n "$running_serial" ]; then
    echo "✓ $AVD is already running ($running_serial) — reusing it."
else
    echo "▸ Starting the $AVD emulator (a phone window will appear)..."
    nohup "$EMULATOR" -avd "$AVD" >/tmp/logez-emulator.log 2>&1 &
    disown
fi

echo "▸ Waiting for Android to finish booting..."
booted=""
for _ in $(seq 1 120); do
    if [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
        booted=1
        break
    fi
    sleep 3
done
if [ -z "$booted" ]; then
    echo "✗ The emulator didn't finish booting after 6 minutes. See /tmp/logez-emulator.log,"
    echo "  or open Android Studio → Device Manager and cold-boot $AVD from there."
    exit 1
fi
echo "✓ Android is up."

if ! "$ADB" shell pm path "$APP_ID" >/dev/null 2>&1; then
    echo "✗ logEZ isn't installed on this emulator yet. From the repo root, run:"
    echo "    ./gradlew :app:installDebug"
    exit 1
fi

echo "▸ Opening logEZ..."
"$ADB" shell am start -n "$APP_ID/.MainActivity" >/dev/null 2>&1
echo "✓ Done — logEZ is on screen. Click to tap, click-drag to scroll."
