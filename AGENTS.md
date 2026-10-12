# Notes for AI agents

## Update the changelog in every commit

Every commit that a tester could notice adds a line under `## Unreleased` in
[`CHANGELOG.md`](CHANGELOG.md), in the same commit. That includes game changes
pulled in by moving the `sms-port` submodule. Commits a tester cannot notice
(tests, CI, refactors, docs) say so in the message, e.g. `No changelog: CI only`.

Write one plain sentence per change for players, ending with a full stop: what
they'll notice, and where to find it if it's a setting. No commit hashes, PR or
issue numbers, file or function names. Fixes say what was wrong.

## Releases

Follow the README's "Releases" section. Increment `smsAndroidPreview` for each
published preview, never reuse a version, and keep signing with the key
`release.sh` checks for: Android refuses updates signed with a different key,
so testers would have to uninstall and lose their disc and saves.

## The game

The game is the `sms-port` submodule
([sms-pc-port](https://github.com/chasem-dev/sms-pc-port)). Make game changes
there, then move the submodule to the new commit here. Only pin commits that
are pushed to that repository.
