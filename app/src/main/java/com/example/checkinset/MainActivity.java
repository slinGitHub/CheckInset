package com.example.checkinset;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.*;
import androidx.annotation.NonNull;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.checkinset.model.DataModel;
import com.example.checkinset.model.ImageModel;
import com.example.checkinset.model.PointModel;
import com.example.checkinset.utils.DataStorage;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import android.view.MenuItem;
import androidx.appcompat.widget.Toolbar; // Oder MaterialToolbar
import android.view.Menu;
import android.widget.LinearLayout;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private String currentPhotoPath;
    private String currentImageTitle;

    private LinearLayout imageContainer;

    private FloatingActionButton addPointButton;

    private boolean isAddingPoint = false;

    private boolean isDeletingImage = false;

    // Parula-Farben
    private static final int[] PARULA_COLORS = {
            0xFF352A87, 0xFF343DAE, 0xFF276FB0, 0xFF21908D,
            0xFF22A884, 0xFF44BF70, 0xFF7AD151, 0xFFBADE24,
            0xFFFDE725, 0xFFFFFF00
    };

    // Unser globales Datenmodell, geladen/gespeichert via JSON
    private DataModel dataModel;

    // Map: Ordnet jedem CustomImageLayout das zugehörige ImageModel zu,
    // damit wir wissen, wohin neue Punkte gehören.
    private final Map<CustomImageLayout, ImageModel> layoutToImageMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Dein Layout

        // Toolbar holen
        Toolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);

        addPointButton = findViewById(R.id.addPointButton);
        imageContainer = findViewById(R.id.imageContainer);

        addPointButton.setOnClickListener(v -> {
            isAddingPoint = true;
            addPointButton.setEnabled(false);
            Toast.makeText(MainActivity.this, "Tippe auf ein Bild, um einen Punkt zu setzen.", Toast.LENGTH_SHORT).show();
        });

        // 1) Daten aus JSON laden
        dataModel = DataStorage.loadData(this);

        // 2) UI aufbauen
        loadUIFromDataModel();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Menü "aufblasen"
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true; // true = Menü wird angezeigt
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_capture_image) {
                checkCameraPermissionAndOpenCamera();
                return true;
        } else if (id == R.id.action_delete_image) {
                isDeletingImage = true;
                Toast.makeText(this, "Tippe nun auf das Bild, das du löschen möchtest.", Toast.LENGTH_SHORT).show();
                // ...
                return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Baut die komplette UI aus dataModel auf (alle Bilder, Punkte).
     */
    private void loadUIFromDataModel() {
        imageContainer.removeAllViews();
        layoutToImageMap.clear();

        for (ImageModel img : dataModel.images) {
            addImageToUI(img);
        }
    }

    /**
     * Zeigt ein Bild + Überschrift im UI und fügt alle Punkte hinzu.
     */
    private void addImageToUI(ImageModel imageModel) {
        // Bitmap laden + ggf. drehen
        Bitmap bitmap = BitmapFactory.decodeFile(imageModel.path);
        if (bitmap != null) {
            try {
                ExifInterface exif = new ExifInterface(imageModel.path);
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                int rotationDegrees = exifToDegrees(orientation);
                if (rotationDegrees != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(rotationDegrees);
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 2) Überschrift
        TextView titleView = new TextView(this);
        titleView.setText(imageModel.title);
        titleView.setTextSize(18);

        // 3) CustomImageLayout
        CustomImageLayout customLayout = new CustomImageLayout(this);
        customLayout.setImageBitmap(bitmap);

        // Setze OnTouchListener
        customLayout.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (isDeletingImage) {
                    removeImage(customLayout);
                    // Modus beenden
                    isDeletingImage = false;
                    return true;
                }
                // Beispiel: Punkt-Setz-Modus
                if (isAddingPoint) {
                    float xPercent = event.getX() / customLayout.getWidth();
                    float yPercent = event.getY() / customLayout.getHeight();
                    createPoint(customLayout, xPercent, yPercent);

                    isAddingPoint = false;
                    addPointButton.setEnabled(true);
                    return true;
                }
                // Oder: Bild-lösch-Modus, etc.
            }
            return false;
        });

        // 4) Parent Layout
        LinearLayout containerLayout = new LinearLayout(this);
        containerLayout.setOrientation(LinearLayout.VERTICAL);
        containerLayout.setPadding(0, 16, 0, 16);
        containerLayout.addView(titleView);
        containerLayout.addView(customLayout,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        imageContainer.addView(containerLayout);

        // 5) Layout erst nach Messung "fertig"
        customLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Wichtig: Listener entfernen, sonst wird er beim nächsten Layout-Pass wieder aufgerufen
                customLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // Jetzt hat customLayout eine feste Breite/Höhe
                // => Punkte hinzufügen und einfärben
                for (PointModel p : imageModel.points) {
                    addPointView(customLayout, p.xPercent, p.yPercent, p.color);
                }
                //updatePointColors(imageModel, customLayout);
                updateAllPointsColors();
            }
        });

        // Map für Datenmodell (falls du das brauchst)
        layoutToImageMap.put(customLayout, imageModel);
    }

    /**
     * Ruft die Kamera auf, nachdem ein Titel erfasst wurde.
     */
    private void showTitleInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Überschrift eingeben");

        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(editText);

        builder.setPositiveButton("OK", (dialog, which) -> {
            currentImageTitle = editText.getText().toString();
            checkCameraPermissionAndOpenCamera();
        });
        builder.setNegativeButton("Abbrechen", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void checkCameraPermissionAndOpenCamera() {
        // Prüfen, ob die Kamera-Berechtigung bereits erteilt ist
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Wenn nicht, bitten wir den Benutzer um Erlaubnis
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            // Kamera-Berechtigung vorhanden -> wir können das Foto aufnehmen
            dispatchTakePictureIntent();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Erlaubnis erteilt -> Kamera öffnen
                dispatchTakePictureIntent();
            } else {
                // Erlaubnis verweigert
                Toast.makeText(this, "Kamera-Berechtigung abgelehnt", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
                Toast.makeText(this, "Fehler beim Erstellen der Bilddatei", Toast.LENGTH_SHORT).show();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.checkinset.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Neues Bild in unser Datenmodell aufnehmen
            ImageModel newImg = new ImageModel();
            newImg.path = currentPhotoPath;
            newImg.title = currentImageTitle;

            // Datenmodell aktualisieren
            dataModel.images.add(newImg);

            // UI aktualisieren
            addImageToUI(newImg);

            // JSON speichern
            DataStorage.saveData(this, dataModel);
        }
    }

    /**
     * Erstellt einen neuen Punkt (wird sofort in dataModel + UI aufgenommen).
     */
    private void createPoint(CustomImageLayout layout, float xPercent, float yPercent) {
        ImageModel imgModel = layoutToImageMap.get(layout);
        if (imgModel == null) return;

        PointModel p = new PointModel();
        p.xPercent = xPercent;
        p.yPercent = yPercent;
        p.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        p.color = 0; // Farbe legen wir gleich in updatePointColors() fest

        // Zum Datenmodell hinzufügen
        imgModel.points.add(p);

        // UI-View anlegen
        addPointView(layout, xPercent, yPercent, p.color);

        // Farben aktualisieren
        //updatePointColors(imgModel, layout);
        updateAllPointsColors();

        // Speichern
        DataStorage.saveData(this, dataModel);
    }

    /**
     * Erzeugt einen View für den Punkt und platziert ihn im Layout.
     */
    private void addPointView(CustomImageLayout layout, float xPercent, float yPercent, int color) {
        int size = (int) (16 * getResources().getDisplayMetrics().density);

        View pointView = new View(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);

        int actualX = (int) (layout.getWidth() * xPercent) - size / 2;
        int actualY = (int) (layout.getHeight() * yPercent) - size / 2;
        lp.leftMargin = actualX;
        lp.topMargin = actualY;

        // Farbe (wird später in updatePointColors geändert, hier erstmal transparent)
        pointView.setBackgroundColor(color);
        layout.addView(pointView, lp);
    }

    /**
     * Aktualisiert die Farben der letzten 10 Punkte nach Parula-Skala,
     * wobei der neueste immer Gelb (Index=9) ist.
     */
//    private void updatePointColors(ImageModel imgModel, CustomImageLayout layout) {
//        List<PointModel> points = imgModel.points;
//        int total = points.size();
//        if (total == 0) return;
//
//        int startIndex = Math.max(0, total - 10);
//        // Ältere grau
//        for (int i = 0; i < startIndex; i++) {
//            points.get(i).color = 0xFF352A87;
//        }
//        // Letzte 10 -> Parula
//        int lastCount = Math.min(10, total);
//        int offset = 10 - lastCount;
//        for (int i = 0; i < lastCount; i++) {
//            int colorIndex = offset + i;
//            points.get(startIndex + i).color = PARULA_COLORS[colorIndex];
//        }
//
//        // Die Views anpassen
//        // Annahme: Views liegen im Layout in der Reihenfolge wie sie erstellt wurden
//        // 0: ImageView, 1..n: die Punkte
//        int childCount = layout.getChildCount();
//        int pointIndex = 1; // Start hinter dem ImageView
//        for (PointModel p : points) {
//            if (pointIndex >= childCount) break;
//            View pointView = layout.getChildAt(pointIndex);
//            setCircleBackground(pointView, p.color);
//            pointIndex++;
//        }
//    }

    private void updateAllPointsColors() {
        // 1) Alle Punkte sammeln
        List<PointModel> allPoints = new ArrayList<>();
        for (ImageModel im : dataModel.images) {
            allPoints.addAll(im.points);
        }

        // 2) Sortieren (z. B. nach timestamp)
        // Falls dein timestamp-Format "yyyy-MM-dd HH:mm:ss" ist, kannst du lexikographisch sortieren
        // oder in ein Date-Objekt parsen. Hier als Beispiel lex. Sort:
        Collections.sort(allPoints, (p1, p2) -> p1.timestamp.compareTo(p2.timestamp));
        // -> p1 < p2 heißt p1 ist älter

        // 3) Einfärben: Letzte 10 => Parula, Rest => Grau
        int total = allPoints.size();
        int startIndex = Math.max(0, total - 10);

        // Ältere
        for (int i = 0; i < startIndex; i++) {
            allPoints.get(i).color = 0xFF352A87; // oder Grau, 0xFF888888
        }

        // Jüngste 10
        int lastCount = Math.min(10, total);
        int offset = 10 - lastCount;
        for (int i = 0; i < lastCount; i++) {
            int colorIndex = offset + i;
            allPoints.get(startIndex + i).color = PARULA_COLORS[colorIndex];
        }

        // 4) Jetzt hat jeder PointModel in allPoints eine aktuelle color.
        // Da sie "by reference" auch in den einzelnen ImageModels stecken,
        // sind die Daten schon aktualisiert. => Wir müssen nur noch die UI updaten.

        // 5) Für jedes Bild: seine Points im Layout neu einfärben
        for (Map.Entry<CustomImageLayout, ImageModel> entry : layoutToImageMap.entrySet()) {
            CustomImageLayout layout = entry.getKey();
            ImageModel img = entry.getValue();

            // Rufe Hilfsmethode auf, die "nur" das UI aktualisiert
            applyPointColorsToLayout(img, layout);
        }
    }

    private void applyPointColorsToLayout(ImageModel imageModel, CustomImageLayout layout) {
        List<PointModel> points = imageModel.points;
        // Annahme: Kind 0 = ImageView, dahinter die Punkte
        int childCount = layout.getChildCount();
        int pointIndex = 1;
        for (PointModel p : points) {
            if (pointIndex >= childCount) break;
            View pointView = layout.getChildAt(pointIndex);
            setCircleBackground(pointView, p.color);
            pointIndex++;
        }
    }



    private void setCircleBackground(View view, int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);

        // Optional: Wenn der Kreis größer ist, kann man auch einen Rand definieren
        // gd.setStroke(2, 0xFF000000); // schwarzer Rand z. B.

        view.setBackground(gd);
    }

    /**
     * Entfernt alle Bilder + Punkte aus dem UI und dem Datenmodell.
     */
    private void removeImage(CustomImageLayout layout) {
        // 1) Das zugehörige ImageModel finden
        ImageModel imageModel = layoutToImageMap.get(layout);
        if (imageModel == null) return;

        // 2) Aus dem Datenmodell entfernen
        dataModel.images.remove(imageModel);

        // 3) Im UI entfernen:
        //    Du hast in addImageToUI(...) einen containerLayout erstellt (LinearLayout),
        //    das die Überschrift + das CustomImageLayout enthält.
        //    => containerLayout ist das "Parent" von customLayout,
        //       und dessen Parent ist 'imageContainer'.
        View containerLayout = (View) layout.getParent();
        if (containerLayout != null && containerLayout.getParent() instanceof ViewGroup) {
            ((ViewGroup) containerLayout.getParent()).removeView(containerLayout);
        }

        // 4) Aus dem Mapping entfernen
        layoutToImageMap.remove(layout);

        // 5) Daten speichern
        DataStorage.saveData(this, dataModel);

        // 6) Farbskala in allen Bildern anpassen
        updateAllPointsColors();

        Toast.makeText(this, "Bild gelöscht.", Toast.LENGTH_SHORT).show();
    }


    private int exifToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) return 90;
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) return 180;
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        return 0;
    }
}
