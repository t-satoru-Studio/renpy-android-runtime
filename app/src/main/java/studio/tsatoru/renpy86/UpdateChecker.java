// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks for a newer release of this runtime on GitHub. Tyranor Next checks only that a module
 * is new enough to use, not whether a newer one exists, so the runtime does it itself.
 *
 * <p>The only request is a GET of the repository's releases from the GitHub API,
 * with no information about the device or the player. The answer is cached, and games
 * check at most once a day ({@link #due}); opening the runtime from the home screen
 * always checks. Network calls must not run on the UI thread.
 */
final class UpdateChecker {

    /**
     * The newest releases. Not /releases/latest, which leaves out prereleases: this
     * project has published its releases as prereleases.
     */
    static final String RELEASES_URL =
            "https://api.github.com/repos/t-satoru-Studio/renpy-android-runtime/releases?per_page=20";
    static final String RELEASES_PAGE =
            "https://github.com/t-satoru-Studio/renpy-android-runtime/releases";

    static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final int TIMEOUT_MS = 8000;

    private static final String PREFS = "update";
    private static final String CHECKED_AT = "checked_at";
    private static final String TAG = "tag";
    private static final String CODE = "code";
    private static final String URL_KEY = "url";
    private static final String NOTICED_AT = "noticed_at";
    private static final String DISMISSED = "dismissed";

    /** "v8.6.1-r1" and the like: Ren'Py version and runtime revision. */
    private static final Pattern TAG_PATTERN =
            Pattern.compile("v?(\\d{1,2})\\.(\\d{1,2})\\.(\\d{1,2})-r(\\d{1,2})");

    /** A published release. */
    static final class Release {
        final String tag;
        /** The versionCode the release's APK has, see {@link #versionCodeOf}. */
        final long versionCode;
        final String url;

        Release(String tag, long versionCode, String url) {
            this.tag = tag;
            this.versionCode = versionCode;
            this.url = url;
        }
    }

    private UpdateChecker() {
    }

    /**
     * The versionCode for a release tag, following app/build.gradle:
     * {@code <major><minor:2><patch:2><revision:2>}, so "v8.6.1-r1" is 8060101.
     * Returns -1 for a tag that is not a runtime version.
     */
    static long versionCodeOf(String tag) {
        if (tag == null) {
            return -1;
        }
        Matcher m = TAG_PATTERN.matcher(tag.trim());
        if (!m.matches()) {
            return -1;
        }
        return Long.parseLong(m.group(1)) * 1_000_000L
                + Long.parseLong(m.group(2)) * 10_000L
                + Long.parseLong(m.group(3)) * 100L
                + Long.parseLong(m.group(4));
    }

    /**
     * Picks the newest runtime version from the GitHub API's list of releases, drafts
     * aside (prereleases count). Returns null when there is none.
     */
    static Release parseList(String json) throws JSONException {
        JSONArray list = new JSONArray(json);
        Release newest = null;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            Release r = o == null ? null : parse(o);
            if (r != null && (newest == null || r.versionCode > newest.versionCode)) {
                newest = r;
            }
        }
        return newest;
    }

    /**
     * Reads one release from the GitHub API. Returns null for a draft or a tag that is
     * not a runtime version.
     */
    static Release parse(JSONObject o) {
        if (o.optBoolean("draft")) {
            return null;
        }
        String tag = o.optString("tag_name", null);
        long code = versionCodeOf(tag);
        if (code < 0) {
            return null;
        }
        // Only ever open a page on GitHub.
        String url = o.optString("html_url", "");
        if (!url.startsWith("https://github.com/")) {
            url = RELEASES_PAGE;
        }
        return new Release(tag, code, url);
    }

    /** Asks GitHub for the newest release. Blocks; returns null when there is none. */
    static Release fetch(String userAgentVersion) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            // GitHub requires a User-Agent; this one names only the app.
            c.setRequestProperty("User-Agent", "renpy-android-runtime/" + userAgentVersion);
            int status = c.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub answered " + status);
            }
            try (InputStream in = c.getInputStream()) {
                return parseList(readAll(in));
            }
        } finally {
            c.disconnect();
        }
    }

    // --- cache ---------------------------------------------------------------------

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Whether a game should check now: the last attempt was a day ago or more. */
    static boolean due(SharedPreferences prefs, long now) {
        long last = prefs.getLong(CHECKED_AT, 0);
        return now - last >= DAY_MS || now < last;
    }

    /** Records an attempt, and its answer when there was one. */
    static void store(SharedPreferences prefs, Release release, long now) {
        SharedPreferences.Editor e = prefs.edit().putLong(CHECKED_AT, now);
        if (release != null) {
            e.putString(TAG, release.tag).putLong(CODE, release.versionCode).putString(URL_KEY, release.url);
        }
        e.apply();
    }

    /** The latest release seen so far, or null. */
    static Release cached(SharedPreferences prefs) {
        String tag = prefs.getString(TAG, null);
        if (tag == null) {
            return null;
        }
        return new Release(tag, prefs.getLong(CODE, -1), prefs.getString(URL_KEY, RELEASES_PAGE));
    }

    /**
     * Whether a game should show a notice about {@code release} now: it is newer than
     * this APK, the player has not closed a notice about it, and the last notice was a
     * day ago or more.
     */
    static boolean shouldNotify(SharedPreferences prefs, Release release, long installed, long now) {
        if (release == null || release.versionCode <= installed) {
            return false;
        }
        if (prefs.getLong(DISMISSED, -1) == release.versionCode) {
            return false;
        }
        long last = prefs.getLong(NOTICED_AT, 0);
        return now - last >= DAY_MS || now < last;
    }

    static void noticed(SharedPreferences prefs, long now) {
        prefs.edit().putLong(NOTICED_AT, now).apply();
    }

    /** The player closed the notice: no more notices about this release in games. */
    static void dismiss(SharedPreferences prefs, Release release) {
        prefs.edit().putLong(DISMISSED, release.versionCode).apply();
    }

    // --- helpers -------------------------------------------------------------------

    static long installedVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return Long.MAX_VALUE;
        }
    }

    static String installedVersionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        // The answer is a few kilobytes; stop at 1 MiB in case it is not.
        while ((n = in.read(buf)) > 0 && out.size() < (1 << 20)) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
