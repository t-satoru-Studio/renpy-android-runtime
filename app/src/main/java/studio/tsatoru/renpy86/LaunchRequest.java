// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Objects;

/**
 * A request to run one game, parsed from the launch intent.
 *
 * <p>Two input forms are accepted (see PROTOCOL.md):
 * <ul>
 *   <li>JoiPlay-compatible: a {@code game} JSON string with {@code folder}, {@code title}
 *   and {@code id}, and a {@code settings} JSON string whose {@code renpy} object holds
 *   {@code renpy_*} switches, either bare booleans or {@code {"boolean": ...}};</li>
 *   <li>plain extras: {@code folder}, optional {@code title}, {@code id}, {@code savedir}.</li>
 * </ul>
 */
public final class LaunchRequest {

    /** Settings the launcher can override. {@code null} means "leave Ren'Py's default". */
    public static final class Settings {
        public final Boolean hwVideo;
        public final Boolean autosave;
        public final Boolean phoneSmallVariant;
        public final Boolean vsync;
        public final Boolean lessMemory;
        public final Boolean lessUpdates;
        public final Boolean recompile;

        public Settings(Boolean hwVideo, Boolean autosave, Boolean phoneSmallVariant, Boolean vsync,
                        Boolean lessMemory, Boolean lessUpdates, Boolean recompile) {
            this.hwVideo = hwVideo;
            this.autosave = autosave;
            this.phoneSmallVariant = phoneSmallVariant;
            this.vsync = vsync;
            this.lessMemory = lessMemory;
            this.lessUpdates = lessUpdates;
            this.recompile = recompile;
        }

        static final Settings NONE = new Settings(null, null, null, null, null, null, null);

        static Settings parse(JSONObject root) {
            if (root == null) {
                return NONE;
            }
            JSONObject renpy = root.optJSONObject("renpy");
            JSONObject src = renpy != null ? renpy : root;
            return new Settings(
                    flag(src, "renpy_hw_video"),
                    flag(src, "renpy_autosave"),
                    flag(src, "renpy_phonesmallvariant"),
                    flag(src, "renpy_vsync"),
                    flag(src, "renpy_less_memory"),
                    flag(src, "renpy_less_updates"),
                    flag(src, "renpy_recompile"));
        }

        private static Boolean flag(JSONObject src, String key) {
            Object value = src.opt(key);
            if (value instanceof JSONObject) {
                value = ((JSONObject) value).opt("boolean");
            }
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value instanceof String) {
                String s = (String) value;
                if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
                if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
            }
            return null;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            put(o, "renpy_hw_video", hwVideo);
            put(o, "renpy_autosave", autosave);
            put(o, "renpy_phonesmallvariant", phoneSmallVariant);
            put(o, "renpy_vsync", vsync);
            put(o, "renpy_less_memory", lessMemory);
            put(o, "renpy_less_updates", lessUpdates);
            put(o, "renpy_recompile", recompile);
            return new JSONObject().put("renpy", o);
        }

