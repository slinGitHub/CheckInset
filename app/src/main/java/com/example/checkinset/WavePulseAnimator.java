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
 * Durch Überladen kann der maximale Ausbreitungsfaktor (maxScale) angepasst werden.
 */
public class WavePulseAnimator {
    private final ViewGroup container;
    private final int startX;
    private final int startY;
    private final int size;
    private final @ColorInt int pulseColor;
    private final long waveInterval;
    private final long waveDuration;
    private final float startScale;
    private final float maxScale;
    private final Handler handler;
    private final Runnable waveRunnable;
    private boolean running;

    /**
     * Privater Konstruktor für volle Konfiguration.
     */
    private WavePulseAnimator(ViewGroup container,
                              int startX,
                              int startY,
                              int size,
                              @ColorInt int pulseColor,
                              long waveInterval,
                              long waveDuration,
                              float startScale,
                              float maxScale) {
        this.container = container;
        this.startX = startX;
        this.startY = startY;
        this.size = size;
        this.pulseColor = pulseColor;
        this.waveInterval = waveInterval;
        this.waveDuration = waveDuration;
        this.startScale = startScale;
        this.maxScale = maxScale;
        this.handler = new Handler();
        this.running = false;

        // Lokale Referenz, damit waveRunnable in sich selbst referenzieren kann
        Runnable r = new Runnable() {
            @Override
            public void run() {
                createWave();
                if (running) {
                    handler.postDelayed(this, WavePulseAnimator.this.waveInterval);
                }
            }
        };
        this.waveRunnable = r;
    }

    /**
     * Standard-Konstruktor: maxScale = 2f, Intervall = 400ms, Dauer = 1000ms
     */
     public WavePulseAnimator(ViewGroup container,
                             int startX,
                             int startY,
                             int size,
                             @ColorInt int pulseColor,
                             float startScale,
                             float maxScale) {
        this(container, startX, startY, size, pulseColor, 2000, 3000, startScale, maxScale);
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

        wave.setScaleX(startScale);
        wave.setScaleY(startScale);
        wave.setAlpha(1f);
        container.addView(wave);

        // Animationssteuerung
        ObjectAnimator sx = ObjectAnimator.ofFloat(wave, "scaleX", startScale, maxScale);
        ObjectAnimator sy = ObjectAnimator.ofFloat(wave, "scaleY", startScale, maxScale);
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
