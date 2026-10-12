# Changelog

What changed in each Android preview, newest first. Each push to `main` with
lines under Unreleased publishes a new preview with those lines as its notes,
and moves them under that preview's heading. See AGENTS.md.

## Unreleased

## 0.2.0-preview.1

- The Android launcher now has its own version numbers, starting at 0.2.0, and is released from its own page. Install it over preview 17 to keep your disc and saves.

## 0.1.57-android-preview.17 and earlier

Published as `android-pr-107-v*` pre-releases from the desktop launcher's repository.

### Launcher

- Added a landscape Android launcher for modern 64-bit handhelds, with disc import, game settings, and save backup import and export.
- Added Android performance hints and a performance overlay switch in Game settings.
- Game settings now includes performance headroom and display refresh requests, plus optional timing logs for diagnosing slow gameplay.
- The launcher now keeps its version and game build visible at the bottom of the screen and includes them in exported game logs.
- Launcher panels now keep their headings visible and show scrollbars so game settings are easier to find in landscape.
- Display refresh and performance headroom requests now start off following reports of slower gameplay, and remain available for testing.
- Game settings now has an optional performance CPU cores switch that works independently of performance hints.
- Added an Input test screen for connected controllers and wireless keyboards, with live buttons, keys and sticks, a device list and input log export.

### Game

- Added native 64-bit Android support with controller input, audio, saves, and an OpenGL ES renderer.
- Reduced rendering overhead in areas with pollution.
- Fixed a loading hang after the opening movies.
- Fixed a black screen with audio on some Adreno graphics drivers.
- The game now reports frame workloads to help the device schedule the game and renderer.
- The game can request an earlier frame-work deadline to leave performance headroom for smooth gameplay.
- The game now uses its original frame workload deadline unless experimental performance headroom is enabled.
- The game can keep frame production on a detected group of faster CPU cores, with automatic fallback when that group is unclear.
- The game no longer waits for an extra screen refresh after a late frame just to give background loading a turn.
- Reduced the CPU work used to position widescreen HUD elements and menus.
- Fixed a startup crash when the system occupies the game's preferred memory address, by relocating its arena and keeping game and graphics addresses consistent.
- Fixed textures and colour palettes being read from the wrong memory location after relocation, which could leave the picture black after the intro.
- Added a controller mapping for the legacy USB Gamepad adapter that was detected in Input test but ignored by the game.