        private static void put(JSONObject o, String key, Boolean value) throws JSONException {
            if (value != null) {
                o.put(key, value.booleanValue());
            }
        }
    }

    /** The request could not be understood; the message is for logs, not users. */
    public static final class InvalidRequestException extends Exception {
        public InvalidRequestException(String message) {
            super(message);
        }
    }

    /** Absolute path of the game's base directory (the one containing {@code game/}). */
    public final String folder;
    public final String title;
    /** Stable identifier, used for the log directory. */
    public final String id;
    /** Save directory; {@code <folder>/saves} unless the caller chose another. */
    public final String saveDir;
    public final Settings settings;

    LaunchRequest(String folder, String title, String id, String saveDir, Settings settings) {
        this.folder = folder;
        this.title = title;
        this.id = id;
        this.saveDir = saveDir;
        this.settings = settings;
    }

    /** Parses the launch intent's extras. Any argument may be {@code null}. */
    public static LaunchRequest fromExtras(String gameJson, String settingsJson, String folderExtra,
                                           String titleExtra, String idExtra, String saveDirExtra)
            throws InvalidRequestException {
        String folder = folderExtra;
        String title = titleExtra;
        String id = idExtra;

        if (gameJson != null) {
            JSONObject game = parseObject(gameJson, "game");
            folder = firstNonBlank(game.optString("folder", null), folder);
            title = firstNonBlank(game.optString("title", null), title);
            id = firstNonBlank(game.optString("id", null), id);
        }

        Settings settings = settingsJson != null
                ? Settings.parse(parseObject(settingsJson, "settings"))
                : Settings.NONE;

        return build(folder, title, id, saveDirExtra, settings);
    }

    static LaunchRequest build(String folder, String title, String id, String saveDir,
                               Settings settings) throws InvalidRequestException {
        folder = normalizePath(folder);
        if (folder == null) {
            throw new InvalidRequestException("no game folder in the request");
        }
        if (!folder.startsWith("/")) {
            throw new InvalidRequestException("game folder is not an absolute path: " + folder);
        }

        String resolvedId = sanitizeId(firstNonBlank(id, Integer.toHexString(folder.hashCode())));
        String resolvedTitle = firstNonBlank(title, new File(folder).getName());
        String resolvedSaveDir = normalizePath(saveDir);
        if (resolvedSaveDir == null) {
            resolvedSaveDir = folder + "/saves";
        } else if (!resolvedSaveDir.startsWith("/")) {
            throw new InvalidRequestException("save directory is not an absolute path: " + saveDir);
        }

        return new LaunchRequest(folder, resolvedTitle, resolvedId, resolvedSaveDir,
                settings == null ? Settings.NONE : settings);
    }

    /**
     * Returns the base directory to run, or {@code null} if {@code folder} holds no game.
     * Accepts both the base directory and its {@code game/} subdirectory.
     */
    public static File resolveBaseDir(File folder) {
        if (new File(folder, "game").isDirectory()) {
            return folder;
        }
        File parent = folder.getParentFile();
        if (folder.getName().equals("game") && folder.isDirectory() && parent != null) {
            return parent;
        }
        return null;
    }

    /** Returns this request with another base directory, keeping everything else. */
    public LaunchRequest withFolder(String newFolder) {
        boolean defaultSaves = saveDir.equals(folder + "/saves");
        return new LaunchRequest(newFolder, title, id,
                defaultSaves ? newFolder + "/saves" : saveDir, settings);
    }

    /** Serialization between LaunchActivity and GameActivity. */
    public String toJson() {
        try {
            return new JSONObject()
                    .put("folder", folder)
                    .put("title", title)
                    .put("id", id)
                    .put("savedir", saveDir)
                    .put("settings", settings.toJson())
                    .toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static LaunchRequest fromJson(String json) throws InvalidRequestException {
        if (json == null) {
            throw new InvalidRequestException("no request");
        }
        JSONObject o = parseObject(json, "request");
        return build(o.optString("folder", null), o.optString("title", null),
                o.optString("id", null), o.optString("savedir", null),
                Settings.parse(o.optJSONObject("settings")));
    }

    private static JSONObject parseObject(String json, String what) throws InvalidRequestException {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new InvalidRequestException(what + " is not a JSON object");
        }
    }

    static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String p = path.trim();
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.isEmpty() ? null : p;
    }

    static String sanitizeId(String id) {
        String s = id.replaceAll("[^A-Za-z0-9._-]", "_");
        if (s.startsWith(".")) {
            s = "_" + s.substring(1);
        }
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LaunchRequest)) return false;
        LaunchRequest r = (LaunchRequest) o;
        return folder.equals(r.folder) && saveDir.equals(r.saveDir) && id.equals(r.id)
                && Objects.equals(title, r.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folder, saveDir, id, title);
    }
}
