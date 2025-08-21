package com.example.checkinset;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.math.MathUtils;

import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;

import org.tensorflow.lite.gpu.GpuDelegate;

public class ImageManager {

    public static final int REQUEST_CAMERA_PERMISSION = 1001;
    public static final int REQUEST_IMAGE_CAPTURE = 1;
    public static final int REQUEST_PICK_IMAGE = 2;

    private Activity activity;
    private ImageResultCallback callback;
    private String currentPhotoPath;
    private String currentImageTitle;

    private final Context context;
    private Interpreter esrganInterpreter = null;
    private GpuDelegate gpuDelegate = null;

    public interface ImageResultCallback {
        void onImageCaptured(String imagePath, String originalImagePath, String imageTitle);

        //void onImagePicked(String imagePath);
        void onError(String errorMessage);
    }

    public ImageManager(Activity activity, ImageResultCallback callback, Context context) {
        this.activity = activity;
        this.callback = callback;
        this.context = context.getApplicationContext();
        try {
            initEsrgan();
        } catch (IOException e) {
            //Log.e("ImageManager", "Failed to load ESRGAN model", e);
        }
    }

    public void setCurrentImageTitle(String title) {
        currentImageTitle = title;
    }

    public void checkCameraPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            dispatchTakePictureIntent();
        }
    }

    public void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        activity.startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    public void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
                Toast.makeText(activity, "Fehler beim Erstellen der Bilddatei", Toast.LENGTH_SHORT).show();
                callback.onError("Fehler beim Erstellen der Bilddatei");
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(activity,
                        "com.example.checkinset.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                activity.startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    // Hilfsfunktion: Bitmap zu Float-Array (normalisiert auf 0..1)
    private float[][][][] bitmapToFloatArray(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float[][][][] input = new float[1][height][width][3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int px = bitmap.getPixel(x, y);
                input[0][y][x][0] = ((px >> 16) & 0xFF) / 255.0f; // R
                input[0][y][x][1] = ((px >> 8) & 0xFF) / 255.0f;  // G
                input[0][y][x][2] = (px & 0xFF) / 255.0f;         // B
            }
        }
        return input;
    }

    // Hilfsfunktion: Float-Array zu Bitmap
    private Bitmap floatArrayToBitmap(float[][][][] output) {
        int height = output[0].length;
        int width = output[0][0].length;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = Math.min(255, Math.max(0, (int)(output[0][y][x][0] * 255)));
                int g = Math.min(255, Math.max(0, (int)(output[0][y][x][1] * 255)));
                int b = Math.min(255, Math.max(0, (int)(output[0][y][x][2] * 255)));
                int color = 0xFF << 24 | (r << 16) | (g << 8) | b;
                bmp.setPixel(x, y, color);
            }
        }
        return bmp;
    }

    // Interpreter initialisieren (einmalig)
    private void initEsrgan(Context context) throws IOException {
        if (esrganInterpreter == null) {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd("edsr.tflite");
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            MappedByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            // GPU Delegate aktivieren
            gpuDelegate = new GpuDelegate();
            Interpreter.Options options = new Interpreter.Options().addDelegate(gpuDelegate);
            esrganInterpreter = new Interpreter(modelBuffer, options);
        }
    }

    private void processCapturedImage() {
        // 1. Originalbild laden
        Bitmap originalBitmap = BitmapFactory.decodeFile(currentPhotoPath);
        originalBitmap = rotateImageIfRequired(currentPhotoPath, originalBitmap);

        // 2. Bild zuschneiden – hier rechteckiger Zuschnitt (SquarePadder liefert z.B. ein quadratisches Bild)
        Bitmap croppedBitmap = SquarePadder.cropToSquare(originalBitmap);

        // ➕ HIER: Speichere das unbearbeitete, nur zugeschnittene Bild
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String originalImagePath = saveBitmap(croppedBitmap, "CROPPED_" + timeStamp + ".jpg");

        // Das zugeschnittene Bild in Graustufen umwandeln
        Bitmap grayscaleBitmap = toGrayscale(croppedBitmap, 30);

        // 3. Bild auf Modell-Eingabegröße skalieren (hier: 512x512, passe ggf. an dein Modell an)
        int modelWidth = 512;
        int modelHeight = 512;
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(grayscaleBitmap, modelWidth, modelHeight, false);

        // 4. Cartoonisierung: Erzeuge einen Cartoonizer (hier mit Modelltyp MODEL_DR) und führe die Inferenz durch
        Cartoonizer cartoonizer = new Cartoonizer(activity, Cartoonizer.MODEL_DR);
        Cartoonizer.InferenceResult result = cartoonizer.cartoonize(resizedBitmap);

        // 5. Das Ergebnis-Bitmap entnehmen
        Bitmap finalCartoon = result.outputBitmap;
        // Optional: Anzeige des cartoonisierten Bildes in einer ImageView
        // cartoonImageView.setImageBitmap(finalCartoon);

        Bitmap warmCartoon = adjustWarmth(finalCartoon, 50);

        // 6. Speichern des Cartoon-Bildes (saveBitmap muss das Bitmap speichern und den Dateipfad zurückgeben)
        timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String cartoonImagePath = saveBitmap(warmCartoon, "CARTOON_" + timeStamp + ".jpg");

        // 7. Die Pfade (Originalbild & cartoonisiertes Bild) persistieren oder via Callback zurückgeben
        //saveImagePaths(currentPhotoPath, cartoonPath);
        // z.B. Rückgabe an den Aufrufer:
        callback.onImageCaptured(cartoonImagePath,originalImagePath, currentImageTitle);
    }

    private Bitmap adjustWarmth(Bitmap original, int warmthPercent) {
        int width = original.getWidth();
        int height = original.getHeight();
        Bitmap warmBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(warmBitmap);
        Paint paint = new Paint();

        ColorMatrix colorMatrix = new ColorMatrix();

        // Berechne den Wärme-Faktor (0 = keine Änderung, 1 = volle Wärme)
        float warmthFactor = warmthPercent / 100f;

        // Verstärke Rot- und Gelbtöne (indem Blau reduziert wird)
        float[] warmMatrix = {
                1.0f + (0.2f * warmthFactor), 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f + (0.1f * warmthFactor), 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f - (0.1f * warmthFactor), 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        };

        colorMatrix.set(warmMatrix);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvas.drawBitmap(original, 0, 0, paint);

        return warmBitmap;
    }

    // Methode zur Umwandlung eines Bitmaps in Graustufen
    private Bitmap toGrayscale(Bitmap original, int grayscalePercent) {
        int width = original.getWidth();
        int height = original.getHeight();
        Bitmap grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayscale);
        Paint paint = new Paint();

        ColorMatrix colorMatrix = new ColorMatrix();
        // Berechne den Sättigungsfaktor: 1.0 entspricht vollem Original, 0.0 ist komplett graustufig.
        float saturation = 1f - (grayscalePercent / 100f);
        colorMatrix.setSaturation(saturation);

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvas.drawBitmap(original, 0, 0, paint);

        return grayscale;
    }

    private String saveBitmap(Bitmap bitmap, String fileName) {
        File storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = new File(storageDir, fileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(imageFile);
            // Komprimiere das Bitmap als JPEG (Qualität: 90)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            return imageFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Wird in onActivityResult() aufgerufen – hier unterscheiden wir Kamera und Galerie.
     */
    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            //callback.onImageCaptured(currentPhotoPath, currentImageTitle);
            processCapturedImage();
        } else if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedUri = data.getData();
            if (selectedUri != null) {
                String copiedPath = copyImageToAppStorage(selectedUri);
                if (copiedPath != null) {
                    currentPhotoPath = copiedPath;
                    processCapturedImage();
                    //callback.onImagePicked(copiedPath);
                } else {
                    callback.onError("Fehler beim Kopieren des Bildes.");
                }
            }
        }
    }


    /**
     * Kopiert ein Bild von der Galerie in den app‑internen Pictures-Ordner.
     * Dadurch hast du einen persistierenden Pfad, auch wenn sich der originale Pfad ändert.
     */
    public String copyImageToAppStorage(Uri uri) {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = activity.getContentResolver().openInputStream(uri);
            File imageFile = createImageFile();
            os = new FileOutputStream(imageFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();
            return imageFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (is != null) is.close();
                if (os != null) os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Bitmap rotateImageIfRequired(String imagePath, Bitmap bitmap) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            int rotationDegrees = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotationDegrees = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotationDegrees = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotationDegrees = 270;
                    break;
                default:
                    return bitmap; // Keine Drehung nötig
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (IOException e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    /** Lädt den TFLite-Interpreter aus assets/esrgan_fp16.tflite */
    private void initEsrgan() throws IOException {
        // Asset in einen MappedByteBuffer laden (effizient für große Modelle)
        MappedByteBuffer modelBuffer = loadModelFile("esrgan_fp16.tflite");

        // Interpreter-Optionen: z.B. mehrere Threads, GPU etc.
        Interpreter.Options opts = new Interpreter.Options()
                .setNumThreads(4);
                //.addDelegate(new GpuDelegate());  // optional: GPU-Beschleunigung

        esrganInterpreter = new Interpreter(modelBuffer, opts);
    }

    /** Lädt eine .tflite aus dem assets-Ordner und returned einen MappedByteBuffer */
    private MappedByteBuffer loadModelFile(String assetFilename) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(assetFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel channel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // Hilfsmethoden zum Konvertieren Bitmap ↔ Float-Array:
    private float[][][][] makeInputBuffer(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        float[][][][] buf = new float[1][h][w][3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = bmp.getPixel(x, y);
                buf[0][y][x][0] = Color.red(p);
                buf[0][y][x][1] = Color.green(p);
                buf[0][y][x][2] = Color.blue(p);
            }
        }
        return buf;
    }

    private Bitmap makeBitmapFromOutput(float[][][][] buf) {
        int h = buf[0].length, w = buf[0][0].length;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = MathUtils.clamp(Math.round(buf[0][y][x][0]), 0, 255);
                int g = MathUtils.clamp(Math.round(buf[0][y][x][1]), 0, 255);
                int b = MathUtils.clamp(Math.round(buf[0][y][x][2]), 0, 255);
                bmp.setPixel(x, y, Color.rgb(r, g, b));
            }
        }
        return bmp;
    }

    // Am Ende der Klasse: Delegate freigeben
    public void close() {
        if (esrganInterpreter != null) {
            esrganInterpreter.close();
            esrganInterpreter = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
    }
}

