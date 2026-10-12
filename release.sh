#!/usr/bin/env bash
# Build, align and sign the release APK into dist/. With --publish, also
# create the GitHub pre-release with the version's CHANGELOG.md section.
set -euo pipefail
root=$(cd "$(dirname "$0")" && pwd)
cd "$root"
publish=0
[[ ${1:-} == --publish ]] && publish=1

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
preview=$(prop smsAndroidPreview)
name=$version
[[ $preview == 0 ]] || name="$version-preview.$preview"
tag="v$name"
notes=$(awk -v h="## $name" '$0 == h { on = 1; next } on && /^## / { exit } on' CHANGELOG.md | sed '/./,$!d')
if (( publish )) && [[ -z "$notes" ]]; then
  echo "CHANGELOG.md has no \"## $name\" section to publish." >&2; exit 1
fi

sms-port/android/build-native.sh --prepare
./gradlew :app:assembleRelease "-PsmsPortRoot=$root/sms-port"

mkdir -p dist
apk="dist/sms-android-$name-arm64.apk"
"$build_tools/zipalign" -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk "$apk.aligned"
"$build_tools/apksigner" sign --ks "$keystore" --ks-key-alias "$key_alias" \
  --ks-pass env:SMS_ANDROID_KEYSTORE_PASS --key-pass env:SMS_ANDROID_KEYSTORE_PASS --out "$apk" "$apk.aligned"
rm -f "$apk.aligned" "$apk.idsig"
cert=$("$build_tools/apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
if [[ $cert != "$expected_cert" ]]; then
  rm -f "$apk"
  echo "Signed with certificate $cert, not the previews' $expected_cert." >&2; exit 1
fi
(cd dist && sha256sum "$(basename "$apk")" > "$(basename "$apk").sha256")
echo "Built $apk"
cat "$apk.sha256"

if (( publish )); then
  gh release create "$tag" "$apk" "$apk.sha256" --repo chasem-dev/sms-android \
    --prerelease --target "$(git rev-parse HEAD)" --title "Android preview $preview ($name)" --notes "$notes"
fi
