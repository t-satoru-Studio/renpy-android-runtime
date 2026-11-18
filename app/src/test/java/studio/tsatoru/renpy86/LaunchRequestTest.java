// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class LaunchRequestTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // A payload as Tyranor sends it: every declared key, with its effective value.
    private static final String FULL_SETTINGS = "{\"version\":1,\"values\":{"
            + "\"hw_video\":\"on\",\"autosave\":\"off\",\"phone_small_variant\":false,"
            + "\"vsync\":true,\"less_memory\":true,\"less_updates\":false,\"recompile\":true,"
            + "\"virtual_keys\":false,\"gamepad\":false,\"update_check\":false}}";

    // The declared defaults.
    private static final String DEFAULT_SETTINGS = "{\"version\":1,\"values\":{"
            + "\"hw_video\":\"game\",\"autosave\":\"game\",\"phone_small_variant\":false,"
            + "\"vsync\":true,\"less_memory\":false,\"less_updates\":false,\"recompile\":false,"
            + "\"virtual_keys\":true,\"gamepad\":true,\"update_check\":true}}";

    @Test
    public void parsesModuleExtras() throws Exception {
        LaunchRequest r = LaunchRequest.fromExtras("/sdcard/Games/koi/", "Koi", FULL_SETTINGS);

        assertEquals("/sdcard/Games/koi", r.folder);
        assertEquals("Koi", r.title);
        assertEquals(Integer.toHexString("/sdcard/Games/koi".hashCode()), r.id);
        assertEquals("/sdcard/Games/koi/saves", r.saveDir);
        assertEquals(Boolean.TRUE, r.settings.hwVideo);
        assertEquals(Boolean.FALSE, r.settings.autosave);
        assertEquals(Boolean.FALSE, r.settings.phoneSmallVariant);
        assertEquals(Boolean.TRUE, r.settings.vsync);
        assertEquals(Boolean.TRUE, r.settings.lessMemory);
        assertEquals(Boolean.FALSE, r.settings.lessUpdates);
        assertEquals(Boolean.TRUE, r.settings.recompile);
        assertEquals(Boolean.FALSE, r.settings.virtualKeys);
        assertEquals(Boolean.FALSE, r.settings.gamepad);
        assertEquals(Boolean.FALSE, r.settings.updateCheck);
    }

    @Test
    public void virtualKeysSurviveTheHandOverToGameActivity() throws Exception {
        LaunchRequest r = LaunchRequest.fromExtras("/g", null, FULL_SETTINGS);
        assertEquals(Boolean.FALSE, LaunchRequest.fromJson(r.toJson()).settings.virtualKeys);
        assertNull(LaunchRequest.fromExtras("/g", null, null).settings.virtualKeys);
    }

    @Test
    public void followGameMeansNoOverride() throws Exception {
        LaunchRequest r = LaunchRequest.fromExtras("/g", null, DEFAULT_SETTINGS);

        assertNull(r.settings.hwVideo);
        assertNull(r.settings.autosave);
    }

    @Test
    public void titleDefaultsToFolderName() throws Exception {
        assertEquals("vn", LaunchRequest.fromExtras("/storage/emulated/0/vn", " ", null).title);
    }

    @Test
    public void ignoresUnusableSettingsPayloads() throws Exception {
        for (String payload : new String[]{null, "{not json", "{\"version\":2,\"values\":{\"recompile\":true}}",
                "{\"version\":\"1\",\"values\":{\"recompile\":true}}"}) {
            assertSame(payload, LaunchRequest.Settings.NONE, LaunchRequest.fromExtras("/g", null, payload).settings);
        }
    }

    @Test
    public void wrongTypedOrUnknownValuesKeepDefaults() throws Exception {
        LaunchRequest r = LaunchRequest.fromExtras("/g", null, "{\"version\":1,\"values\":{"
                + "\"recompile\":\"true\",\"hw_video\":true,\"autosave\":\"maybe\",\"extra\":1}}");

        assertNull(r.settings.recompile);
        assertNull(r.settings.hwVideo);
        assertNull(r.settings.autosave);
    }

    @Test(expected = LaunchRequest.InvalidRequestException.class)
    public void rejectsMissingFolder() throws Exception {
        LaunchRequest.fromExtras(null, "x", null);
    }

    @Test(expected = LaunchRequest.InvalidRequestException.class)
    public void rejectsRelativeFolder() throws Exception {
        LaunchRequest.fromExtras("Games/koi", null, null);
    }

    @Test
    public void sanitizesIdForUseAsDirectoryName() throws Exception {
        LaunchRequest r = LaunchRequest.build("/g", null, "../evil/id", null);
        assertFalse(r.id.contains("/"));
        assertFalse(r.id.startsWith("."));
    }

    @Test
    public void roundTripsThroughJson() throws Exception {
        LaunchRequest r = LaunchRequest.fromExtras("/sdcard/Games/koi", "Koi", FULL_SETTINGS);
        LaunchRequest back = LaunchRequest.fromJson(r.toJson());

        assertEquals(r, back);
        assertEquals(r.saveDir, back.saveDir);
        assertEquals(r.settings.hwVideo, back.settings.hwVideo);
        assertEquals(r.settings.autosave, back.settings.autosave);
        assertEquals(r.settings.phoneSmallVariant, back.settings.phoneSmallVariant);
        assertEquals(r.settings.vsync, back.settings.vsync);
        assertEquals(r.settings.lessMemory, back.settings.lessMemory);
        assertEquals(r.settings.lessUpdates, back.settings.lessUpdates);
        assertEquals(r.settings.recompile, back.settings.recompile);

        LaunchRequest plain = LaunchRequest.fromJson(LaunchRequest.fromExtras("/g", null, DEFAULT_SETTINGS).toJson());
        assertNull(plain.settings.hwVideo);
        assertNull(plain.settings.autosave);
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
    public void withFolderMovesSaves() throws Exception {
        LaunchRequest r = LaunchRequest.fromExtras("/g/game", null, null);
        LaunchRequest moved = r.withFolder("/g");

        assertEquals("/g/saves", moved.saveDir);
        assertEquals(r.id, moved.id);
    }
}
