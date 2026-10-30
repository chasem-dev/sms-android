#!/usr/bin/env bash
# Build the debug APK. SMS_ECLIPSE=1 builds the Super Mario Eclipse app
# instead. Extra arguments go to Gradle. Finds the Android SDK, or sets one up
# in Android Studio's default place, and installs the SDK packages it needs.
set -euo pipefail
root=$(cd "$(dirname "$0")" && pwd)
port_root=${SMS_PORT_ROOT:-$root/sms-port}
case $(uname -s) in
  MINGW* | MSYS* | CYGWIN*) echo 'On Windows, build in WSL; the README shows how.' >&2; exit 1 ;;
esac
missing=()
for tool in git python3 curl tar unzip patch; do command -v "$tool" > /dev/null || missing+=("$tool"); done
if [[ ${#missing[@]} -gt 0 ]]; then
  echo "Install these first: ${missing[*]}" >&2
  echo '(On Ubuntu or WSL: sudo apt install openjdk-17-jdk python3 git curl unzip patch)' >&2; exit 1
fi

# Java: JAVA_HOME, the one on PATH, or Android Studio's.
if [[ -z ${JAVA_HOME:-} ]] && ! command -v java > /dev/null; then
  for jbr in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" /opt/android-studio/jbr \
      "$HOME/android-studio/jbr" /snap/android-studio/current/jbr; do
    if [[ -x $jbr/bin/java ]]; then export JAVA_HOME=$jbr; break; fi
  done
fi
java=${JAVA_HOME:+$JAVA_HOME/bin/}java
command -v "$java" > /dev/null || { echo 'Install a JDK, 17 or newer (on Ubuntu or WSL: sudo apt install openjdk-17-jdk), or Android Studio.' >&2; exit 1; }
java_major=$("$java" -version 2>&1 | sed -n 's/.* version "\([0-9]*\).*/\1/p' | head -n 1)
[[ ${java_major:-0} -ge 17 ]] || { echo "Java 17 or newer is needed; $java is ${java_major:-unknown}. Set JAVA_HOME to a newer JDK." >&2; exit 1; }

# The SDK: ANDROID_HOME, Android Studio's local.properties, or its default place.
sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [[ -z $sdk_root && -f $root/local.properties ]]; then sdk_root=$(sed -n 's/^sdk\.dir=//p' "$root/local.properties"); fi
if [[ -z $sdk_root ]]; then
  case $(uname -s) in Darwin) sdk_root=$HOME/Library/Android/sdk ;; *) sdk_root=$HOME/Android/Sdk ;; esac
fi
export ANDROID_HOME="$sdk_root" ANDROID_SDK_ROOT="$sdk_root"

packages=()
[[ -x $sdk_root/platform-tools/adb ]] || packages+=(platform-tools)
[[ -f $sdk_root/platforms/android-35/android.jar ]] || packages+=('platforms;android-35')
[[ -x $sdk_root/build-tools/36.0.0/zipalign ]] || packages+=('build-tools;36.0.0')
[[ -f $sdk_root/ndk/28.2.13676358/source.properties ]] || packages+=('ndk;28.2.13676358')
[[ -x $sdk_root/cmake/3.22.1/bin/cmake ]] || packages+=('cmake;3.22.1')
if [[ ${#packages[@]} -gt 0 ]]; then
  sdkmanager=
  for tools in "$sdk_root/cmdline-tools/latest" "$sdk_root"/cmdline-tools/*; do
    if [[ -x $tools/bin/sdkmanager ]]; then sdkmanager=$tools/bin/sdkmanager; break; fi
  done
  if [[ -z $sdkmanager ]]; then
    # Google's command-line tools 23.0; the checksums are from its SDK repository.
    case $(uname -s)-$(uname -m) in
      Linux-*) zip=commandlinetools-linux-16111833_latest.zip sha1=e025545c62a8e64c7559119566a569fb1dec5f60 ;;
      Darwin-arm64) zip=commandlinetools-mac_arm64-16111833_latest.zip sha1=ad03dc49bfacfd52c110b14104ea548b8a07e830 ;;
      Darwin-*) zip=commandlinetools-mac_x86_64-16111833_latest.zip sha1=112cf9618794a997ff273537d55bee02c22abffe ;;
      *) echo "Can't set up the Android SDK on $(uname -s); set ANDROID_HOME to one." >&2; exit 1 ;;
    esac
    echo "Setting up the Android SDK in $sdk_root"
    mkdir -p "$sdk_root/cmdline-tools"
    archive="$sdk_root/cmdline-tools/$zip"
    curl -fL --retry 3 -o "$archive.part" "https://dl.google.com/android/repository/$zip"
    sum=$( (sha1sum "$archive.part" 2> /dev/null || shasum -a 1 "$archive.part") | cut -d ' ' -f 1)
    [[ $sum == "$sha1" ]] || { rm -f "$archive.part"; echo "$zip failed its checksum." >&2; exit 1; }
    rm -rf "$sdk_root/cmdline-tools/unpack" "$sdk_root/cmdline-tools/latest"
    unzip -q "$archive.part" -d "$sdk_root/cmdline-tools/unpack"
    mv "$sdk_root/cmdline-tools/unpack/cmdline-tools" "$sdk_root/cmdline-tools/latest"
    rm -rf "$sdk_root/cmdline-tools/unpack" "$archive.part"
    sdkmanager=$sdk_root/cmdline-tools/latest/bin/sdkmanager
  fi
  echo "Installing Android SDK packages: ${packages[*]}"
  # Older command-line tools ask you to accept the SDK licenses first; newer
  # ones need no step.
  if [[ -t 0 ]]; then "$sdkmanager" --sdk_root="$sdk_root" --licenses; fi
  "$sdkmanager" --sdk_root="$sdk_root" "${packages[@]}"
fi

if [[ -z ${SMS_PORT_ROOT:-} ]]; then git -C "$root" submodule update --init sms-port; fi
[[ -f "$port_root/android/build-native.sh" ]] || { echo 'Set SMS_PORT_ROOT to the Android port checkout.' >&2; exit 1; }
"$port_root/android/build-native.sh" --prepare
cd "$root"
flavor=Sunshine apk=app/build/outputs/apk/sunshine/debug/app-sunshine-debug.apk
[[ ${SMS_ECLIPSE:-0} != 1 ]] || flavor=Eclipse apk=app/build/outputs/apk/eclipse/debug/app-eclipse-debug.apk
./gradlew ":app:assemble${flavor}Debug" "-PsmsPortRoot=$port_root" "${@}"
echo "Built $apk"
echo "Install it on a phone connected over USB with: $sdk_root/platform-tools/adb install -r $apk"
