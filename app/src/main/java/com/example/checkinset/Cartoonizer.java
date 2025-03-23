package com.example.checkinset;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.SystemClock;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class Cartoonizer {

    // Konstanten zur Auswahl des Modells
    public static final int MODEL_DR = 0;
    public static final int MODEL_FP16 = 1;
    public static final int MODEL_INT8 = 2;

    private Context context;
    private int modelType;
    private int modelWidth = 512;   // Anpassen, falls dein Modell eine andere Eingabegröße erwartet
    private int modelHeight = 512;

    public Cartoonizer(Context context, int modelType) {
        this.context = context;
        this.modelType = modelType;
    }

    // Bestimmt anhand des modelType den Namen der Modelldatei in assets
    private String getModelFileName() {
        switch (modelType) {
            case MODEL_FP16:
                return "whitebox_cartoon_gan_fp16.tflite";
            case MODEL_INT8:
                return "whitebox_cartoon_gan_int8.tflite";
            case MODEL_DR:
            default:
                return "whitebox_cartoon_gan_dr.tflite";
        }
    }

    // Lädt das Modell aus dem assets-Ordner als MappedByteBuffer
    private MappedByteBuffer loadModelFile(String modelFilename) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Führt die Inferenz auf dem übergebenen Bitmap aus und gibt das Ergebnis sowie die Inferenzzeit zurück.
     *
     * @param inputBitmap Das Eingangs-Bitmap
     * @return Ein InferenceResult, das das cartoonisierte Bitmap und die Inferenzzeit (in ms) enthält.
     */
    public InferenceResult cartoonize(Bitmap inputBitmap) {
        // Bitmap auf Modell-Eingabegröße skalieren
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(inputBitmap, modelWidth, modelHeight, false);
        // Konvertiere Bitmap in ByteBuffer (normalisierte Float-Werte)
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);
        // Output-Puffer vorbereiten (Größe: 1 x width x height x 3, 4 Byte pro Float)
        ByteBuffer outputBuffer = ByteBuffer.allocateDirect(4 * modelWidth * modelHeight * 3);
        outputBuffer.order(ByteOrder.nativeOrder());

        Interpreter interpreter = null;
        try {
            String modelFile = getModelFileName();
            MappedByteBuffer tfliteModel = loadModelFile(modelFile);
            interpreter = new Interpreter(tfliteModel);
        } catch (IOException e) {
            e.printStackTrace();
            return new InferenceResult(null, 0);
        }

        long startTime = SystemClock.uptimeMillis();
        interpreter.run(inputBuffer, outputBuffer);
        long inferenceTime = SystemClock.uptimeMillis() - startTime;
        outputBuffer.rewind();

        Bitmap outputBitmap = convertByteBufferToBitmap(outputBuffer, modelWidth, modelHeight);
        if (interpreter != null) {
            interpreter.close();
        }
        return new InferenceResult(outputBitmap, inferenceTime);
    }

    // Wandelt ein Bitmap in einen ByteBuffer um, indem die Pixelwerte normalisiert werden (0-1)
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * width * height * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int pixel : pixels) {
            float r = ((pixel >> 16) & 0xFF) / 255.f;
            float g = ((pixel >> 8) & 0xFF) / 255.f;
            float b = (pixel & 0xFF) / 255.f;
            byteBuffer.putFloat(r);
            byteBuffer.putFloat(g);
            byteBuffer.putFloat(b);
        }
        return byteBuffer;
    }

    // Wandelt den Output-ByteBuffer (Float-Werte) zurück in ein Bitmap
    private Bitmap convertByteBufferToBitmap(ByteBuffer buffer, int width, int height) {
        buffer.rewind();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            float r = buffer.getFloat();
            float g = buffer.getFloat();
            float b = buffer.getFloat();
            int ir = Math.min(255, Math.max(0, (int) (r * 255)));
            int ig = Math.min(255, Math.max(0, (int) (g * 255)));
            int ib = Math.min(255, Math.max(0, (int) (b * 255)));
            pixels[i] = 0xFF000000 | (ir << 16) | (ig << 8) | ib;
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    /**
     * Container-Klasse, um das Ergebnis der Inferenz zu speichern.
     */
    public static class InferenceResult {
        public final Bitmap outputBitmap;
        public final long inferenceTimeMs;

        public InferenceResult(Bitmap outputBitmap, long inferenceTimeMs) {
            this.outputBitmap = outputBitmap;
            this.inferenceTimeMs = inferenceTimeMs;
        }
    }
}
