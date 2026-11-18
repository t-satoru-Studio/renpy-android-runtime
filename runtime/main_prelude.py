# --- renpy-android-runtime ---------------------------------------------------
# Copyright (C) 2026 t-satoru
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Inserted by tools/prepare.py into Ren'Py's own renpy.py (which becomes
# main.py in private.mp3). librenpython's SDL_main ignores the argv that the
# Java side could pass, and always runs "python main.py", so the launcher hands
# over the game through environment variables set in GameActivity.preparePython.
# They become Ren'Py's normal command line:
#
#   main.py <basedir> [--savedir <dir>] [--compile]
#
# With a basedir, Ren'Py reads the game from the file system instead of the
# APK (renpy/loader.py: renpy_base != basedir), and config.gamedir points into
# the real game folder.


def _rar_launch_arguments(environ):
    basedir = environ.get("RAR_BASEDIR")
    if not basedir:
        return None

    args = [basedir]

    savedir = environ.get("RAR_SAVEDIR")
    if savedir:
        args += ["--savedir", savedir]

    if environ.get("RAR_COMPILE") == "1":
        args.append("--compile")

    return args


_rar_args = _rar_launch_arguments(os.environ)
if _rar_args is not None:
    sys.argv[1:] = _rar_args
del _rar_args


# --- renpy-android-runtime: loading progress ----------------------------------
# Ren'Py on Android shows nothing until its first frame (RAPT only has a static
# presplash image, which a build without a bundled game lacks). GameActivity
# draws a loading screen over the SDL surface instead; this thread tells it what
# Ren'Py is doing. It never touches Ren'Py: it looks at renpy.game's state and at
# the main thread's stack every 100 ms, which costs the main thread a few
# microseconds per sample, and stops once Ren'Py hides the presplash.

def _rar_loading_state(main_ident, seen):
    """
    Returns (stage, detail, done, total) for the main thread right now. The
    stages are keys of GameActivity's loading screen. `seen` is a set the
    caller keeps between calls.
    """

    game = sys.modules.get("renpy.game")
    info = getattr(game, "exception_info", None) or ""
    if not info:
        # Still importing Ren'Py.
        return ("engine", "", 0, 0)

    frames = []
    frame = sys._current_frames().get(main_ident)
    while frame is not None:
        frames.append(frame)
        frame = frame.f_back

    def find(module, name):
        for f in frames:
            if f.f_code.co_name == name and f.f_globals.get("__name__") == module:
                return f
        return None

    if info.startswith("After initialization"):
        seen.add("init done")

    # Each init statement runs as game code, which sets exception_info again.
    in_init = info.startswith("While executing init code") or (
        info == "While running game code:" and "init done" not in seen)

    if in_init:
        script = game.script
        index = getattr(game, "initcode_ast_id", 0)
        detail = ""
        try:
            node = script.initcode[index][1]
            detail = "{}:{}".format(node.filename, node.linenumber)
        except Exception:
            pass
        return ("init", detail, index + 1, len(script.initcode))

    if info == "While loading the script.":
        load_script = find("renpy.script", "load_script")
        if load_script is None:
            return ("prepare", "", 0, 0)
        loc = load_script.f_locals
        detail = ""
        current = find("renpy.script", "load_appropriate_file")
        if current is not None:
            detail = str(current.f_locals.get("fn", ""))
        return ("script", detail, int(loc.get("count", 0)), len(loc.get("script_files", ())))

    if info == "Before loading the script.":
        if find("renpy.python", "load") is not None:
            return ("bytecode", "", 0, 0)
        return ("files", "", 0, 0)

    if info == "After loading the script.":
        return ("saves", "", 0, 0)

    for f in frames:
        module = f.f_globals.get("__name__", "")
        if module.startswith("renpy.sl2") or module == "renpy.pyanalysis":
            return ("screens", "", 0, 0)
        if module.startswith("renpy.display") or module.startswith("renpy.gl2"):
            return ("display", "", 0, 0)

    return ("finishing", "", 0, 0)


def _rar_start_loading_reporter(environ):
    if not environ.get("RAR_BASEDIR"):
        return False

    try:
        import threading
        import time
        from jnius import autoclass, detach

        # Looked up here: threads started from Python only see the system class
        # loader, not the app's.
        activity = autoclass("studio.tsatoru.renpy86.GameActivity")
    except Exception:
        return False

    main_ident = threading.get_ident()

    def report():
        last = None
        seen = set()
        try:
            while not activity.isLoadingDone():
                try:
                    state = _rar_loading_state(main_ident, seen)
                except Exception:
                    state = None

                if state is not None and state != last:
                    activity.reportLoading(*state)
                    last = state

                time.sleep(0.1)
        finally:
            detach()

    threading.Thread(target=report, name="rar-loading", daemon=True).start()
    return True


_rar_start_loading_reporter(os.environ)
# --- end renpy-android-runtime -----------------------------------------------
