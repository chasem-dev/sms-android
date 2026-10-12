#!/usr/bin/env bash
# Build the debug APK. Extra arguments go to Gradle.
set -euo pipefail
root=$(cd "$(dirname "$0")" && pwd)
port_root=${SMS_PORT_ROOT:-$root/sms-port}
sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[[ -n "$sdk_root" ]] || { echo 'Set ANDROID_HOME to your Android SDK.' >&2; exit 1; }
export ANDROID_HOME="$sdk_root" ANDROID_SDK_ROOT="$sdk_root"
if [[ -z ${SMS_PORT_ROOT:-} ]]; then git -C "$root" submodule update --init sms-port; fi
[[ -f "$port_root/android/build-native.sh" ]] || { echo 'Set SMS_PORT_ROOT to the Android port checkout.' >&2; exit 1; }
"$port_root/android/build-native.sh" --prepare
cd "$root"
./gradlew :app:assembleDebug "-PsmsPortRoot=$port_root" "${@}"
