# renpy-android-runtime

A standalone Android app that runs Ren'Py 8.6 games from a folder on the device, as a runtime module for the Tyranor launcher.

Ren'Py's official Android support (RAPT) packages one game into one APK. This app keeps Ren'Py's own engine and Android code, unchanged, and points it at a game in shared storage instead. It implements Tyranor's third-party runtime module protocol (see [PROTOCOL.md](PROTOCOL.md)): Tyranor finds it by its package name and launch action, recognizes Ren'Py 8.6 and later games from the scan description it declares, and starts them with the game folder.

- Ren'Py **8.6.0.26111403** (SDL3, Python 3.12), official prebuilt `librenpython.so`
- arm64-v8a, Android 6.0 (API 23) and later
- Saves in `<game>/saves`, like Ren'Py on a PC; declared to Tyranor, which can manage and sync them

## Use with Tyranor

1. Install the APK from [Releases](https://github.com/t-satoru-Studio/renpy-android-runtime/releases) and allow it all files access (open it once from the home screen, or on the first launch).
2. In Tyranor, open Miaomiao Tools → Engine modules and enable "Ren'Py 8.6".
3. Scan your game folders again. Ren'Py 8.6 and later games are now listed as "Plugin" games; older Ren'Py games keep using Tyranor's other Ren'Py runtimes.
4. Per-game settings are in the game's drawer under Engine settings.

## How it works

| Piece | Source |
|---|---|
| Engine, Python, SDL3 (`librenpython.so`), Ren'Py Android Java | Official `renpy-8.6.0-rapt.zip`, unmodified |
| Ren'Py Python code, standard library, `renpy/common` | Official `renpy-8.6.0-sdk.tar.bz2`, unmodified, laid out as RAPT does |
| `main.py` | Ren'Py's `renpy.py` plus a short prelude ([runtime/main_prelude.py](runtime/main_prelude.py)) |
| Settings Ren'Py has no switch for, background workaround | [runtime/common/00runtime_overrides.rpy](runtime/common/00runtime_overrides.rpy) |
| Launch handling | [app/src/main/java](app/src/main/java/studio/tsatoru/renpy86) |
| Module description (scan, settings, saves) | [app/src/main/res/raw/tyranor_runtime.json](app/src/main/res/raw/tyranor_runtime.json) |

`librenpython` always runs `python main.py` and ignores command line arguments, so `GameActivity` passes the game through environment variables in `preparePython()`, and the prelude turns them into Ren'Py's normal command line: `main.py <game folder> --savedir <dir>`. With a base directory given, Ren'Py reads the game from the file system instead of the APK.

## Building

Requirements: JDK 17 or later, Android SDK (platform 36), Python 3.10+, about 2 GB of disk for the upstream archives.

```sh
python3 tools/prepare.py          # download, verify and stage the upstream runtime
./gradlew :app:testDebugUnitTest :app:assembleDebug
python3 -m unittest discover tests
```

`tools/prepare.py` downloads the archives pinned in [upstream.lock.json](upstream.lock.json), checks their SHA-256 against the values published at renpy.org, and writes `build/staging/`. It also compiles `runtime/common/*.rpy` with the Linux Ren'Py SDK, so preparing needs Linux x86_64.

Release builds are signed with a key outside the repository:

```sh
RAR_SIGNING_PROPERTIES=/path/to/signing.properties ./gradlew :app:assembleRelease
```

The properties file holds `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

## Status

Tested on one phone (Android 16, Snapdragon 8s Gen 3, arm64) with Ren'Py 8.6.0 games: a commercial-size game (5.6 GB of RPA archives, video, CJK text) and Ren'Py's own sample game. r3 was checked with Tyranor's third-party module flow: discovery (off by default), enabling, scanning (an 8.6 game is claimed; a folder with an 8.5 `script_version` and no `renpy/asynctask.py` is not), per-game settings, the storage permission flow on first launch, launch, saving on going to the background, and Tyranor's save management seeing `<game>/saves`. Earlier revisions also checked rendering, touch, back as rollback, saving to `<game>/saves`, going to the background and back, switching games, relaunching the running game, the storage permission flow and error messages. See [CHANGELOG.md](CHANGELOG.md).

Known issues:

- First start of a large game reads all archive indexes from shared storage, which took about 15 s on the test phone (4 s when cached).

Upstream problems this runtime works around (Ren'Py 8.6.0 with SDL 3.4.18 on Android, so Ren'Py's own Android builds are likely affected too):

- **Back closes the game on Android 16.** Apps targeting API 36 get back only through the back dispatcher, never as a key event, so SDL cannot keep it and the activity finishes under the running game. `GameActivity` registers a back callback that sends Ren'Py the back key (rollback). Fixed on SDL `main` (libsdl-org/SDL#16052) but not in 3.4; reported as renpy/renpy#7533.
- **8-second main-thread stall when sent to the background, and no background save.** Ren'Py turns off SDL's pause blocking (`SDL_ANDROID_BLOCK_ON_PAUSE=0`), but while the app is paused SDL's `Android_PumpEvents` keeps waiting as long as events arrive at least every 100 ms, and Ren'Py's 50 ms timer keeps waking it. So `SDL_EVENT_WILL_ENTER_BACKGROUND` reaches Ren'Py only on resume, and RAPT's `onStop` waits its 8 s limit. The runtime stops Ren'Py's timers when SDL pauses (`runtime/common/00runtime_overrides.rpy`); `onStop` now ends in about 1 s, after Ren'Py has saved. Reported as libsdl-org/SDL#16744 and renpy/renpy#7534.
Not supported: games made with Ren'Py 7 (Python 2), games that need native Python modules for other platforms, Live2D (the Cubism library is not bundled).

## License

The code in this repository is GPL-3.0-or-later ([LICENSE](LICENSE)). The APK also contains Ren'Py and the libraries it is built with, each under its own license; see [NOTICE.md](NOTICE.md).

This project is not affiliated with Ren'Py or JoiPlay.
