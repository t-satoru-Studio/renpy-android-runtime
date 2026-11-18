// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Objects;

/**
 * A request to run one game, parsed from the launch intent of the Tyranor runtime module
 * protocol (see PROTOCOL.md): a game folder, an optional title and an optional
 * {@code {"version": 1, "values": {...}}} settings payload whose keys are declared in
 * {@code res/raw/tyranor_runtime.json}.
 */
public final class LaunchRequest {

    /** Version of the settings payload this runtime understands. */
    static final int SETTINGS_VERSION = 1;

    /** Settings the launcher can override. {@code null} means "leave Ren'Py's or the game's default". */
    public static final class Settings {
        /** Enum values of {@link #KEY_HW_VIDEO} and {@link #KEY_AUTOSAVE}. */
        static final String FOLLOW_GAME = "game";
        static final String ON = "on";
        static final String OFF = "off";

        static final String KEY_HW_VIDEO = "hw_video";
        static final String KEY_AUTOSAVE = "autosave";
        static final String KEY_PHONE_SMALL_VARIANT = "phone_small_variant";
        static final String KEY_VSYNC = "vsync";
        static final String KEY_LESS_MEMORY = "less_memory";
        static final String KEY_LESS_UPDATES = "less_updates";
        static final String KEY_RECOMPILE = "recompile";
        static final String KEY_VIRTUAL_KEYS = "virtual_keys";
        static final String KEY_GAMEPAD = "gamepad";
        static final String KEY_UPDATE_CHECK = "update_check";

        public final Boolean hwVideo;
        public final Boolean autosave;
        public final Boolean phoneSmallVariant;
        public final Boolean vsync;
        public final Boolean lessMemory;
        public final Boolean lessUpdates;
        public final Boolean recompile;
        /** On-screen keys; {@code null} means shown, the declared default. */
        public final Boolean virtualKeys;
        /** JoiPlay's virtual gamepad; {@code null} means shown, the declared default. */
        public final Boolean gamepad;
        /** Checking GitHub for a newer runtime from games; {@code null} means on. */
        public final Boolean updateCheck;

        public Settings(Boolean hwVideo, Boolean autosave, Boolean phoneSmallVariant, Boolean vsync,
                        Boolean lessMemory, Boolean lessUpdates, Boolean recompile,
                        Boolean virtualKeys, Boolean gamepad, Boolean updateCheck) {
            this.hwVideo = hwVideo;
            this.autosave = autosave;
            this.phoneSmallVariant = phoneSmallVariant;
            this.vsync = vsync;
            this.lessMemory = lessMemory;
            this.lessUpdates = lessUpdates;
            this.recompile = recompile;
            this.virtualKeys = virtualKeys;
            this.gamepad = gamepad;
            this.updateCheck = updateCheck;
        }

        static final Settings NONE = new Settings(null, null, null, null, null, null, null, null, null, null);

        /**
         * Reads the {@code values} object. Unknown keys are ignored, and a known key with a
         * value of the wrong type keeps its default, as the module protocol asks.
         */
        static Settings fromValues(JSONObject values) {
            if (values == null) {
                return NONE;
            }
            return new Settings(
                    choice(values, KEY_HW_VIDEO),
                    choice(values, KEY_AUTOSAVE),
                    flag(values, KEY_PHONE_SMALL_VARIANT),
                    flag(values, KEY_VSYNC),
                    flag(values, KEY_LESS_MEMORY),
                    flag(values, KEY_LESS_UPDATES),
                    flag(values, KEY_RECOMPILE),
                    flag(values, KEY_VIRTUAL_KEYS),
                    flag(values, KEY_GAMEPAD),
                    flag(values, KEY_UPDATE_CHECK));
        }

