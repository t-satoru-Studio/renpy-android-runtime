// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class UpdateCheckerTest {

    @Test
    public void tagsMapToTheVersionCodesInBuildGradle() {
        assertEquals(8060101L, UpdateChecker.versionCodeOf("v8.6.1-r1"));
        assertEquals(8060004L, UpdateChecker.versionCodeOf("v8.6.0-r4"));
        assertEquals(8060003L, UpdateChecker.versionCodeOf("8.6.0-r3"));
        assertEquals(8100212L, UpdateChecker.versionCodeOf("v8.10.2-r12"));
    }

    @Test
    public void otherTagsAreNotVersions() {
        assertEquals(-1L, UpdateChecker.versionCodeOf(null));
        assertEquals(-1L, UpdateChecker.versionCodeOf("v8.6.1"));
        assertEquals(-1L, UpdateChecker.versionCodeOf("nightly"));
        assertEquals(-1L, UpdateChecker.versionCodeOf("v8.6.1-r1-debug"));
    }

    private static JSONObject release(String tag) throws Exception {
        return new JSONObject().put("tag_name", tag)
                .put("html_url", "https://github.com/t-satoru-Studio/renpy-android-runtime/releases/tag/" + tag)
                .put("draft", false).put("prerelease", true);
    }

    @Test
    public void picksTheNewestReleasePrereleasesIncluded() throws Exception {
        String json = new JSONArray()
                .put(release("v8.6.0-r3"))
                .put(release("v8.6.1-r1"))
                .put(release("v8.6.0-r4"))
                .put(new JSONObject().put("tag_name", "v9.0.0-r1").put("draft", true))
                .put(new JSONObject().put("tag_name", "nightly"))
                .toString();
        UpdateChecker.Release r = UpdateChecker.parseList(json);
        assertEquals("v8.6.1-r1", r.tag);
        assertEquals(8060101L, r.versionCode);
        assertEquals("https://github.com/t-satoru-Studio/renpy-android-runtime/releases/tag/v8.6.1-r1", r.url);
    }

    @Test
    public void noReleasesIsNoAnswer() throws Exception {
        assertNull(UpdateChecker.parseList("[]"));
    }

    @Test
    public void onlyOpensGitHubPages() throws Exception {
        UpdateChecker.Release r = UpdateChecker.parse(release("v8.6.1-r1").put("html_url", "javascript:alert(1)"));
        assertEquals(UpdateChecker.RELEASES_PAGE, r.url);
        r = UpdateChecker.parse(release("v8.6.1-r1").put("html_url", "https://example.com/x"));
        assertEquals(UpdateChecker.RELEASES_PAGE, r.url);
    }
}
