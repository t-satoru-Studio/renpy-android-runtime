# Changelog

## 8.6.0-r1 (unreleased)

First version.

- Ren'Py 8.6.0.26111403 (SDL3, Python 3.12) from the official RAPT and SDK archives, arm64-v8a.
- Launch protocol with JoiPlay-compatible extras (`game`, `settings`) and plain extras (`folder`, `savedir`, ...). See PROTOCOL.md.
- Saves in `<game>/saves` (or `savedir`); logs in `Android/data/<package>/files/logs/<id>/`.
- Settings: less memory, less updates, vsync, phone variant, recompile, hardware video, autosave.
- Each game runs in its own `:game` process, which exits when the game quits; launching another game stops the previous one, launching the same game brings it back.
- Back gesture and back key roll back, as in Ren'Py, instead of closing the game (Android 16 sends back only through the back dispatcher; SDL3 would otherwise finish the activity).
