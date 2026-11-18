// Copyright (C) 2026 t-satoru
// SPDX-License-Identifier: GPL-3.0-or-later
package studio.tsatoru.renpy86;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * A thin, rounded progress bar for {@link LoadingOverlay}. The platform ProgressBar takes
 * its look from the activity's theme, which for RAPT's activity is the pre-Holo
 * {@code Theme.NoTitleBar.Fullscreen}: a yellow gradient bar and a striped animation.
 *
 * <p>Determinate progress glides to each new value; indeterminate progress is a segment
 * sweeping across the track.
 */
final class ThinProgressBar extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private boolean indeterminate = true;
    /** Shown fraction, 0..1, animated towards the target. */
    private float shown;
    private ValueAnimator glide;

    /** Position of the indeterminate segment, 0..1. */
    private float sweep;
    private final ValueAnimator sweeper = ValueAnimator.ofFloat(0f, 1f);

    ThinProgressBar(Context context, int trackColor, int fillColor) {
        super(context);
        track.setColor(trackColor);
        fill.setColor(fillColor);

        sweeper.setDuration(1400);
        sweeper.setRepeatCount(ValueAnimator.INFINITE);
        sweeper.setInterpolator(new LinearInterpolator());
        sweeper.addUpdateListener(a -> {
            sweep = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    void setIndeterminate(boolean value) {
        if (indeterminate == value) {
            return;
        }
        indeterminate = value;
        if (value) {
            if (glide != null) {
                glide.cancel();
            }
            shown = 0f;
            if (isAttachedToWindow()) {
                sweeper.start();
            }
        } else {
            sweeper.cancel();
        }
        invalidate();
    }

    /** Sets determinate progress, 0..1. */
    void setFraction(float target) {
        setIndeterminate(false);
        target = Math.max(0f, Math.min(1f, target));
        if (glide != null) {
            glide.cancel();
        }
        glide = ValueAnimator.ofFloat(shown, target);
        glide.setDuration(180);
        glide.setInterpolator(new DecelerateInterpolator());
        glide.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        glide.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (indeterminate) {
            sweeper.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        sweeper.cancel();
        if (glide != null) {
            glide.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = Math.round(4 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;

        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, track);

        if (indeterminate) {
            // A segment a third of the track wide, entering on the left, leaving on the right.
            float segment = w / 3f;
            float start = -segment + sweep * (w + segment);
            float left = Math.max(0f, start);
            float right = Math.min(w, start + segment);
            if (right > left) {
                rect.set(left, 0, right, h);
                canvas.drawRoundRect(rect, r, r, fill);
            }
        } else if (shown > 0f) {
            rect.set(0, 0, Math.max(h, w * shown), h);
            canvas.drawRoundRect(rect, r, r, fill);
        }
    }
}
