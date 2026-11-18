// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A small card at the bottom of the game saying that a newer runtime is out, with
 * "view" and "close" buttons. It takes no touches outside itself and goes away by
 * itself after a while, so it never gets in the way of the game.
 */
@SuppressLint("ViewConstructor")
final class UpdateNotice extends FrameLayout {

    interface Listener {
        void onView();

        void onClose();
    }

    private static final long SHOWN_MS = 8000;

    UpdateNotice(Context context, String version, Listener listener) {
        super(context);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(6), dp(6), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(Color.argb(0xE6, 0x1C, 0x1D, 0x22));
        bg.setStroke(dp(1), Color.argb(0x40, 0xFF, 0xFF, 0xFF));
        card.setBackground(bg);
        card.setClickable(true);

        TextView text = new TextView(context);
        text.setText(context.getString(R.string.update_available, version));
        text.setTextColor(Color.rgb(0xE6, 0xE6, 0xEA));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        card.addView(text);

        TextView view = button(context, R.string.update_view, Color.rgb(0x8A, 0xB4, 0xF8));
        view.setOnClickListener(v -> {
            dismiss();
            listener.onView();
        });
        card.addView(view);

        TextView close = button(context, R.string.update_close, Color.rgb(0x9A, 0x9C, 0xA6));
        close.setOnClickListener(v -> {
            dismiss();
            listener.onClose();
        });
        card.addView(close);

        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(20);
        addView(card, lp);

        setAlpha(0f);
        animate().alpha(1f).setDuration(200).start();
        postDelayed(this::dismiss, SHOWN_MS);
    }

    void dismiss() {
        animate().alpha(0f).setDuration(200).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) {
                parent.removeView(this);
            }
        }).start();
    }

    private TextView button(Context context, int label, int color) {
        TextView b = new TextView(context);
        b.setText(label);
        b.setTextColor(color);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setClickable(true);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
