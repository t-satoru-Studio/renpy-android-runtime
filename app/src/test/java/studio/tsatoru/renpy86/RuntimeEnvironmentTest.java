// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.File;
import java.util.Map;

public class RuntimeEnvironmentTest {

    private static final File LOGS = new File("/data/logs/x");
    private static final File NO_OLD = new File("/data/files/no-old-public");

    private static Map<String, String> envFor(String settingsJson) throws Exception {
        LaunchRequest r = LaunchRequest.fromExtras(null, settingsJson, "/sdcard/koi", null, "x", null);
        return RuntimeEnvironment.build(r, LOGS, NO_OLD);
    }

    @Test
    public void alwaysSetsGameAndDirectories() throws Exception {
        Map<String, String> env = envFor(null);

        assertEquals("/sdcard/koi", env.get("RAR_BASEDIR"));
        assertEquals("/sdcard/koi/saves", env.get("RAR_SAVEDIR"));
        assertEquals("/data/logs/x", env.get("ANDROID_PUBLIC"));
        assertEquals("/data/files/no-old-public", env.get("ANDROID_OLD_PUBLIC"));
        // Nothing else when no setting was given: Ren'Py and the game decide.
        assertEquals(4, env.size());
    }

    @Test
    public void sdlHintsKeepRenPyRunningInTheBackgroundAndTrapBack() {
        Map<String, String> hints = RuntimeEnvironment.sdlHints();

        assertEquals("0", hints.get("SDL_ANDROID_BLOCK_ON_PAUSE"));
        assertEquals("1", hints.get("SDL_ANDROID_TRAP_BACK_BUTTON"));
    }

    @Test
    public void mapsEnabledSwitches() throws Exception {
        Map<String, String> env = envFor("{\"renpy\":{"
                + "\"renpy_less_memory\":true,\"renpy_less_updates\":true,\"renpy_vsync\":false,"
                + "\"renpy_phonesmallvariant\":true,\"renpy_recompile\":true,"
                + "\"renpy_hw_video\":true,\"renpy_autosave\":false}}");

        assertEquals("1", env.get("RENPY_LESS_MEMORY"));
        assertEquals("1", env.get("RENPY_LESS_UPDATES"));
        assertEquals("0", env.get("RENPY_GL_VSYNC"));
        assertEquals(RuntimeEnvironment.PHONE_VARIANTS, env.get("RENPY_VARIANT"));
        assertEquals("1", env.get("RAR_COMPILE"));
        assertEquals("1", env.get("RAR_HW_VIDEO"));
        assertEquals("0", env.get("RAR_AUTOSAVE"));
    }

    @Test
    public void disabledSwitchesLeaveRenPyDefaults() throws Exception {
        Map<String, String> env = envFor("{\"renpy\":{"
                + "\"renpy_less_memory\":false,\"renpy_less_updates\":false,\"renpy_vsync\":true,"
                + "\"renpy_phonesmallvariant\":false,\"renpy_recompile\":false}}");

        // Ren'Py tests these for presence, so "off" must mean "not set".
        assertFalse(env.containsKey("RENPY_LESS_MEMORY"));
        assertFalse(env.containsKey("RENPY_LESS_UPDATES"));
        assertFalse(env.containsKey("RENPY_GL_VSYNC"));
        assertFalse(env.containsKey("RENPY_VARIANT"));
        assertFalse(env.containsKey("RAR_COMPILE"));
    }
}
