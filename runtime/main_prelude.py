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
# --- end renpy-android-runtime -----------------------------------------------
