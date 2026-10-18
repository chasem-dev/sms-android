#!/usr/bin/env bash
# Build, align and sign the release APKs into dist/: Super Mario Sunshine's
# and Super Mario Eclipse's. SMS_ANDROID_PREVIEW
# overrides gradle.properties' preview number; extra arguments go to Gradle.
# Pushes to main publish releases through .github/workflows/release.yml.
set -euo pipefail
root=$(cd "$(dirname "$0")" && pwd)
cd "$root"

# Android only installs an update signed with the same certificate as the
# installed app. Previews 1-17 used this one; signing with another key would
# force testers to uninstall, losing their disc and saves.
expected_cert=${SMS_ANDROID_CERT_SHA256:-9eff857ce48cfed822b462a4aa31df7b88e7856737bfe00fac6425fdd3740323}
keystore=${SMS_ANDROID_KEYSTORE:-$HOME/.android/debug.keystore}
key_alias=${SMS_ANDROID_KEY_ALIAS:-androiddebugkey}
export SMS_ANDROID_KEYSTORE_PASS=${SMS_ANDROID_KEYSTORE_PASS:-android}

sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[[ -n "$sdk_root" ]] || { echo 'Set ANDROID_HOME to your Android SDK.' >&2; exit 1; }
export ANDROID_HOME="$sdk_root" ANDROID_SDK_ROOT="$sdk_root"
build_tools="$sdk_root/build-tools/36.0.0"
[[ -f "$keystore" ]] || { echo "Signing keystore not found: $keystore" >&2; exit 1; }

git submodule update --init sms-port
if [[ -n $(git status --porcelain --untracked-files=no) ]] || ! git -C sms-port diff --quiet HEAD --; then
  echo 'Commit your changes first, so the APK names clean source revisions.' >&2; exit 1
fi

prop() { sed -n "s/^$1=//p" gradle.properties; }
version=$(prop smsAndroidVersion)
preview=${SMS_ANDROID_PREVIEW:-$(prop smsAndroidPreview)}
name=$version
[[ $preview == 0 ]] || name="$version-preview.$preview"

sms-port/android/build-native.sh --prepare
./gradlew :app:assembleSunshineRelease :app:assembleEclipseRelease "-PsmsPortRoot=$root/sms-port" "-PsmsAndroidPreview=$preview" "${@}"

mkdir -p dist
# flavor, APK name
for build in "sunshine sms-android-$name-arm64.apk" "eclipse sms-android-eclipse-$name-arm64.apk"; do
  read -r flavor file <<< "$build"
  apk="dist/$file"
  "$build_tools/zipalign" -P 16 -f 4 "app/build/outputs/apk/$flavor/release/app-$flavor-release-unsigned.apk" "$apk.aligned"
  "$build_tools/apksigner" sign --ks "$keystore" --ks-key-alias "$key_alias" \
    --ks-pass env:SMS_ANDROID_KEYSTORE_PASS --key-pass env:SMS_ANDROID_KEYSTORE_PASS --out "$apk" "$apk.aligned"
  rm -f "$apk.aligned" "$apk.idsig"
  cert=$("$build_tools/apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
  if [[ $cert != "$expected_cert" ]]; then
    rm -f "$apk"
    echo "Signed with certificate $cert, not the previews' $expected_cert." >&2; exit 1
  fi
  (cd dist && sha256sum "$file" > "$file.sha256")
  echo "Built $apk"
  cat "$apk.sha256"
done
