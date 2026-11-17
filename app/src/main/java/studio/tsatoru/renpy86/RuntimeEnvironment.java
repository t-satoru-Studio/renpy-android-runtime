// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Environment variables handed to Ren'Py before Python starts.
 *
 * <p>{@code RAR_*} variables are read by our main.py prelude and common override script;
 * {@code RENPY_*} variables are Ren'Py's own; {@code ANDROID_*} replace values RAPT's
 * PythonSDLActivity set for an APK with a bundled game.
 */
public final class RuntimeEnvironment {

    /** The variants Ren'Py itself picks for a phone (renpy/main.py, choose_variants). */
    static final String PHONE_VARIANTS = "small phone touch android mobile";

    private RuntimeEnvironment() {
    }

    /**
     * @param request   the game to run
     * @param logDir    where Ren'Py writes log.txt and traceback.txt (ANDROID_PUBLIC)
     * @param noOldPublic a path that must never exist; Ren'Py would otherwise prefer
     *                  {@code <ANDROID_OLD_PUBLIC>/game/saves} over the save directory
     *                  it was told to use on some code paths
     */
    public static Map<String, String> build(LaunchRequest request, File logDir, File noOldPublic) {
        Map<String, String> env = new LinkedHashMap<>();
        LaunchRequest.Settings s = request.settings;

        env.put("RAR_BASEDIR", request.folder);
        env.put("RAR_SAVEDIR", request.saveDir);
        if (Boolean.TRUE.equals(s.recompile)) {
            env.put("RAR_COMPILE", "1");
        }

        env.put("ANDROID_PUBLIC", logDir.getAbsolutePath());
        env.put("ANDROID_OLD_PUBLIC", noOldPublic.getAbsolutePath());

        if (Boolean.TRUE.equals(s.lessMemory)) {
            env.put("RENPY_LESS_MEMORY", "1");
        }
        if (Boolean.TRUE.equals(s.lessUpdates)) {
            env.put("RENPY_LESS_UPDATES", "1");
        }
        if (Boolean.FALSE.equals(s.vsync)) {
            env.put("RENPY_GL_VSYNC", "0");
        }
        if (Boolean.TRUE.equals(s.phoneSmallVariant)) {
            env.put("RENPY_VARIANT", PHONE_VARIANTS);
        }

        putFlag(env, "RAR_HW_VIDEO", s.hwVideo);
        putFlag(env, "RAR_AUTOSAVE", s.autosave);
        return env;
    }

    /**
     * SDL hints, given as environment variables. SDL copies the process environment
     * once, when its main thread starts (SDL_InitMainThread), so these must be set
     * before PythonSDLActivity.onCreate, not in preparePython like the rest.
     */
    public static Map<String, String> sdlHints() {
        Map<String, String> hints = new LinkedHashMap<>();
        // Ren'Py maps the back key to rollback (K_AC_BACK in 00keymap.rpy), but SDL
        // finishes the activity on back unless this hint is set. Android 13 and later
        // use GameActivity's back callback instead.
        //
        // SDL_ANDROID_BLOCK_ON_PAUSE=0 is not needed here: librenpython's SDL_main sets
        // it before SDL starts.
        hints.put("SDL_ANDROID_TRAP_BACK_BUTTON", "1");
        return hints;
    }

    private static void putFlag(Map<String, String> env, String name, Boolean value) {
        if (value != null) {
            env.put(name, value ? "1" : "0");
        }
    }
}
