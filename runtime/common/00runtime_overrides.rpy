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


# Let Ren'Py see that it was sent to the background.
#
# When the app pauses, SDL queues SDL_EVENT_WILL_ENTER_BACKGROUND, which Ren'Py
# answers by saving and telling RAPT's onStop it may finish (Interface.
# check_suspend). But SDL 3's Android_PumpEvents only returns after a lifecycle
# wait times out with nothing arriving, and while paused every timer event wakes
# that wait again. Ren'Py's PERIODIC timer fires every 50 ms, so the event never
# reaches Ren'Py until the app resumes, and onStop blocks the main thread for its
# 8 s limit. Stopping the timers as SDL pauses, which check_suspend would do first
# anyway, lets the wait time out. check_suspend restarts them in the foreground.
# Needs SDL_ANDROID_BLOCK_ON_PAUSE=0, which librenpython sets before SDL starts.
init -1500 python hide:
    import threading

    def watch_pause(GameActivity):
        import sys
        import time

        was_paused = False

        while True:
            time.sleep(0.1)

            try:
                paused = bool(GameActivity.isNativePaused())
            except Exception:
                time.sleep(1)
                continue

            if paused and not was_paused:
                # Looked up each time: Ren'Py reloads its modules on a full restart,
                # while this thread lives on.
                core = sys.modules["renpy.display.core"]
                set_timer = sys.modules["renpy.pygame.time"].set_timer
                for event in (core.PERIODIC, core.REDRAW, core.TIMEEVENT):
                    set_timer(event, 0)

            was_paused = paused

    if renpy.android and not any(t.name == "rar-pause-watch" for t in threading.enumerate()):
        # Look the class up here: threads started from Python only see the system
        # class loader, not the app's.
        from jnius import autoclass
        GameActivity = autoclass("studio.tsatoru.renpy86.GameActivity")
        threading.Thread(target=watch_pause, args=(GameActivity,), name="rar-pause-watch", daemon=True).start()
