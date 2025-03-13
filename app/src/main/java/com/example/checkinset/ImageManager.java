package com.example.checkinset;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;

public class ImageManager {

    public static final int REQUEST_CAMERA_PERMISSION = 1001;
    public static final int REQUEST_IMAGE_CAPTURE = 1;
    public static final int REQUEST_PICK_IMAGE = 2;

    private Activity activity;
    private ImageResultCallback callback;
    private String currentPhotoPath;
    private String currentImageTitle;

    public interface ImageResultCallback {
        void onImageCaptured(String imagePath, String imageTitle);
        void onImagePicked(String imagePath);
        void onError(String errorMessage);
    }

    public ImageManager(Activity activity, ImageResultCallback callback) {
        this.activity = activity;
        this.callback = callback;
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

    /**
     * Wird in onActivityResult() aufgerufen – hier unterscheiden wir Kamera und Galerie.
     */
    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            callback.onImageCaptured(currentPhotoPath, currentImageTitle);
        } else if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedUri = data.getData();
            if (selectedUri != null) {
                String copiedPath = copyImageToAppStorage(selectedUri);
                if (copiedPath != null) {
                    callback.onImagePicked(copiedPath);
                } else {
                    callback.onError("Fehler beim Kopieren des Bildes.");
                }
            }
        }
    }

    public String getRealPathFromURI(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = activity.getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String filePath = cursor.getString(columnIndex);
            cursor.close();
            return filePath;
        }
        return null;
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
}

