// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class LaunchRequestTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // The payload launchers build for JoiPlay's Ren'Py plugin.
    private static final String JOIPLAY_GAME =
            "{\"title\":\"Koi\",\"id\":\"1a2b\",\"folder\":\"/sdcard/Games/koi/\",\"execFile\":\"\",\"type\":\"renpy\"}";
    private static final String JOIPLAY_SETTINGS = "{\"app\":{\"cheats\":{\"boolean\":false}},"
            + "\"renpy\":{\"renpy_hw_video\":{\"boolean\":true},\"renpy_autosave\":{\"boolean\":false},"
            + "\"renpy_phonesmallvariant\":{\"boolean\":false},\"renpy_vsync\":{\"boolean\":true},"
            + "\"renpy_less_memory\":{\"boolean\":true},\"renpy_less_updates\":{\"boolean\":false},"
            + "\"renpy_dont_use_gl2\":{\"boolean\":false},\"renpy_recompile\":{\"boolean\":true}}}";

    @Test
    public void parsesJoiPlayPayload() throws Exception {
        LaunchRequest r = LaunchRequest.fromExtras(JOIPLAY_GAME, JOIPLAY_SETTINGS, null, null, null, null);

        assertEquals("/sdcard/Games/koi", r.folder);
        assertEquals("Koi", r.title);
        assertEquals("1a2b", r.id);
        assertEquals("/sdcard/Games/koi/saves", r.saveDir);
        assertEquals(Boolean.TRUE, r.settings.hwVideo);
        assertEquals(Boolean.FALSE, r.settings.autosave);
        assertEquals(Boolean.FALSE, r.settings.phoneSmallVariant);
        assertEquals(Boolean.TRUE, r.settings.vsync);
        assertEquals(Boolean.TRUE, r.settings.lessMemory);
        assertEquals(Boolean.FALSE, r.settings.lessUpdates);
        assertEquals(Boolean.TRUE, r.settings.recompile);
    }

    @Test
    public void parsesPlainExtrasAndBareBooleans() throws Exception {
        LaunchRequest r = LaunchRequest.fromExtras(null, "{\"renpy_less_updates\":true}",
                "/storage/emulated/0/vn", null, null, "/storage/emulated/0/vn-saves/");

        assertEquals("/storage/emulated/0/vn", r.folder);
        assertEquals("vn", r.title);
        assertEquals(Integer.toHexString("/storage/emulated/0/vn".hashCode()), r.id);
        assertEquals("/storage/emulated/0/vn-saves", r.saveDir);
        assertEquals(Boolean.TRUE, r.settings.lessUpdates);
        assertNull(r.settings.lessMemory);
    }

    @Test(expected = LaunchRequest.InvalidRequestException.class)
    public void rejectsMissingFolder() throws Exception {
        LaunchRequest.fromExtras("{\"title\":\"x\"}", null, null, null, null, null);
    }

    @Test(expected = LaunchRequest.InvalidRequestException.class)
    public void rejectsRelativeFolder() throws Exception {
        LaunchRequest.fromExtras(null, null, "Games/koi", null, null, null);
    }

    @Test(expected = LaunchRequest.InvalidRequestException.class)
    public void rejectsMalformedJson() throws Exception {
        LaunchRequest.fromExtras("{not json", null, null, null, null, null);
    }

    @Test
    public void sanitizesIdForUseAsDirectoryName() throws Exception {
        LaunchRequest r = LaunchRequest.fromExtras(null, null, "/g", null, "../evil/id", null);
        assertFalse(r.id.contains("/"));
        assertFalse(r.id.startsWith("."));
    }

    @Test
    public void roundTripsThroughJson() throws Exception {
        LaunchRequest r = LaunchRequest.fromExtras(JOIPLAY_GAME, JOIPLAY_SETTINGS, null, null, null, null);
        LaunchRequest back = LaunchRequest.fromJson(r.toJson());

        assertEquals(r, back);
        assertEquals(r.settings.recompile, back.settings.recompile);
        assertEquals(r.settings.autosave, back.settings.autosave);
        assertEquals(r.settings.phoneSmallVariant, back.settings.phoneSmallVariant);
        assertEquals(r.settings.hwVideo, back.settings.hwVideo);
    }

    @Test
    public void resolvesBaseDirFromEitherLevel() throws Exception {
        File base = tmp.newFolder("koi");
        File game = new File(base, "game");
        assertTrue(game.mkdir());

        assertEquals(base, LaunchRequest.resolveBaseDir(base));
        assertEquals(base, LaunchRequest.resolveBaseDir(game));
        assertNull(LaunchRequest.resolveBaseDir(tmp.newFolder("empty")));
    }

    @Test
    public void withFolderKeepsCustomSaveDir() throws Exception {
        LaunchRequest defaults = LaunchRequest.fromExtras(null, null, "/g/game", null, null, null);
        assertEquals("/g/saves", defaults.withFolder("/g").saveDir);

        LaunchRequest custom = LaunchRequest.fromExtras(null, null, "/g/game", null, null, "/s");
        assertEquals("/s", custom.withFolder("/g").saveDir);
    }
}
