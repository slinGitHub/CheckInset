package com.example.checkinset.utils;

import android.graphics.Bitmap;

/**
 * Sammlung von Bildverarbeitungs-Hilfsmethoden
 */
public class ImageUtils {

    /**
     * Posterize/Comic-Effekt: Reduziert die Anzahl der Farbstufen auf "colorLevels".
     * Beispiel: colorLevels=8 => nur 8 Stufen pro Farbkanal.
     */
    public static Bitmap posterizeBitmap(Bitmap src, int colorLevels) {
        // Falls das Original null ist oder colorLevels < 2, direkt zurück
        if (src == null || colorLevels < 2) return src;

        // Erstelle ein neues, gleich großes Bitmap (mutable)
        Bitmap result = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);

        // Faktor berechnen (z.B. bei colorLevels=8 => factor=32)
        int factor = 256 / colorLevels;

        // Schleife über alle Pixel
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int color = src.getPixel(x, y);

                // Extrahiere R,G,B
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = (color) & 0xFF;
                int a = (color >> 24) & 0xFF;  // Alpha

                // R,G,B auf "colorLevels" Stufen reduzieren
                r = (r / factor) * factor;
                g = (g / factor) * factor;
                b = (b / factor) * factor;

                // Neuen Pixel zusammensetzen
                int newColor = (a << 24) | (r << 16) | (g << 8) | b;
                result.setPixel(x, y, newColor);
            }
        }
        return result;
    }
}
