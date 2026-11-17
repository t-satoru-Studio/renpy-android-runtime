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
