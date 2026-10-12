# Android launcher

This is a native Android companion to the desktop launcher. The APK includes
the compiled PC port and SDL, but no game data. It targets ARM64 Android 10 or
newer, with GLES 3.1 or newer and a controller.
AYN Thor and similar modern Android handhelds are the intended hardware.
See [VALIDATION.txt](VALIDATION.txt) for build and emulator results. Plaza
gameplay is currently slow in the ARM64-translating emulator; handheld
performance has not been measured.

## Build

Keep the Android port checkout beside this launcher checkout as `sms-port`,
or set `SMS_PORT_ROOT` to its location. Install a JDK (17 or newer), Python 3,
CMake, Git, curl, tar, patch, and the Android SDK. Install these SDK packages:

```
platform-tools platforms;android-35 build-tools;36.0.0
ndk;28.2.13676358 cmake;3.22.1
```

Accept the SDK licenses, set `ANDROID_HOME` and `JAVA_HOME`, and run:

```sh
npm run android:build
```

The script downloads checksum-verified SDL 2.32.10, initializes the port's
decompilation submodule, and uses the checked-in Gradle wrapper to build only
`arm64-v8a`. The installable debug APK is
`android/app/build/outputs/apk/debug/app-debug.apk`.

For a release build, run `./gradlew :app:assembleRelease -PsmsPortRoot=...`
inside `android/` after preparing SDL. Release signing uses your own Android
signing configuration; do not distribute an unsigned APK or share a key.

## Use

Install the APK, open the landscape SMS Launcher, and choose **Import disc image**. Pick a
US revision 0 Sunshine ISO or GCM using Android's document picker. Import
copies it into private app storage; allow enough free space for that copy.
Choose frame rate, resolution scale, aspect ratio and volume, then **Play**.
Connect a USB/Bluetooth controller or use the handheld's built-in gamepad.
**Android performance hints** defaults on and lets supported devices adjust
CPU scheduling to the frame workload. It does not force clock speeds or
bypass thermal limits. Toggle it off to compare the same scene and settings.
**Show performance overlay** reports the port's game/shown FPS, frame time,
game work, GX submission, presentation/swap and idle time. This helps
separate game timing from an external SurfaceFlinger FPS counter.
Android Back returns to the launcher. Each game session uses a fresh process.

**Export save backup** writes a ZIP you can keep or move to the desktop port's
`card-a` folder. **Import save backup** accepts a ZIP containing the desktop
port's `index.txt`, `super_mario_sunshine.dat` and
`super_mario_sunshine.stat`, at the root or beneath `card-a/`. Import keeps
the prior card in a private `saves/previous` folder. Use Export before
uninstalling: uninstalling deletes the disc, saves, settings and caches.
The import does not support Dolphin `.gci` or whole memory card images.

**Export game log** uses the document picker to save diagnostic output.
Disc and save import need no broad storage permission, and gameplay is
offline. This Android launcher currently supplies the base game; the desktop
launcher remains the path for Eclipse and HD texture pack setup.

## Emulator validation

On an x86 Linux host, the Android 35 Google APIs x86_64 image includes
`libndk_translation` and advertises `arm64-v8a`. Use the host GPU, KVM,
and a GLES 3.1/3.2-capable host driver. Software renderers that expose only
GLES 3.0 cannot run this renderer. Verify the emulator's ABI list and GPU
before installing; a successful x86 APK test would not validate this ARM64 APK.
The Android 30 image's older translator crashes on a valid ARM64 floating-point
conversion instruction emitted by the compiler; use the newer image.

```sh
adb shell getprop ro.product.cpu.abilist
adb shell getprop ro.dalvik.vm.native.bridge
adb shell dumpsys SurfaceFlinger
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Import a copy of your disc and exported save, then test the ordinary title
screen and file selection, loaded stage gameplay, movie playback, saving,
Back, and a second Play session. Keep screenshots and game logs with the
validation report. Emulator checks do not measure AYN Thor performance.

Debug APKs also include the native GX graphics self-test. With a disc imported,
start the launcher with the diagnostic intent and choose **Play**:

```sh
adb shell am force-stop dev.chasem.smslauncher
adb shell am start -W -n dev.chasem.smslauncher/.LauncherActivity \
  --ez gxSelftest true --esa testEnvironment SMS_GX_CHECK_ERRORS=1
adb shell run-as dev.chasem.smslauncher cat files/game/game.log
```

Wait for `66 passed, 0 failed` and `GX self-test returned 0` in the log
(`63 passed` if the GLES clipping extension is unavailable).
Export or copy that log before the next Play session replaces it. Force-stop
and reopen the launcher without the intent extras to resume ordinary gameplay.
The self-test library and diagnostic intent handling are omitted from release
builds. This test exercises the ARM64 renderer through a real Android SDL
surface, including depth peeks, pixel metrics, texture sampling and clipping.

For repeated debug gameplay checks, use `--esa testEnvironment
SMS_SKIP_MOVIES=1,SMS_OVERLAY=1` on the launcher intent instead of `gxSelftest`.
The movie flag skips non-looping prerecorded movies; native 3D camera sequences
still render. The overlay reports frame timing. These intent flags apply only
to debug APKs. Force-stop and reopen without extras to check ordinary playback.
