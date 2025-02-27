package com.example.checkinset;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * FrameLayout, das ein ImageView enthält und Touch-Events abfangen kann,
 * um ggf. einen Punkt zu platzieren.
 */
public class CustomImageLayout extends FrameLayout {

    private ImageView imageView; // Das angezeigte Bild

    public CustomImageLayout(Context context) {
        super(context);
        init(context);
    }

    public CustomImageLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CustomImageLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Das Layout selbst ist ein FrameLayout, in dem wir ein ImageView platzieren
        imageView = new ImageView(context);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // ImageView als Kind-View hinzufügen
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        addView(imageView, params);
    }

    public ImageView getImageView() {
        return imageView;
    }

    /**
     * Setzt das Bitmap für das ImageView.
     */
    public void setImageBitmap(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
    }

    /**
     * Liefert das aktuell angezeigte Bitmap zurück (falls nötig).
     */
    public Bitmap getBitmap() {
        imageView.buildDrawingCache();
        return imageView.getDrawingCache();
    }

    /**
     * Optional: Du kannst hier onMeasure anpassen, falls du
     * das Layoutverhalten steuern willst.
     */

    /**
     * Hier können wir das Touch-Event abfangen. Die Activity entscheidet dann,
     * ob wir gerade im "Punkt-Setz-Modus" sind.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Wir leiten das Event nach oben an die Activity weiter
        return super.onTouchEvent(event);
    }
}

