package com.example.checkinset;

import android.graphics.Bitmap;
import android.graphics.Canvas;

public class SquarePadder {
    public static class PaddedImage {
        public Bitmap paddedBitmap;
        public int originalWidth, originalHeight, offsetX, offsetY;

        public PaddedImage(Bitmap paddedBitmap, int originalWidth, int originalHeight, int offsetX, int offsetY) {
            this.paddedBitmap = paddedBitmap;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    public static Bitmap cropToSquare(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int newSize = Math.min(width, height);
        int offsetX = (width - newSize) / 2;
        int offsetY = (height - newSize) / 2;
        return Bitmap.createBitmap(bitmap, offsetX, offsetY, newSize, newSize);
    }

    public static PaddedImage padToSquare(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.max(width, height);

        Bitmap paddedBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(paddedBitmap);
        int offsetX = (size - width) / 2;
        int offsetY = (size - height) / 2;
        canvas.drawBitmap(bitmap, offsetX, offsetY, null);

        return new PaddedImage(paddedBitmap, width, height, offsetX, offsetY);
    }

    public static Bitmap revertPadToSquare(PaddedImage paddedImage) {
        return Bitmap.createBitmap(paddedImage.paddedBitmap, paddedImage.offsetX, paddedImage.offsetY,
                paddedImage.originalWidth, paddedImage.originalHeight);
    }
}
