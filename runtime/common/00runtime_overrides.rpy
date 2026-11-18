# Copyright (C) 2026 t-satoru
# SPDX-License-Identifier: GPL-3.0-or-later
#
# renpy-android-runtime: launcher settings that Ren'Py has no environment
# variable for. Runs after the game's own init code (priority 1500), and each
# override applies only when the launcher set its variable, so a game keeps its
# own configuration otherwise.

init 1500 python hide:
    import os

    def flag(name):
        value = os.environ.get(name)
        if value is None:
            return None
        return value == "1"

    hw_video = flag("RAR_HW_VIDEO")
    if hw_video is not None:
        config.hw_video = hw_video

    autosave = flag("RAR_AUTOSAVE")
    if autosave is not None:
        config.has_autosave = autosave
        config.autosave_on_choice = autosave


# Commands from the on-screen keys (GameActivity.pollCommand), for actions Ren'Py
# has no default key for. A thread polls for them and has Ren'Py run them on its
# main thread, the only one that may.
#
# Up to 8.6.0, this thread also stopped Ren'Py's timers when SDL paused, so that
# Android_PumpEvents could deliver SDL_EVENT_WILL_ENTER_BACKGROUND (renpy/renpy#7534).
# Ren'Py 8.6.1 suspends the timers itself.
init -1500 python hide:
    import threading

    def poll_commands(GameActivity):
        import sys
        import time

        while True:
            time.sleep(0.1)

            try:
                command = GameActivity.pollCommand()
            except Exception:
                time.sleep(1)
                continue

            if command:
                # Looked up each time: Ren'Py reloads its modules on a full restart,
                # while this thread lives on.
                store = sys.modules["store"]
                try:
                    store.renpy.invoke_in_main_thread(store._rar_run_command, command)
                except Exception:
                    # Still in the init phase, or restarting.
                    pass

    if renpy.android and not any(t.name == "rar-commands" for t in threading.enumerate()):
        # Look the class up here: threads started from Python only see the system
        # class loader, not the app's.
        from jnius import autoclass
        GameActivity = autoclass("studio.tsatoru.renpy86.GameActivity")
        threading.Thread(target=poll_commands, args=(GameActivity,), name="rar-commands", daemon=True).start()


# Runs a command from the virtual keys (see above). The keys that Ren'Py has a
# default binding for send that key instead, so that the game's keymap applies.
init -1500 python:
    def _rar_run_command(command):
        if command == "auto":
            renpy.run(Preference("auto-forward", "toggle"))
        elif command == "quick_save":
            renpy.run(QuickSave())
        elif command == "quick_load":
            renpy.run(QuickLoad())
        elif command == "mute":
            renpy.run(Preference("all mute", "toggle"))
        elif command == "quit":
            renpy.quit(save=True)
        elif command == "history":
            if renpy.has_screen("history"):
                renpy.run(ShowMenu("history"))
        renpy.restart_interaction()
