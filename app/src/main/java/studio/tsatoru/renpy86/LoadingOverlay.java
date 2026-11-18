// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The screen shown over the SDL surface until Ren'Py draws its first frame. Ren'Py's
 * own Android presplash is a static image from the APK, which a runtime without a
 * bundled game lacks, so the player would otherwise look at a black screen for
 * several seconds. The stages come from the reporter thread in
 * runtime/main_prelude.py, through {@link GameActivity#reportLoading}.
 *
 * <p>All methods run on the UI thread.
 */
final class LoadingOverlay extends FrameLayout {

    /** Stage keys, in the order they usually happen, and their names. */
    private static final String[] STAGES = {
            "runtime", "python", "engine", "files", "bytecode", "prepare", "script",
            "saves", "init", "finishing", "screens", "display",
    };
    private static final int[] STAGE_NAMES = {
            R.string.loading_runtime, R.string.loading_python, R.string.loading_engine,
            R.string.loading_files, R.string.loading_bytecode, R.string.loading_prepare,
            R.string.loading_script, R.string.loading_saves, R.string.loading_init,
            R.string.loading_finishing, R.string.loading_screens, R.string.loading_display,
    };

    /** How many finished stages stay listed. */
    private static final int HISTORY = 5;

    private final TextView stageView;
    private final TextView countView;
    private final ThinProgressBar progress;
    private final TextView detailView;
    private final TextView historyView;
    private final TextView elapsedView;

    private final long started = SystemClock.elapsedRealtime();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String stage;
    private long stageStarted;
    /** Finished stages, as {key, milliseconds}; a stage seen again adds to its time. */
    private final List<Object[]> finished = new ArrayList<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateElapsed();
            handler.postDelayed(this, 100);
        }
    };

    LoadingOverlay(Context context, String title) {
        super(context);
        setBackgroundColor(Color.rgb(0x10, 0x11, 0x14));
        // Taps during loading go nowhere, rather than to Ren'Py once it is up.
        setClickable(true);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView titleView = text(context, 20, Color.WHITE);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setText(title);
        column.addView(titleView, matchWidth());

        LinearLayout stageRow = new LinearLayout(context);
        stageRow.setOrientation(LinearLayout.HORIZONTAL);
        stageView = text(context, 15, Color.rgb(0xE6, 0xE6, 0xEA));
        countView = text(context, 13, Color.rgb(0x9A, 0x9C, 0xA6));
        countView.setFontFeatureSettings("tnum");
        countView.setGravity(Gravity.END);
        stageRow.addView(stageView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        stageRow.addView(countView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams stageParams = matchWidth();
        stageParams.topMargin = dp(28);
        column.addView(stageRow, stageParams);

        progress = new ThinProgressBar(context, Color.rgb(0x2A, 0x2C, 0x34), Color.rgb(0x8A, 0xB4, 0xF8));
        LinearLayout.LayoutParams progressParams = matchWidth();
        progressParams.topMargin = dp(10);
        progressParams.bottomMargin = dp(8);
        column.addView(progress, progressParams);

        detailView = text(context, 12, Color.rgb(0x9A, 0x9C, 0xA6));
        detailView.setTypeface(Typeface.MONOSPACE);
        detailView.setSingleLine(true);
        detailView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        column.addView(detailView, matchWidth());

        historyView = text(context, 12, Color.rgb(0x70, 0x72, 0x7C));
        historyView.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams historyParams = matchWidth();
        historyParams.topMargin = dp(18);
        column.addView(historyView, historyParams);

        int width = Math.min(dp(560), getResources().getDisplayMetrics().widthPixels - dp(64));
        addView(column, new LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        elapsedView = text(context, 12, Color.rgb(0x70, 0x72, 0x7C));
        elapsedView.setFontFeatureSettings("tnum");
        LayoutParams elapsedParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        elapsedParams.setMargins(0, 0, dp(24), dp(16));
        addView(elapsedView, elapsedParams);

        update("runtime", "", 0, 0);
        handler.post(tick);
    }

    /** Shows what Ren'Py is doing. {@code total} is 0 when the stage has no count. */
    void update(String newStage, String detail, int done, int total) {
        long now = SystemClock.elapsedRealtime();
        if (!newStage.equals(stage)) {
            if (stage != null) {
                addFinished(stage, now - stageStarted);
            }
            stage = newStage;
            stageStarted = now;
            stageView.setText(stageName(newStage));
            renderHistory();
        }

        if (total > 0) {
            progress.setFraction((float) Math.min(done, total) / total);
            countView.setText(String.format(Locale.ROOT, "%d / %d", done, total));
        } else {
            progress.setIndeterminate(true);
            countView.setText("");
        }
        detailView.setText(detail == null ? "" : detail);
    }

    /** Stops the clock; the activity removes the view. */
    void finish() {
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(tick);
    }

    private void addFinished(String key, long ms) {
        for (Object[] entry : finished) {
            if (entry[0].equals(key)) {
                entry[1] = (Long) entry[1] + ms;
                return;
            }
        }
        finished.add(new Object[]{key, ms});
    }

    private void renderHistory() {
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, finished.size() - HISTORY);
        for (int i = from; i < finished.size(); i++) {
            Object[] entry = finished.get(i);
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("✓ ").append(stageName((String) entry[0]))
                    .append("  ").append(seconds((Long) entry[1]));
        }
        historyView.setText(sb);
    }

    private void updateElapsed() {
        elapsedView.setText(getResources().getString(R.string.loading_elapsed,
                seconds(SystemClock.elapsedRealtime() - started)));
    }

    private String stageName(String key) {
        for (int i = 0; i < STAGES.length; i++) {
            if (STAGES[i].equals(key)) {
                return getResources().getString(STAGE_NAMES[i]);
            }
        }
        return getResources().getString(R.string.loading_finishing);
    }

    private static String seconds(long ms) {
        return String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
    }

    private TextView text(Context context, int sp, int color) {
        TextView v = new TextView(context);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        v.setLineSpacing(dp(3), 1);
        return v;
    }

    private static LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Fades the view out, then removes it from its parent. */
    void dismiss() {
        finish();
        animate().alpha(0f).setDuration(200).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) {
                parent.removeView(this);
            }
        }).start();
    }
}
