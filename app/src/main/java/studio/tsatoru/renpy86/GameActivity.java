// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import org.renpy.android.PythonSDLActivity;

import cyou.joiplay.joipad.JoiPad;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    /** The activity Ren'Py runs in; this process runs one game, once. */
    private static volatile GameActivity current;
    /** Ren'Py has drawn its first frame. Python runs once per process, and so does loading. */
    private static volatile boolean loadingDone;
    /** Commands from the virtual keys, read by runtime/common/00runtime_overrides.rpy. */
    private static final ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();

    private LoadingOverlay loadingOverlay;
    private VirtualKeys virtualKeys;
    /** Holds JoiPlay's gamepad, so it can be hidden while loading. */
    private FrameLayout gamepad;

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
            // Before super.onCreate, which starts the SDL thread that reports loading.
            current = this;
            new GameSession(Process.myPid(), request.folder).write(getFilesDir());
            Log.i(TAG, "Running " + request.folder + ", saves in " + request.saveDir);
        }

        for (Map.Entry<String, String> hint : RuntimeEnvironment.sdlHints().entrySet()) {
            try {
                Os.setenv(hint.getKey(), hint.getValue(), true);
            } catch (ErrnoException e) {
                Log.w(TAG, "Could not set " + hint.getKey() + ": " + e.getMessage());
            }
        }

        super.onCreate(savedInstanceState);

        if (request == null) {
            finish();
            return;
        }

        if (!Boolean.FALSE.equals(request.settings.gamepad)) {
            gamepad = new FrameLayout(this);
            gamepad.setVisibility(loadingDone ? View.VISIBLE : View.GONE);
            mLayout.addView(gamepad, new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            attachGamepad(gamepad);
        }
        if (!Boolean.FALSE.equals(request.settings.virtualKeys)) {
            List<VirtualKeys.Toggle> toggles = new ArrayList<>();
            if (gamepad != null) {
                // JoiPad's own handles are arrows at the middle of the top and bottom edges.
                // With our panel on screen they open from there instead.
                toggles.add(gamepadToggle(R.string.vkey_gamepad,
                        cyou.joiplay.joipad.R.id.bottomShowButton, cyou.joiplay.joipad.R.id.padLayChild));
                toggles.add(gamepadToggle(R.string.vkey_toolbar,
                        cyou.joiplay.joipad.R.id.topShowButton, cyou.joiplay.joipad.R.id.miscLayChild));
            }
            virtualKeys = new VirtualKeys(this, VirtualKeys.preferences(this), commands::add, toggles);
            virtualKeys.setVisibility(loadingDone ? View.VISIBLE : View.GONE);
            mLayout.addView(virtualKeys, new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (!loadingDone) {
            loadingOverlay = new LoadingOverlay(this, request.title);
            mLayout.addView(loadingOverlay, new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 16 always uses the back dispatcher for apps targeting it: no back
            // key event reaches SDL, and the default callback finishes the activity under
            // the running game. Hand back to Ren'Py as the key it expects (K_AC_BACK,
            // rollback). Older versions deliver the key event, and SDL keeps it because
            // of SDL_ANDROID_TRAP_BACK_BUTTON (see RuntimeEnvironment).
            //
            // Ren'Py fixed this in RAPT (renpy/renpy#7533, renpy-build 5d80c67), but the
            // 8.6.1 RAPT archive was built before that commit and lacks it. Once RAPT
            // registers its own callback, this one, registered later, takes the presses
            // and RAPT's goes unused, so both can stay.
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
        // RAPT unpacks the Python runtime here after an install or update.
        reportLoading("runtime", "", 0, 0);
        super.preparePython();

        if (request == null) {
            return;
        }
        reportLoading("python", "", 0, 0);

        File logDir = new File(getExternalFilesDir(null), "logs/" + request.id);
        //noinspection ResultOfMethodCallIgnored
        logDir.mkdirs();
        File noOldPublic = new File(getFilesDir(), "no-old-public");

        Map<String, String> env = RuntimeEnvironment.build(request, logDir, noOldPublic);
        for (Map.Entry<String, String> e : env.entrySet()) {
            nativeSetEnv(e.getKey(), e.getValue());
        }
    }

    /**
     * Called by Ren'Py after its first frame (Interface.after_first_frame). RAPT hides
     * its presplash image here; we also hide the loading screen and show the keys.
     */
    @Override
    public void hidePresplash() {
        super.hidePresplash();
        loadingDone = true;
        runOnUiThread(() -> {
            if (loadingOverlay != null) {
                loadingOverlay.dismiss();
                loadingOverlay = null;
            }
            if (virtualKeys != null) {
                virtualKeys.setVisibility(View.VISIBLE);
            }
            if (gamepad != null) {
                gamepad.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * A panel button for one half of JoiPlay's gamepad: hides JoiPad's handle for it and
     * clicks that handle instead, so JoiPad keeps its own state.
     */
    private VirtualKeys.Toggle gamepadToggle(int label, int handleId, int partId) {
        View handle = gamepad.findViewById(handleId);
        View part = gamepad.findViewById(partId);
        handle.setVisibility(View.GONE);
        return new VirtualKeys.Toggle(label, handle::performClick,
                () -> part.getVisibility() == View.VISIBLE);
    }

    /**
     * JoiPlay's virtual gamepad (third_party/joiplay/joipad), with Ren'Py keys (see
     * {@link RenPyGamePad}). Its keys go to SDL like a hardware keyboard's.
     */
    private void attachGamepad(ViewGroup holder) {
        JoiPad pad = new JoiPad();
        pad.init(this, RenPyGamePad.defaults());
        pad.setGame(RenPyGamePad.game(request));
        pad.setOnKeyDownListener(key -> {
            if (key == KeyEvent.KEYCODE_MUTE) {
                // The gamepad's volume button: Ren'Py has no key for muting.
                commands.add("mute");
            } else {
                onNativeKeyDown(key);
            }
        });
        pad.setOnKeyUpListener(key -> {
            if (key != KeyEvent.KEYCODE_MUTE) {
                onNativeKeyUp(key);
            }
        });
        // The gamepad has asked "close the game?" already. Ren'Py quits the way its own
        // quit action does, saving persistent data; the activity then finishes.
        pad.setOnCloseListener(() -> commands.add("quit"));
        pad.attachTo(this, holder);
    }

    /**
     * Shows what Ren'Py is loading. Called from the reporter thread in
     * runtime/main_prelude.py, and from the SDL thread before Python starts.
     *
     * @param stage  one of the stage keys {@link LoadingOverlay} knows
     * @param detail the file or statement being loaded, or ""
     * @param total  0 when the stage has no count
     */
    public static void reportLoading(String stage, String detail, int done, int total) {
        GameActivity activity = current;
        if (activity == null || loadingDone) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (activity.loadingOverlay != null) {
                activity.loadingOverlay.update(stage, detail, done, total);
            }
        });
    }

    /** Whether the loading screen is gone, so the reporter thread can stop. */
    public static boolean isLoadingDone() {
        return loadingDone;
    }

    /** The next command from the virtual keys, or null. */
    public static String pollCommand() {
        return commands.poll();
    }

    @Override
    protected void onDestroy() {
        if (backCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (OnBackInvokedCallback) backCallback);
        }
        super.onDestroy();
        if (current == this) {
            current = null;
        }
        GameSession.clear(getFilesDir(), Process.myPid());

        if (isFinishing()) {
            // Python cannot be started twice in one process, so leave none behind.
            Process.killProcess(Process.myPid());
        }
    }
}
