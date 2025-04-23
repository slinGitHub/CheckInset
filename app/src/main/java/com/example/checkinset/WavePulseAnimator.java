package com.example.checkinset;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.ColorInt;

/**
 * WavePulseAnimator erzeugt eine sich wiederholende Wellenanimation an einer spezifischen Koordinate.
 * Die Wellenfarbe entspricht der ursprünglichen Farbe des Punkts.
 */
public class WavePulseAnimator {
    private final ViewGroup container;
    private final int startX;
    private final int startY;
    private final int size;
    private final @ColorInt int pulseColor;
    private final long waveInterval;
    private final long waveDuration;
    private final float maxScale;
    private final Handler handler;
    private final Runnable waveRunnable;
    private boolean running;

    /**
     * Konstruktor: definiert Position, Größe und Farbe der Welle
     * @param container Eltern-Container, in dem die Wellen-Views erzeugt werden
     * @param startX x-Koordinate in px, an der die Welle startet
     * @param startY y-Koordinate in px, an der die Welle startet
     * @param size Größe der Welle in px (Durchmesser)
     * @param pulseColor Farbe der Wellenlinie
     */
    public WavePulseAnimator(ViewGroup container,
                             int startX,
                             int startY,
                             int size,
                             @ColorInt int pulseColor) {
        this.container = container;
        this.startX = startX;
        this.startY = startY;
        this.size = size;
        this.pulseColor = pulseColor;
        this.waveInterval = 3000;
        this.waveDuration = 5000;
        this.maxScale = 3f;
        this.handler = new Handler();
        this.running = false;

        this.waveRunnable = new Runnable() {
            @Override
            public void run() {
                createWave();
                if (running) handler.postDelayed(this, waveInterval);
            }
        };
    }

    /**
     * Startet die Wellenanimation (wiederholt sich, bis stop() aufgerufen wird)
     */
    public void start() {
        if (running) return;
        running = true;
        handler.post(waveRunnable);
    }

    /**
     * Stoppt alle weiteren Wellen und entfernt bestehende Wellen-Views
     */
    public void stop() {
        running = false;
        handler.removeCallbacks(waveRunnable);
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            View child = container.getChildAt(i);
            if ("wave".equals(child.getTag())) container.removeViewAt(i);
        }
    }

    /**
     * Erzeugt eine einzelne Welle an der übergebenen Koordinate.
     */
    private void createWave() {
        Context ctx = container.getContext();
        View wave = new View(ctx);
        wave.setTag("wave");

        // LayoutParams mit exakter Position und Größe
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.leftMargin = startX;
        lp.topMargin = startY;
        wave.setLayoutParams(lp);

        // Hintergrund: kreisförmiger Stroke
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setStroke(dpToPx(ctx, 2), pulseColor);
        wave.setBackground(gd);

        wave.setScaleX(0f);
        wave.setScaleY(0f);
        wave.setAlpha(1f);
        container.addView(wave);

        // Animationssteuerung
        ObjectAnimator sx = ObjectAnimator.ofFloat(wave, "scaleX", 0f, maxScale);
        ObjectAnimator sy = ObjectAnimator.ofFloat(wave, "scaleY", 0f, maxScale);
        ObjectAnimator al = ObjectAnimator.ofFloat(wave, "alpha", 0.5f, 0f);
        sx.setDuration(waveDuration);
        sy.setDuration(waveDuration);
        al.setDuration(waveDuration);

        sx.start(); sy.start(); al.start();

        al.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                container.removeView(wave);
            }
        });
    }

    private int dpToPx(Context ctx, float dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}

