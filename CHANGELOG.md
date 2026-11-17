# Changelog

## 8.6.0-r3

The runtime is now a Tyranor third-party runtime module. **The package name changed** to `com.tyranor.runtime.renpy86`, so r3 installs as a new app next to r2; uninstall r2 (`studio.tsatoru.renpy86`) yourself.

- Launch with Tyranor's module protocol (`com.tyranor.runtime.action.LAUNCH`, `FOLDER_PATH`, `GAME_TITLE`, `SETTINGS`). The JoiPlay-style `studio.tsatoru.renpy86.RUN` entry is removed.
- Module description `res/raw/tyranor_runtime.json`: Tyranor recognizes Ren'Py 8.6 and later games (folders with `game/` and `renpy/asynctask.py*`), shows per-game settings, and manages `<game>/saves`.
- Settings are now per game in Tyranor. Hardware video and autosave can follow the game, or be turned on or off.
- No longer sets `SDL_ANDROID_BLOCK_ON_PAUSE` itself: Ren'Py's `librenpython` already sets it before SDL starts. The r2 notes said Ren'Py set it too late; that was wrong.

## 8.6.0-r2

- Fix: sending the game to the background blocked the main thread for 8 seconds, and Ren'Py saved only when the game came back. SDL hints are now set before SDL starts (`SDL_ANDROID_BLOCK_ON_PAUSE=0`, which Ren'Py sets too late), and Ren'Py's timers are stopped when SDL pauses, so SDL can deliver the background event. `onStop` now takes about 1 s, including Ren'Py's save.
- `SDL_ANDROID_TRAP_BACK_BUTTON` is now actually seen by SDL (it was set after SDL had copied the environment).

## 8.6.0-r1

First version.

- Ren'Py 8.6.0.26111403 (SDL3, Python 3.12) from the official RAPT and SDK archives, arm64-v8a.
- Launch protocol with JoiPlay-compatible extras (`game`, `settings`) and plain extras (`folder`, `savedir`, ...). See PROTOCOL.md.
- Saves in `<game>/saves` (or `savedir`); logs in `Android/data/<package>/files/logs/<id>/`.
- Settings: less memory, less updates, vsync, phone variant, recompile, hardware video, autosave.
- Each game runs in its own `:game` process, which exits when the game quits; launching another game stops the previous one, launching the same game brings it back.
- Back gesture and back key roll back, as in Ren'Py, instead of closing the game (Android 16 sends back only through the back dispatcher; SDL3 would otherwise finish the activity).
