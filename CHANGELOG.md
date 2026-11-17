# Changelog

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
