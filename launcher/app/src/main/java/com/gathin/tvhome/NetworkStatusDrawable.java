package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * The small badge after the Wi-Fi network's name: a spinner while the wake-time check asks the network a
 * real question, a check that fades out when it answers, and an orange warning triangle while the TV is
 * connected without internet. Drawn by hand as one small bitmap a frame, like the rest of the top bar.
 */
final class NetworkStatusDrawable extends Drawable {
    static final int IDLE = 0;
    static final int CHECKING = 1;
    static final int OK = 2;
    static final int WARNING = 3;

    private static final int GREEN = 0xFF34C759;
    private static final int ORANGE = 0xFFFF9F0A;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path segment = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float density;
    private final int size;

    private int state = IDLE;
    private int alpha = 255;
    private float rotation;
    private float checkProgress;
    /** Guards the callbacks of an animation that a newer state replaced. */
    private int generation;
    private ValueAnimator spin, check, fade;

    NetworkStatusDrawable(Context context, float density) {
        this.density = density;
        this.size = Math.round(14 * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    int state() {
        return state;
    }

    void showChecking() {
        if (state == CHECKING && spin != null && spin.isRunning()) return;
        generation++;
        cancel();
        state = CHECKING;
        rotation = 0;
        checkProgress = 0;
        alpha = 255;
        spin = ValueAnimator.ofFloat(0, 360);
        spin.setDuration(900);
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.setInterpolator(new LinearInterpolator());
        spin.addUpdateListener(a -> {
            rotation = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        spin.start();
        invalidateSelf();
    }

    void showOk() {
        generation++;
        cancel();
        final int gen = generation;
        state = OK;
        checkProgress = 0;
        alpha = 255;
        check = ValueAnimator.ofFloat(0, 1);
        check.setDuration(260);
        check.setInterpolator(new DecelerateInterpolator());
        check.addUpdateListener(a -> {
            checkProgress = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        check.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (gen != generation) return;
                handler.postDelayed(() -> fadeOut(gen), 450);
            }
        });
        check.start();
        invalidateSelf();
    }

    private void fadeOut(int gen) {
        if (gen != generation || state != OK) return;
        fade = ValueAnimator.ofInt(255, 0);
        fade.setDuration(300);
        fade.addUpdateListener(a -> {
            if (gen != generation) return;
            alpha = (int) a.getAnimatedValue();
            invalidateSelf();
        });
        fade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (gen != generation) return;
                state = IDLE;
                alpha = 255;   // the next state starts fully visible
                invalidateSelf();
            }
        });
        fade.start();
    }

    void showWarning() {
        if (state == WARNING) return;
        generation++;
        cancel();
        state = WARNING;
        alpha = 255;
        invalidateSelf();
    }

    /** Drops the warning; a running spinner or the fading check keeps its own state. */
    void clearWarning() {
        if (state != WARNING) return;
        generation++;
        cancel();
        state = IDLE;
        alpha = 255;
        invalidateSelf();
    }

    void stop() {
        generation++;
        cancel();
        state = IDLE;
        alpha = 255;
        invalidateSelf();
    }

    private void cancel() {
        ValueAnimator s = spin, c = check, f = fade;
        spin = null;
        check = null;
        fade = null;
        if (s != null) s.cancel();
        if (c != null) c.cancel();
        if (f != null) f.cancel();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void draw(Canvas canvas) {
        if (state == IDLE) return;
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        paint.setAlpha(alpha);
        if (state == CHECKING) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f * density);
            paint.setColor(GREEN);
            RectF oval = new RectF(b.left + 2 * density, b.top + 2 * density,
                    b.right - 2 * density, b.bottom - 2 * density);
            canvas.save();
            canvas.rotate(rotation, b.centerX(), b.centerY());
            canvas.drawArc(oval, -90, 100, false, paint);
            canvas.restore();
            return;
        }
        if (state == OK) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.9f * density);
            paint.setColor(GREEN);
            path.reset();
            path.moveTo(b.left + w * 0.18f, b.top + h * 0.54f);
            path.lineTo(b.left + w * 0.42f, b.top + h * 0.77f);
            path.lineTo(b.left + w * 0.83f, b.top + h * 0.27f);
            measure.setPath(path, false);
            float length = measure.getLength();
            if (length > 0 && checkProgress > 0) {
                segment.reset();
                measure.getSegment(0, length * checkProgress, segment, true);
                canvas.drawPath(segment, paint);
            }
            return;
        }
        // WARNING: a filled orange triangle with a white exclamation mark.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ORANGE);
        path.reset();
        path.moveTo(b.centerX(), b.top + h * 0.07f);
        path.lineTo(b.right - w * 0.05f, b.bottom - h * 0.07f);
        path.lineTo(b.left + w * 0.05f, b.bottom - h * 0.07f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        canvas.drawLine(b.centerX(), b.top + h * 0.36f, b.centerX(), b.top + h * 0.60f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(b.centerX(), b.top + h * 0.73f, 0.85f * density, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // The colours are part of the state machine; a tint from the TextView would erase them.
    }

    @Override
    public void setTintList(ColorStateList tint) {
        // The TextView's drawableTint tints the VPN shield and the pill icon; this badge keeps its own colours.
    }

    @Override
    public void setTint(int tintColor) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
