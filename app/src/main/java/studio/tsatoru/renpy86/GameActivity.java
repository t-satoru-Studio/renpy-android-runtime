// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.KeyEvent;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import org.renpy.android.PythonSDLActivity;

import java.io.File;
import java.util.Map;

/**
 * Ren'Py's own Android activity (RAPT's PythonSDLActivity, unchanged), pointed at a
 * game on external storage. Runs in the {@code :game} process; started only by
 * {@link LaunchActivity}, which has already checked the request and storage access.
 */
public class GameActivity extends PythonSDLActivity {

    static final String EXTRA_REQUEST = "studio.tsatoru.renpy86.REQUEST";
    private static final String TAG = "RenPyRuntime";

    private LaunchRequest request;
    /** An OnBackInvokedCallback; typed Object so the class loads on API < 33. */
    private Object backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Read the request before super.onCreate starts the SDL thread, which calls
        // preparePython() and then Python.
        try {
            request = LaunchRequest.fromJson(getIntent().getStringExtra(EXTRA_REQUEST));
        } catch (LaunchRequest.InvalidRequestException e) {
            Log.e(TAG, "GameActivity started without a valid request: " + e.getMessage());
            request = null;
        }

        if (request != null) {
            new GameSession(Process.myPid(), request.folder).write(getFilesDir());
            Log.i(TAG, "Running " + request.folder + ", saves in " + request.saveDir);
        }

        super.onCreate(savedInstanceState);

        if (request == null) {
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 16 always uses the back dispatcher for apps targeting it: no back
            // key event reaches SDL, and the default callback finishes the activity under
            // the running game. Hand back to Ren'Py as the key it expects (K_AC_BACK,
            // rollback). Older versions deliver the key event, and SDL keeps it because
            // of SDL_ANDROID_TRAP_BACK_BUTTON (see RuntimeEnvironment).
            OnBackInvokedCallback callback = () -> {
                onNativeKeyDown(KeyEvent.KEYCODE_BACK);
                onNativeKeyUp(KeyEvent.KEYCODE_BACK);
            };
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            backCallback = callback;
        }
    }

    /**
     * Called by librenpython on the SDL thread before Python starts. RAPT sets up
     * ANDROID_PRIVATE and friends for an APK with a bundled game; we keep its private
     * directory (the shared runtime) and point everything game-related elsewhere.
     */
    @Override
    public void preparePython() {
        super.preparePython();

        if (request == null) {
            return;
        }

        File logDir = new File(getExternalFilesDir(null), "logs/" + request.id);
        //noinspection ResultOfMethodCallIgnored
        logDir.mkdirs();
        File noOldPublic = new File(getFilesDir(), "no-old-public");

        Map<String, String> env = RuntimeEnvironment.build(request, logDir, noOldPublic);
        for (Map.Entry<String, String> e : env.entrySet()) {
            nativeSetEnv(e.getKey(), e.getValue());
        }
    }

    @Override
    protected void onDestroy() {
        if (backCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (OnBackInvokedCallback) backCallback);
        }
        super.onDestroy();
        GameSession.clear(getFilesDir(), Process.myPid());

        if (isFinishing()) {
            // Python cannot be started twice in one process, so leave none behind.
            Process.killProcess(Process.myPid());
        }
    }
}
