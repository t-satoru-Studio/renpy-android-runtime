# Launch protocol

The runtime is a Tyranor third-party runtime module (protocol 1). Tyranor's module developer guide describes the protocol itself; this page lists what this runtime declares and how it reads each value.

## Identity

| | |
|---|---|
| Package | `com.tyranor.runtime.renpy86` (debug builds: `com.tyranor.runtime.renpy86.debug`) |
| Action | `com.tyranor.runtime.action.LAUNCH`, category `DEFAULT` |
| Activity | `studio.tsatoru.renpy86.LaunchActivity` (exported) |
| Module description | `<meta-data android:name="com.tyranor.runtime.MANIFEST" android:resource="@raw/tyranor_runtime">` on that activity |
| Ren'Py build | `<meta-data android:name="studio.tsatoru.renpy86.RENPY_BUILD">`, e.g. `8.6.0.26111403` |
| Version code | `MMmmppRR`: Ren'Py major, minor, patch and runtime revision (8.6.0 r3 = `8060003`) |

Until 8.6.0-r2 the package was `studio.tsatoru.renpy86`, started with action `studio.tsatoru.renpy86.RUN` and JoiPlay-style extras. That entry point is gone; the two packages can be installed side by side.

## Module description

[app/src/main/res/raw/tyranor_runtime.json](app/src/main/res/raw/tyranor_runtime.json):

- **Scan**: `match: all` with markers `game` and `renpy/asynctask.py*`. `renpy/asynctask.py` first ships with Ren'Py 8.6.0, so games made with 8.5 and earlier are not claimed. The trailing `*` also matches distributions that ship only `.pyc` files.
- **Settings** (version 1), below.
- **Saves**: directory `saves`, where Ren'Py writes them (`<game folder>/saves`).

## Extras

| Extra | Use |
|---|---|
| `com.tyranor.runtime.extra.FOLDER_PATH` | Absolute path of the game. Required. May be the base directory (the one containing `game/`) or `game/` itself. |
| `com.tyranor.runtime.extra.GAME_TITLE` | Optional. Default: the folder name. |
| `com.tyranor.runtime.extra.PROTOCOL_VERSION` | Logged. |
| `com.tyranor.runtime.extra.SETTINGS` | `{"version": 1, "values": {...}}`. A payload that is not JSON, or has another version, is ignored as a whole; a known key with a wrong-typed value keeps its default; unknown keys are ignored. |

## Settings

The defaults change nothing: with them, Ren'Py and the game decide as they would on their own.

| Key | Type, default | Effect |
|---|---|---|
| `hw_video` | `game` / `on` / `off`, `game` | sets `config.hw_video` after the game's init code |
| `autosave` | `game` / `on` / `off`, `game` | sets `config.has_autosave` and `config.autosave_on_choice` after the game's init code |
| `phone_small_variant` | boolean, `false` | `true` forces the `small phone touch android mobile` screen variants |
| `vsync` | boolean, `true` | `false` sets `RENPY_GL_VSYNC=0` |
| `less_memory` | boolean, `false` | `true` sets `RENPY_LESS_MEMORY` |
| `less_updates` | boolean, `false` | `true` sets `RENPY_LESS_UPDATES` |
| `recompile` | boolean, `false` | `true` runs Ren'Py with `--compile` (recompiles `.rpy` to `.rpyc`) |

## Files

- Saves: `<folder>/saves`. Ren'Py 8.6 may briefly create `saves.renpy-repair` or `saves.renpy-replaced` next to it while it repairs permissions.
- Logs: `Android/data/<package>/files/logs/<id>/log.txt` and `traceback.txt`, where `<id>` is the hex of the folder path's Java `hashCode()`.
- The game folder needs to be writable if the game compiles scripts or writes caches.

## Storage access

The runtime reads games from shared storage, so it needs "All files access" (Android 11+) or the storage permission (Android 6–10); Tyranor's own access does not carry over. If it is missing, `LaunchActivity` explains why and opens the system settings page, then continues the launch when the user comes back.

## Processes

Python can start only once per process. The game runs in the `:game` process; launching a different game stops the previous one first, while launching the same game again brings it back. When the game quits, its process exits.

## Example

```sh
adb shell am start -a com.tyranor.runtime.action.LAUNCH \
  -n com.tyranor.runtime.renpy86/studio.tsatoru.renpy86.LaunchActivity \
  --es com.tyranor.runtime.extra.FOLDER_PATH /sdcard/Games/MyGame \
  --es com.tyranor.runtime.extra.SETTINGS '{"version":1,"values":{"less_memory":true}}'
```
