// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Checks res/raw/tyranor_runtime.json against what the runtime reads. */
public class ModuleDescriptorTest {

    private static JSONObject descriptor() throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/main/res/raw/tyranor_runtime.json"));
        // Tyranor reads at most 64 KiB.
        assertTrue(bytes.length <= 64 * 1024);
        return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    public void scansOnlyRenPy86AndLaterGames() throws Exception {
        JSONObject scan = descriptor().getJSONObject("scan");
        assertEquals("all", scan.getString("match"));
        // renpy/asynctask.py is new in Ren'Py 8.6.0; 8.5 and earlier games stay with
        // their own runtimes.
        JSONArray markers = scan.getJSONArray("markers");
        assertEquals(new HashSet<>(Arrays.asList("game", "renpy/asynctask.py*")), toSet(markers));
    }

    @Test
    public void declaresTheSettingsTheRuntimeReads() throws Exception {
        JSONObject settings = descriptor().getJSONObject("settings");
        assertEquals(1, settings.getInt("version"));

        JSONArray items = settings.getJSONArray("items");
        Set<String> keys = new HashSet<>();
        JSONObject defaults = new JSONObject();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            keys.add(item.getString("key"));
            assertTrue(item.getString("title").length() <= 128);
            defaults.put(item.getString("key"), item.get("default"));
            if (item.getString("type").equals("enum")) {
                Set<String> values = new HashSet<>();
                JSONArray options = item.getJSONArray("options");
                for (int j = 0; j < options.length(); j++) {
                    values.add(options.getJSONObject(j).getString("value"));
                }
                assertEquals(new HashSet<>(Arrays.asList("game", "on", "off")), values);
            }
        }
        assertEquals(new HashSet<>(Arrays.asList(
                LaunchRequest.Settings.KEY_HW_VIDEO, LaunchRequest.Settings.KEY_AUTOSAVE,
                LaunchRequest.Settings.KEY_PHONE_SMALL_VARIANT, LaunchRequest.Settings.KEY_VSYNC,
                LaunchRequest.Settings.KEY_LESS_MEMORY, LaunchRequest.Settings.KEY_LESS_UPDATES,
                LaunchRequest.Settings.KEY_RECOMPILE, LaunchRequest.Settings.KEY_VIRTUAL_KEYS,
                LaunchRequest.Settings.KEY_GAMEPAD,
                LaunchRequest.Settings.KEY_UPDATE_CHECK)), keys);

        // The defaults must change nothing: the game runs as Ren'Py and its own code set it up.
        LaunchRequest r = LaunchRequest.fromExtras("/g", null,
                new JSONObject().put("version", 1).put("values", defaults).toString());
        assertEquals(4, RuntimeEnvironment.build(r, new java.io.File("/l"), new java.io.File("/n")).size());
        assertNull(r.settings.hwVideo);
        assertNull(r.settings.autosave);
    }

    @Test
    public void declaresTheSaveDirectoryRenPyUses() throws Exception {
        JSONObject saves = descriptor().getJSONObject("saves");
        assertEquals(1, saves.getInt("version"));
        String dir = saves.getString("directory");
        assertTrue(LaunchRequest.fromExtras("/g", null, null).saveDir.endsWith("/" + dir));
    }

    private static Set<String> toSet(JSONArray array) throws Exception {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            set.add(array.getString(i));
        }
        return set;
    }
}
