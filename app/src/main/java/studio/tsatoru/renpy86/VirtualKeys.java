// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * On-screen buttons for what Ren'Py otherwise needs a keyboard or mouse for: the game
 * menu, skipping, auto-forward, hiding the text window, rollback, quick save and load.
 *
 * <p>A handle at the right edge opens and closes a panel of buttons, two rows under (or,
 * in the lower half, above) the handle; dragging the handle moves it up or down. Where Ren'Py has a default key, the button sends that key to SDL,
 * so the game's own keymap and handlers apply. The rest are commands for the pause
 * watcher in runtime/common/00runtime_overrides.rpy, through
 * {@link GameActivity#pollCommand}.
 *
 * <p>The view covers the screen but only its buttons take touches, so everything else
 * reaches the game.
 */
@SuppressLint("ViewConstructor")
final class VirtualKeys extends FrameLayout {

    /** What a button does. */
    private interface Action {
        void press();

        /** {@code cancelled} when the touch was taken away, e.g. by a system gesture. */
        void release(boolean cancelled);
    }

    interface CommandSink {
        void send(String command);
    }

    /** A button that switches something else on and off, highlighted while on. */
    static final class Toggle {
        interface State {
            boolean isOn();
        }

        final int label;
        final Runnable flip;
        final State state;

        Toggle(int label, Runnable flip, State state) {
            this.label = label;
            this.flip = flip;
            this.state = state;
        }
    }

    private static final int IDLE = Color.argb(0x99, 0, 0, 0);
    private static final int PRESSED = Color.argb(0xCC, 0x40, 0x48, 0x60);
    private static final int ON = Color.argb(0xCC, 0x2F, 0x5D, 0xA8);

    private static final String PREFS = "virtual_keys";
    private static final String PREF_EXPANDED = "expanded";
    private static final String PREF_HANDLE_Y = "handle_y";

    private final SharedPreferences prefs;
    private final CommandSink commands;
    private final TextView handle;
    private final LinearLayout panel;
    /** Refreshes the toggles' highlight. */
    private final List<Runnable> toggleViews = new ArrayList<>();
    private boolean expanded;
    /** The handle's centre, as a fraction of the available height. */
    private float handleY;

    VirtualKeys(Context context, SharedPreferences prefs, CommandSink commands) {
        this(context, prefs, commands, Collections.emptyList());
    }

    /**
     * @param toggles extra buttons at the end of the rows, one per row: the other
     *                on-screen controls, such as JoiPlay's gamepad, open from here
     */
    VirtualKeys(Context context, SharedPreferences prefs, CommandSink commands, List<Toggle> toggles) {
        super(context);
        this.prefs = prefs;
        this.commands = commands;
        expanded = prefs.getBoolean(PREF_EXPANDED, false);
        // Near the top by default: JoiPlay's gamepad uses the lower half of both sides.
        handleY = prefs.getFloat(PREF_HANDLE_Y, 0.15f);

        // Two rows, so the panel stays short wherever the handle is.
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = row(context);
        LinearLayout bottom = row(context);
        panel.addView(top);
        panel.addView(bottom);

        addButton(top, R.string.vkey_menu, tap(KeyEvent.KEYCODE_ESCAPE));
        addButton(top, R.string.vkey_skip, tap(KeyEvent.KEYCODE_TAB));
        addButton(top, R.string.vkey_fast, hold(KeyEvent.KEYCODE_CTRL_LEFT));
        addButton(top, R.string.vkey_auto, command("auto"));
        addButton(top, R.string.vkey_hide, tap(KeyEvent.KEYCODE_H));

        addButton(bottom, R.string.vkey_history, command("history"));
        addButton(bottom, R.string.vkey_rollback, tap(KeyEvent.KEYCODE_PAGE_UP));
        addButton(bottom, R.string.vkey_rollforward, tap(KeyEvent.KEYCODE_PAGE_DOWN));
        addButton(bottom, R.string.vkey_quick_save, command("quick_save"));
        addButton(bottom, R.string.vkey_quick_load, command("quick_load"));

        for (int i = 0; i < toggles.size(); i++) {
            addToggle(i % 2 == 0 ? top : bottom, toggles.get(i));
        }

        addView(panel, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        handle = new TextView(context);
        handle.setText("☰");
        handle.setTextColor(Color.WHITE);
        handle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        handle.setGravity(Gravity.CENTER);
        GradientDrawable round = new GradientDrawable();
        round.setShape(GradientDrawable.OVAL);
        round.setColor(Color.argb(0x99, 0, 0, 0));
        round.setStroke(dp(1), Color.argb(0x66, 0xFF, 0xFF, 0xFF));
        handle.setBackground(round);
        handle.setOnTouchListener(new HandleTouch());
        addView(handle, new LayoutParams(dp(44), dp(44)));

        setOnApplyWindowInsetsListener((v, insets) -> {
            applyCutout(insets);
            return insets;
        });

        applyExpanded();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        placeChildren();
    }

    /** Puts the handle at the right edge at handleY, and the panel next to it. */
    private void placeChildren() {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0) {
            return;
        }
        int margin = dp(8);

        int hx = getPaddingLeft() + width - handle.getWidth() - margin;
        int hy = getPaddingTop() + clamp(Math.round(handleY * height) - handle.getHeight() / 2,
                margin, height - handle.getHeight() - margin);
        handle.setX(hx);
        handle.setY(hy);

        // Under the handle, right-aligned with it, or above it in the lower half. Beside it
        // the panel would cover the toolbar of JoiPlay's gamepad, centred at the top.
        int px = Math.max(getPaddingLeft() + margin, hx + handle.getWidth() - panel.getWidth());
        int py;
        if (handleY < 0.5f) {
            py = hy + handle.getHeight() + margin;
        } else {
            py = hy - panel.getHeight() - margin;
        }
        py = getPaddingTop() + clamp(py - getPaddingTop(), margin, height - panel.getHeight() - margin);
        panel.setX(px);
        panel.setY(py);
    }

    private void applyCutout(WindowInsets insets) {
        int l = 0, t = 0, r = 0, b = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets cut = insets.getInsets(WindowInsets.Type.displayCutout());
            l = cut.left;
            t = cut.top;
            r = cut.right;
            b = cut.bottom;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DisplayCutout cut = insets.getDisplayCutout();
            if (cut != null) {
                l = cut.getSafeInsetLeft();
                t = cut.getSafeInsetTop();
                r = cut.getSafeInsetRight();
                b = cut.getSafeInsetBottom();
            }
        }
        setPadding(l, t, r, b);
    }

    private void applyExpanded() {
        panel.setVisibility(expanded ? VISIBLE : GONE);
        for (Runnable refresh : toggleViews) {
            refresh.run();
        }
        handle.setAlpha(expanded ? 1f : 0.6f);
    }

    private void toggle() {
        expanded = !expanded;
        prefs.edit().putBoolean(PREF_EXPANDED, expanded).apply();
        applyExpanded();
    }

    private final class HandleTouch implements OnTouchListener {
        private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        private float downY;
        private float startFraction;
        private boolean dragging;

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            int height = getHeight() - getPaddingTop() - getPaddingBottom();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = e.getRawY();
                    startFraction = handleY;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dy = e.getRawY() - downY;
                    if (!dragging && Math.abs(dy) > slop) {
                        dragging = true;
                    }
                    if (dragging && height > 0) {
                        handleY = Math.max(0f, Math.min(1f, startFraction + dy / height));
                        placeChildren();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        prefs.edit().putFloat(PREF_HANDLE_Y, handleY).apply();
                    } else {
                        toggle();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return false;
            }
        }
    }

    // --- buttons ---------------------------------------------------------------

    private LinearLayout row(Context context) {
        LinearLayout r = new LinearLayout(context);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private void addButton(LinearLayout row, int label, Action action) {
        addButton(row, label, action, () -> false);
    }

    private void addToggle(LinearLayout row, Toggle toggle) {
        GradientDrawable[] bg = new GradientDrawable[1];
        Action flip = new Action() {
            @Override
            public void press() {
            }

            @Override
            public void release(boolean cancelled) {
                if (!cancelled) {
                    toggle.flip.run();
                }
            }
        };
        bg[0] = addButton(row, toggle.label, flip, toggle.state::isOn);
        toggleViews.add(() -> bg[0].setColor(toggle.state.isOn() ? ON : IDLE));
    }

    /** Adds a button; {@code on} says whether it shows as switched on when not pressed. */
    @SuppressLint("ClickableViewAccessibility")
    private GradientDrawable addButton(LinearLayout row, int label, Action action, Toggle.State on) {
        TextView b = new TextView(getContext());
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(on.isOn() ? ON : IDLE);
        bg.setStroke(dp(1), Color.argb(0x40, 0xFF, 0xFF, 0xFF));
        b.setBackground(bg);

        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    bg.setColor(PRESSED);
                    action.press();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    action.release(e.getActionMasked() == MotionEvent.ACTION_CANCEL);
                    bg.setColor(on.isOn() ? ON : IDLE);
                    return true;
                default:
                    return true;
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(34));
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(b, lp);
        return bg;
    }

    /** A key pressed and released when the finger lifts. */
    private static Action tap(int keycode) {
        return new Action() {
            @Override
            public void press() {
            }

            @Override
            public void release(boolean cancelled) {
                if (!cancelled) {
                    GameActivity.onNativeKeyDown(keycode);
                    GameActivity.onNativeKeyUp(keycode);
                }
            }
        };
    }

    /** A key held down for as long as the finger is. */
    private static Action hold(int keycode) {
        return new Action() {
            @Override
            public void press() {
                GameActivity.onNativeKeyDown(keycode);
            }

            @Override
            public void release(boolean cancelled) {
                GameActivity.onNativeKeyUp(keycode);
            }
        };
    }

    private Action command(String name) {
        return new Action() {
            @Override
            public void press() {
            }

            @Override
            public void release(boolean cancelled) {
                if (!cancelled) {
                    commands.send(name);
                }
            }
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
