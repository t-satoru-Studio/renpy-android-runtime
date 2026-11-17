// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.util.List;

/**
 * Entry point for Tyranor (action {@code com.tyranor.runtime.action.LAUNCH}, see
 * PROTOCOL.md) and for the home screen icon, which shows the runtime version and storage
 * access.
 *
 * <p>Checks storage access and the game folder, makes sure the {@code :game} process is
 * free for this game, then starts {@link GameActivity} in its own task.
 */
public class LaunchActivity extends Activity {

    public static final String ACTION_LAUNCH = "com.tyranor.runtime.action.LAUNCH";

    static final String EXTRA_FOLDER_PATH = "com.tyranor.runtime.extra.FOLDER_PATH";
    static final String EXTRA_GAME_TITLE = "com.tyranor.runtime.extra.GAME_TITLE";
    static final String EXTRA_PROTOCOL_VERSION = "com.tyranor.runtime.extra.PROTOCOL_VERSION";
    static final String EXTRA_SETTINGS = "com.tyranor.runtime.extra.SETTINGS";

    private static final String TAG = "RenPyRuntime";
    private static final int REQUEST_LEGACY_STORAGE = 1;
    private static final long PROCESS_EXIT_TIMEOUT_MS = 3000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LaunchRequest pending;
    private boolean waitingForAccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (!ACTION_LAUNCH.equals(intent.getAction())) {
            showInfo();
            return;
        }

        String folder = intent.getStringExtra(EXTRA_FOLDER_PATH);
        Log.i(TAG, "Launch request: folder=" + folder + " protocol="
                + intent.getIntExtra(EXTRA_PROTOCOL_VERSION, 1));
        try {
            pending = LaunchRequest.fromExtras(folder,
                    intent.getStringExtra(EXTRA_GAME_TITLE),
                    intent.getStringExtra(EXTRA_SETTINGS));
        } catch (LaunchRequest.InvalidRequestException e) {
            Log.w(TAG, "Rejected launch request: " + e.getMessage());
            showError(getString(R.string.error_bad_request));
            return;
        }

        continueLaunch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!waitingForAccess) {
            return;
        }
        // Back from the system "all files access" page.
        waitingForAccess = false;
        if (pending == null) {
            showInfo();
        } else if (hasStorageAccess()) {
            continueLaunch();
        } else {
            showError(getString(R.string.error_no_access));
        }
    }

    private void continueLaunch() {
        if (!hasStorageAccess()) {
            askForStorageAccess();
            return;
        }

        File base = LaunchRequest.resolveBaseDir(new File(pending.folder));
        if (base == null) {
            showError(getString(R.string.error_no_game, pending.folder));
            return;
        }
        LaunchRequest request = pending.withFolder(base.getAbsolutePath());

        int running = runningGamePid();
        GameSession session = GameSession.read(getFilesDir());
        if (running > 0 && session != null && session.pid == running
                && request.folder.equals(session.folder)) {
            // The same game is still running: bring it back rather than restart it.
            startGame(request);
            return;
        }

        if (running > 0) {
            Log.i(TAG, "Stopping the previous game (pid " + running + ")");
            Process.killProcess(running);
            waitForExit(running, System.currentTimeMillis() + PROCESS_EXIT_TIMEOUT_MS,
                    () -> startGame(request));
            return;
        }

        startGame(request);
    }

    private void startGame(LaunchRequest request) {
        Intent game = new Intent(this, GameActivity.class)
                .putExtra(GameActivity.EXTRA_REQUEST, request.toJson())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(game);
        finish();
    }

    private void waitForExit(int pid, long deadline, Runnable then) {
        if (runningGamePid() != pid || System.currentTimeMillis() > deadline) {
            then.run();
            return;
        }
        handler.postDelayed(() -> waitForExit(pid, deadline, then), 50);
    }

    /** The pid of our {@code :game} process, or 0 if it is not running. */
    private int runningGamePid() {
        ActivityManager am = getSystemService(ActivityManager.class);
        List<ActivityManager.RunningAppProcessInfo> processes =
                am != null ? am.getRunningAppProcesses() : null;
        if (processes == null) {
            return 0;
        }
        String name = getPackageName() + ":game";
        for (ActivityManager.RunningAppProcessInfo p : processes) {
            if (name.equals(p.processName)) {
                return p.pid;
            }
        }
        return 0;
    }

    // Storage access ---------------------------------------------------------

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void askForStorageAccess() {
        dialog()
                .setTitle(R.string.access_title)
                .setMessage(R.string.access_message)
                .setPositiveButton(R.string.access_grant, (d, w) -> requestStorageAccess())
                .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            waitingForAccess = true;
            Intent settings = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(settings);
            } catch (RuntimeException e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
            }, REQUEST_LEGACY_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_LEGACY_STORAGE) {
            return;
        }
        if (pending == null) {
            showInfo();
        } else if (hasStorageAccess()) {
            continueLaunch();
        } else {
            showError(getString(R.string.error_no_access));
        }
    }

    // Dialogs ----------------------------------------------------------------

    private void showInfo() {
        String access = getString(hasStorageAccess() ? R.string.info_access_yes : R.string.info_access_no);
        AlertDialog.Builder b = dialog()
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.info_message, versionName(),
                        getString(R.string.renpy_build), access))
                .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                .setOnCancelListener(d -> finish());
        if (!hasStorageAccess()) {
            b.setNeutralButton(R.string.access_grant, (d, w) -> requestStorageAccess());
        }
        b.show();
    }

    private void showError(String message) {
        dialog()
                .setTitle(R.string.error_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }
}
