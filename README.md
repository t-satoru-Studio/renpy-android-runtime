# renpy-android-runtime

A standalone Android app that runs Ren'Py 8.6 games from a folder on the device, for game launchers.

Ren'Py's official Android support (RAPT) packages one game into one APK. This app keeps Ren'Py's own engine and Android code, unchanged, and points it at a game in shared storage instead. A launcher starts a game with an intent (see [PROTOCOL.md](PROTOCOL.md)); the extras follow the JoiPlay Ren'Py plugin's format, so launchers that already support that plugin need only a new package name and action.

- Ren'Py **8.6.0.26111403** (SDL3, Python 3.12), official prebuilt `librenpython.so`
- arm64-v8a, Android 6.0 (API 23) and later
- Saves in `<game>/saves`, like Ren'Py on a PC

## How it works

| Piece | Source |
|---|---|
| Engine, Python, SDL3 (`librenpython.so`), Ren'Py Android Java | Official `renpy-8.6.0-rapt.zip`, unmodified |
| Ren'Py Python code, standard library, `renpy/common` | Official `renpy-8.6.0-sdk.tar.bz2`, unmodified, laid out as RAPT does |
| `main.py` | Ren'Py's `renpy.py` plus a short prelude ([runtime/main_prelude.py](runtime/main_prelude.py)) |
| Launcher settings Ren'Py has no switch for, background workaround | [runtime/common/00runtime_overrides.rpy](runtime/common/00runtime_overrides.rpy) |
| Launch handling | [app/src/main/java](app/src/main/java/studio/tsatoru/renpy86) |

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

Tested on one phone (Android 16, Snapdragon 8s Gen 3, arm64) with Ren'Py 8.6.0 games: a commercial-size game (5.6 GB of RPA archives, video, CJK text) and Ren'Py's own sample game. Checked: start from a launcher, rendering, touch, back as rollback, saving to `<game>/saves`, going to the background and back, switching games, relaunching the running game, the storage permission flow and error messages. See [CHANGELOG.md](CHANGELOG.md).

Known issues:

- First start of a large game reads all archive indexes from shared storage, which took about 15 s on the test phone (4 s when cached).

Upstream problems this runtime works around (Ren'Py 8.6.0 with SDL 3.4.18 on Android, so Ren'Py's own Android builds are likely affected too):

- **Back closes the game on Android 16.** Apps targeting API 36 get back only through the back dispatcher, never as a key event, so SDL cannot keep it and the activity finishes under the running game. `GameActivity` registers a back callback that sends Ren'Py the back key (rollback). Fixed on SDL `main` (libsdl-org/SDL#16052) but not in 3.4; reported as renpy/renpy#7533.
- **8-second main-thread stall when sent to the background, and no background save.** Ren'Py turns off SDL's pause blocking (`SDL_ANDROID_BLOCK_ON_PAUSE=0`), but while the app is paused SDL's `Android_PumpEvents` keeps waiting as long as events arrive at least every 100 ms, and Ren'Py's 50 ms timer keeps waking it. So `SDL_EVENT_WILL_ENTER_BACKGROUND` reaches Ren'Py only on resume, and RAPT's `onStop` waits its 8 s limit. The runtime stops Ren'Py's timers when SDL pauses (`runtime/common/00runtime_overrides.rpy`); `onStop` now ends in about 1 s, after Ren'Py has saved. Reported as libsdl-org/SDL#16744 and renpy/renpy#7534.
Not supported: games made with Ren'Py 7 (Python 2), games that need native Python modules for other platforms, Live2D (the Cubism library is not bundled).

## License

The code in this repository is GPL-3.0-or-later ([LICENSE](LICENSE)). The APK also contains Ren'Py and the libraries it is built with, each under its own license; see [NOTICE.md](NOTICE.md).

This project is not affiliated with Ren'Py or JoiPlay.
