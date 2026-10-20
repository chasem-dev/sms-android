# Changelog

What changed in each Android release and preview, newest first. Each push to
`main` with lines under Unreleased publishes a new preview (or a full release)
with those lines as its notes, and moves them under its heading. See AGENTS.md.

## Unreleased

## 0.2.0-preview.11

- The game no longer fails to start with "could not reserve the game's compatibility address range" on phones where Android leaves little room for it, which mostly affected SMS Eclipse.

## 0.2.0-preview.10

- A second app, SMS Eclipse, plays Super Mario Eclipse: import your Super Mario Sunshine disc in it and choose Download Super Mario Eclipse, which downloads the mod (about 851 MB) and patches your copy; it installs beside SMS Launcher with its own disc and saves.

## 0.2.0-preview.9

- HD textures now show at full detail, as on a PC, on phones whose graphics chip reads the pack directly (such as the Snapdragon 8 Gen 3); before, the largest ones were shown at lower detail, and wide ones at the original game's.

## 0.2.0-preview.8

- Resolution scale in Game settings is now called Picture sharpness and goes up to Sharpest (4×); Original (1×) is still the default.
- Game settings has an HD textures switch, off by default, with a Download HD textures button that installs the UHD texture pack and its extras inside the app (about 1 GB to download, 3.1 GB installed); a stopped download carries on where it left off, and Remove HD textures deletes them again.

## 0.2.0-preview.7

- Use performance CPU cores in Game settings is now on by default, which keeps the game on the phone's fastest cores for smoother play; if you changed it before, your choice is kept.
- The map's opening and closing wipe no longer stutters, and the map screen now fills the widescreen frame.
- The circle wipes, such as going from the file select into Delfino Plaza, are now solid black at higher resolution scales instead of grey stripes, on phones whose graphics driver supports it.
- The camera-in-wall screen and the HUD's sparkles at the screen edges now line up with the widescreen frame.
- Pressing L1 and R1 together saves a state, and the pause menu lists your states to load; on Android a state brings back the level, your progress and Mario's position rather than the exact moment.
- Dune Buds and other sounds made from simple tones are no longer silent.
- At 60 FPS, Blooper punches now hurt Mario, birds fly at their normal speed and the GO! letters keep their size, and the GO! sparkles no longer come back after a pause.
- Fixed crashes when the Phantamanta first splits in Sirena Beach and when Fire Petey breathes fire with too many effects on screen.
- The placeholder cubes in Bianco Hills' Dirty Lake secret course are hidden, and a secret course's music now starts while the last stage's song fades out.

## 0.2.0-preview.6

- Fixed the game slowing to about 1 FPS for as long as Mario is electrocuted, for example by an Electrokoopa in Pinna Park.
- Looking toward the sun no longer drops the game below 60 FPS, and every frame takes a little less time.
- The performance log now also records draws, texture uploads and waits for the graphics chip, to help track down slow scenes.
- Game settings now has Invert camera left/right and Invert camera up/down options, for when the camera turns the opposite way to how you push the stick.
- Fixed Controller configuration ignoring a stick direction given to the Up, Down, Left and Right rows, so swapping the camera stick's Left and Right there now turns the camera the way you assigned.
- Controller configuration in Game settings now has an Invert button on each stick's Horizontal and Vertical rows once a stick is assigned, and assigning one says that moving the stick the other way inverts it.

## 0.2.0-preview.5

- Fixed a black screen from the title screen onward, with sound still playing, on devices where the game has to load into a different part of memory.

## 0.2.0-preview.4

- Controller configuration in Game settings now has separate Full press and Soft press rows for L and R, so controllers without analog triggers can make a light press (such as spraying while moving), plus Up, Down, Left and Right rows for both sticks so buttons can move Mario or the camera.

## 0.2.0-preview.3

- No changes from 0.2.0-preview.2.

## 0.2.0-preview.2

- Game settings now has Controller configuration: assign buttons, sticks and triggers for each controller, including ones the game couldn't map automatically, then clear controls or restore defaults. Saved layouts apply the next time you press Play.

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
