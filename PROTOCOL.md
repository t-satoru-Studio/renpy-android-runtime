# Launch protocol

How a launcher app starts a game in this runtime.

## Identity

| | |
|---|---|
| Package | `studio.tsatoru.renpy86` (debug builds: `studio.tsatoru.renpy86.debug`) |
| Action | `studio.tsatoru.renpy86.RUN` |
| Activity | `studio.tsatoru.renpy86.LaunchActivity` (exported) |
| Ren'Py build | `<meta-data android:name="studio.tsatoru.renpy86.RENPY_BUILD">`, e.g. `8.6.0.26111403` |
| Version code | `MMmmppRR`: Ren'Py major, minor, patch and runtime revision (8.6.0 r1 = `8060001`) |

Send an explicit intent (set the package). The runtime finishes `LaunchActivity` and starts the game in its own task, so the launcher's task is not replaced.

## Extras

Either form works; JoiPlay-style values win where both are given.

### JoiPlay-compatible

The same extras launchers already build for JoiPlay's Ren'Py plugin:

- `game` (String, JSON): `{"title": "...", "id": "...", "folder": "/storage/emulated/0/Games/MyGame"}`. Other keys (`execFile`, `type`) are ignored.
- `settings` (String, JSON): `{"renpy": {"renpy_less_memory": {"boolean": true}, ...}}`. Values may also be bare booleans. The `app` object (JoiPlay's cheats) is ignored.

### Plain

- `folder` (String): absolute path of the game.
- `title` (String, optional)
- `id` (String, optional): names the log directory. Default: hex of the folder path's Java `hashCode()`.
- `savedir` (String, optional): absolute save directory. Default: `<folder>/saves`.
- `settings` as above.

`folder` may be the game's base directory (the one that contains `game/`) or the `game/` directory itself.

## Settings

All are optional; an absent setting leaves Ren'Py's (or the game's) default.

| Key | Effect |
|---|---|
| `renpy_less_memory` | `true` sets `RENPY_LESS_MEMORY` |
| `renpy_less_updates` | `true` sets `RENPY_LESS_UPDATES` |
| `renpy_vsync` | `false` sets `RENPY_GL_VSYNC=0` |
| `renpy_phonesmallvariant` | `true` forces the `small phone touch android mobile` screen variants |
| `renpy_recompile` | `true` runs Ren'Py with `--compile` (recompiles `.rpy` to `.rpyc`) |
| `renpy_hw_video` | sets `config.hw_video`, after the game's init code |
| `renpy_autosave` | sets `config.has_autosave` and `config.autosave_on_choice`, after the game's init code |

`renpy_dont_use_gl2` is accepted and ignored: Ren'Py 8.6 on Android has only the `gles2` renderer.

## Files

- Saves: `savedir`, by default `<folder>/saves`. Ren'Py 8.6 may briefly create `saves.renpy-repair` or `saves.renpy-replaced` next to it while it repairs permissions; launchers that sync saves should ignore those names.
- Logs: `Android/data/<package>/files/logs/<id>/log.txt` and `traceback.txt`.
- The game folder needs to be writable if the game compiles scripts or writes caches.

## Storage access

The runtime reads games from shared storage, so it needs "All files access" (Android 11+) or the storage permission (Android 6–10). If it is missing, `LaunchActivity` explains why and opens the system settings page, then continues the launch when the user comes back.

## Processes

Python can start only once per process. The game runs in the `:game` process; launching a different game stops the previous one first, while launching the same game again brings it back. When the game quits, its process exits.

## Example

```sh
adb shell am start -a studio.tsatoru.renpy86.RUN \
  -n studio.tsatoru.renpy86/.LaunchActivity \
  --es folder /sdcard/Games/MyGame \
  --es settings '{"renpy_less_memory":true}'
```