        /**
         * Parses a whole settings payload. A payload that is not JSON or has another
         * version is ignored as a whole, so the game runs with its defaults.
         */
        static Settings fromPayload(String payload) {
            if (payload == null) {
                return NONE;
            }
            JSONObject root;
            try {
                root = new JSONObject(payload);
            } catch (JSONException e) {
                return NONE;
            }
            Object version = root.opt("version");
            if (!(version instanceof Integer) || (Integer) version != SETTINGS_VERSION) {
                return NONE;
            }
            return fromValues(root.optJSONObject("values"));
        }

        private static Boolean flag(JSONObject values, String key) {
            Object value = values.opt(key);
            return value instanceof Boolean ? (Boolean) value : null;
        }

        private static Boolean choice(JSONObject values, String key) {
            Object value = values.opt(key);
            if (ON.equals(value)) return Boolean.TRUE;
            if (OFF.equals(value)) return Boolean.FALSE;
            return null;
        }

        JSONObject toValues() throws JSONException {
            JSONObject o = new JSONObject();
            putChoice(o, KEY_HW_VIDEO, hwVideo);
            putChoice(o, KEY_AUTOSAVE, autosave);
            putFlag(o, KEY_PHONE_SMALL_VARIANT, phoneSmallVariant);
            putFlag(o, KEY_VSYNC, vsync);
            putFlag(o, KEY_LESS_MEMORY, lessMemory);
            putFlag(o, KEY_LESS_UPDATES, lessUpdates);
            putFlag(o, KEY_RECOMPILE, recompile);
            putFlag(o, KEY_VIRTUAL_KEYS, virtualKeys);
            putFlag(o, KEY_GAMEPAD, gamepad);
            putFlag(o, KEY_UPDATE_CHECK, updateCheck);
            return o;
        }

        private static void putFlag(JSONObject o, String key, Boolean value) throws JSONException {
            if (value != null) {
                o.put(key, value.booleanValue());
            }
        }

        private static void putChoice(JSONObject o, String key, Boolean value) throws JSONException {
            if (value != null) {
                o.put(key, value ? ON : OFF);
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
    /** {@code <folder>/saves}, the directory the module declares to Tyranor. */
    public final String saveDir;
    public final Settings settings;

    private LaunchRequest(String folder, String title, String id, Settings settings) {
        this.folder = folder;
        this.title = title;
        this.id = id;
        this.saveDir = folder + "/saves";
        this.settings = settings;
    }

    /** Parses the launch intent's extras. Any argument may be {@code null}. */
    public static LaunchRequest fromExtras(String folderPath, String title, String settingsPayload)
            throws InvalidRequestException {
        return build(folderPath, title, null, Settings.fromPayload(settingsPayload));
    }

    static LaunchRequest build(String folder, String title, String id, Settings settings)
            throws InvalidRequestException {
        folder = normalizePath(folder);
        if (folder == null) {
            throw new InvalidRequestException("no game folder in the request");
        }
        if (!folder.startsWith("/")) {
            throw new InvalidRequestException("game folder is not an absolute path: " + folder);
        }

        String resolvedId = sanitizeId(firstNonBlank(id, Integer.toHexString(folder.hashCode())));
        String resolvedTitle = firstNonBlank(title, new File(folder).getName());
        return new LaunchRequest(folder, resolvedTitle, resolvedId,
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
        return new LaunchRequest(newFolder, title, id, settings);
    }

    /** Serialization between LaunchActivity and GameActivity. */
    public String toJson() {
        try {
            return new JSONObject()
                    .put("folder", folder)
                    .put("title", title)
                    .put("id", id)
                    .put("values", settings.toValues())
                    .toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static LaunchRequest fromJson(String json) throws InvalidRequestException {
        if (json == null) {
            throw new InvalidRequestException("no request");
        }
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (JSONException e) {
            throw new InvalidRequestException("request is not a JSON object");
        }
        return build(o.optString("folder", null), o.optString("title", null),
                o.optString("id", null), Settings.fromValues(o.optJSONObject("values")));
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
        return folder.equals(r.folder) && id.equals(r.id) && Objects.equals(title, r.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folder, id, title);
    }
}
