package com.example.checkinset;

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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.checkinset.model.DataModel;
import com.example.checkinset.model.ImageModel;
import com.example.checkinset.model.PointModel;
import com.example.checkinset.utils.DataStorage;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import android.animation.ValueAnimator;

public class MainActivity extends AppCompatActivity implements ImageManager.ImageResultCallback {

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

    // Globales Datenmodell, geladen/gespeichert via JSON
    private DataModel dataModel;

    // Map: Ordnet jedem CustomImageLayout das zugehörige ImageModel zu
    private final Map<CustomImageLayout, ImageModel> layoutToImageMap = new HashMap<>();

    private ImageManager imageManager;
    private String currentImageTitle;

    // Bottom Sheet Komponenten
    private LinearLayout bottomSheet;
    private TextView tvTimestamp, tvCoordinates;
    private Button btnDeletePoint;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;

    // Global gespeicherter aktuell ausgewählter Punkt und zugehöriges Layout
    private PointModel currentPoint;
    private CustomImageLayout currentLayout;

    private ValueAnimator pulseAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar initialisieren
        Toolbar topAppBar = findViewById(R.id.topAppBar);
        setSupportActionBar(topAppBar);

        addPointButton = findViewById(R.id.addPointButton);
        imageContainer = findViewById(R.id.imageContainer);

        //BottomSheet initialisieren
        bottomSheet = findViewById(R.id.bottom_sheet);


        tvTimestamp = findViewById(R.id.tvTimestamp);
        tvCoordinates = findViewById(R.id.tvCoordinates);
        btnDeletePoint = findViewById(R.id.btnDeletePoint);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        bottomSheet.post(() -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN));
        bottomSheetBehavior.setPeekHeight(bottomSheet.getHeight());

        // Aktion des Lösch-Buttons im Bottom Sheet
        btnDeletePoint.setOnClickListener(v -> {
            if (currentPoint != null && currentLayout != null) {
                ImageModel imgModel = layoutToImageMap.get(currentLayout);
                if (imgModel != null) {
                    imgModel.points.remove(currentPoint);
                    // UI neu aufbauen (alternativ: gezielt den Punkt entfernen)
                    loadUIFromDataModel();
                    DataStorage.saveData(MainActivity.this, dataModel);
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    Toast.makeText(MainActivity.this, "Punkt gelöscht.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        addPointButton.setOnClickListener(v -> {
            isAddingPoint = true;
            addPointButton.setEnabled(false);
            Toast.makeText(MainActivity.this, "Tippe auf ein Bild, um einen Punkt zu setzen.", Toast.LENGTH_SHORT).show();
        });

        // Datenmodell laden
        dataModel = DataStorage.loadData(this);

        // UI aus dem Datenmodell aufbauen
        loadUIFromDataModel();

        // ImageManager initialisieren
        imageManager = new ImageManager(this, this);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_capture_image) {
            // Vor der Kameraaufnahme evtl. Titel abfragen
            showTitleInputDialog(true);
            return true;
        } else if (id == R.id.action_pick_image) {
            showTitleInputDialog(false);
            return true;
        } else if (id == R.id.action_delete_image) {
            isDeletingImage = true;
            Toast.makeText(this, "Tippe nun auf das Bild, das du löschen möchtest.", Toast.LENGTH_SHORT).show();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void showTitleInputDialog(boolean fromCamera) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Überschrift eingeben");

        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(editText);

        builder.setPositiveButton("OK", (dialog, which) -> {
            currentImageTitle = editText.getText().toString();
            imageManager.setCurrentImageTitle(currentImageTitle);
            if (fromCamera) {
                imageManager.checkCameraPermissionAndOpenCamera();
            } else {
                imageManager.openGallery();
            }
        });
        builder.setNegativeButton("Abbrechen", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Baut die komplette UI aus dem Datenmodell auf.
     */
    private void loadUIFromDataModel() {
        imageContainer.removeAllViews();
        layoutToImageMap.clear();

        for (ImageModel img : dataModel.images) {
            addImageToUI(img);
        }
    }

    /**
     * Zeigt ein Bild samt Überschrift und Punkten.
     */
    private void addImageToUI(ImageModel imageModel) {
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

        TextView titleView = new TextView(this);
        titleView.setText(imageModel.title);
        titleView.setTextSize(18);
        titleView.setTextColor(0xFFFFFFFF); // Weißer Text

        CustomImageLayout customLayout = new CustomImageLayout(this);
        customLayout.setImageBitmap(bitmap);

        // TouchListener für das CustomImageLayout:
        customLayout.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Falls das Bild gelöscht werden soll:
                if (isDeletingImage) {
                    removeImage(customLayout);
                    isDeletingImage = false;
                    return true;
                }
                // Falls ein neuer Punkt gesetzt werden soll:
                if (isAddingPoint) {
                    float xPercent = event.getX() / customLayout.getWidth();
                    float yPercent = event.getY() / customLayout.getHeight();
                    createPoint(customLayout, xPercent, yPercent);
                    isAddingPoint = false;
                    addPointButton.setEnabled(true);
                    return true;
                }
                // Sonst: Ermittle den am nächsten liegenden Punkt zum Klick
                float xPercent = event.getX() / customLayout.getWidth();
                float yPercent = event.getY() / customLayout.getHeight();
                ImageModel imgModel = layoutToImageMap.get(customLayout);
                if (imgModel != null && !imgModel.points.isEmpty()) {
                    PointModel nearestPoint = getClosestPoint(imgModel, xPercent, yPercent);
                    if (nearestPoint != null) {
                        currentPoint = nearestPoint;
                        currentLayout = customLayout;
                        showPointDetails(nearestPoint, customLayout);
                    }
                }
                return true;
            }
            return false;
        });

        LinearLayout containerLayout = new LinearLayout(this);
        containerLayout.setOrientation(LinearLayout.VERTICAL);
        containerLayout.setPadding(0, 16, 0, 16);
        containerLayout.addView(titleView);
        containerLayout.addView(customLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        imageContainer.addView(containerLayout);

        customLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                customLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                for (PointModel p : imageModel.points) {
                    addPointView(customLayout, p.xPercent, p.yPercent, p.color, p);
                }
                updateAllPointsColors();
            }
        });

        layoutToImageMap.put(customLayout, imageModel);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        imageManager.handleActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onImageCaptured(String imagePath, String imageTitle) {
        ImageModel newImg = new ImageModel();
        newImg.path = imagePath;
        newImg.title = imageTitle;
        dataModel.images.add(newImg);
        addImageToUI(newImg);
        DataStorage.saveData(this, dataModel);
    }

    @Override
    public void onImagePicked(String imagePath) {
        ImageModel newImg = new ImageModel();
        newImg.path = imagePath;
        newImg.title = (currentImageTitle != null && !currentImageTitle.isEmpty())
                ? currentImageTitle
                : "Galerie-Bild";
        dataModel.images.add(newImg);
        addImageToUI(newImg);
        DataStorage.saveData(this, dataModel);
    }

    @Override
    public void onError(String errorMessage) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

            // Falls ein Punkt animiert wird, stoppe die Animation
            if (currentPoint != null && currentLayout != null) {
                View pointView = getPointView(currentLayout, currentPoint);
                if (pointView != null) stopPulseAnimation(pointView);
            }
            return; // Verhindert, dass die App geschlossen wird
        }
        super.onBackPressed();
    }

    private void createPoint(CustomImageLayout layout, float xPercent, float yPercent) {
        ImageModel imgModel = layoutToImageMap.get(layout);
        if (imgModel == null) return;

        PointModel p = new PointModel();
        p.xPercent = xPercent;
        p.yPercent = yPercent;
        p.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        p.color = 0;

        imgModel.points.add(p);
        addPointView(layout, xPercent, yPercent, p.color, p);
        updateAllPointsColors();
        DataStorage.saveData(this, dataModel);
    }

    private void addPointView(CustomImageLayout layout, float xPercent, float yPercent, int color, PointModel point) {
        int size = (int) (16 * getResources().getDisplayMetrics().density);
        View pointView = new View(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        int actualX = (int) (layout.getWidth() * xPercent) - size / 2;
        int actualY = (int) (layout.getHeight() * yPercent) - size / 2;
        lp.leftMargin = actualX;
        lp.topMargin = actualY;
        pointView.setBackgroundColor(color);
        pointView.setTag(point); // Speichert das PointModel als Tag!
        layout.addView(pointView, lp);
    }

    /**
     * Ermittelt den Punkt, der den angegebenen x- und y-Prozentwerten am nächsten liegt.
     *
     * @param imageModel Das ImageModel, in dem die Punkte gespeichert sind.
     * @param xPercent   Der x-Wert (0..1), an dem gesucht wird.
     * @param yPercent   Der y-Wert (0..1), an dem gesucht wird.
     * @return Der am nächsten liegende Punkt oder null, falls keine Punkte vorhanden.
     */
    private PointModel getClosestPoint(ImageModel imageModel, float xPercent, float yPercent) {
        PointModel closestPoint = null;
        double minDistance = Double.MAX_VALUE;

        for (PointModel point : imageModel.points) {
            double dx = xPercent - point.xPercent;
            double dy = yPercent - point.yPercent;
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = point;
            }
        }
        return closestPoint;
    }

    /**
     * Zeigt die Details des ausgewählten Punktes im Bottom Sheet an.
     */
    private void showPointDetails(PointModel point, CustomImageLayout layout) {
        if (bottomSheetBehavior == null) {
            Log.e("BottomSheet", "❌ Fehler: BottomSheetBehavior ist null!");
            return;
        }

        // Setze Text für den ausgewählten Punkt
        tvTimestamp.setText("Erstellt am: " + point.timestamp);
        int absX = (int) (layout.getWidth() * point.xPercent);
        int absY = (int) (layout.getHeight() * point.yPercent);
        tvCoordinates.setText("X: " + absX + ", Y: " + absY);

        // Bottom Sheet anzeigen
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        // Falls ein anderer Punkt bereits pulsiert, Animation stoppen
        if (currentPoint != null && currentLayout != null && currentPoint != point) {
            View oldPointView = getPointView(currentLayout, currentPoint);
            if (oldPointView != null) stopPulseAnimation(oldPointView);
        }

        // Neuer aktueller Punkt
        currentPoint = point;
        currentLayout = layout;

        // Starte die Animation für den neuen Punkt
        View pointView = getPointView(layout, point);
        if (pointView != null) startPulseAnimation(pointView);
    }

    private View getPointView(CustomImageLayout layout, PointModel point) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child.getTag() instanceof PointModel) {
                PointModel taggedPoint = (PointModel) child.getTag();
                if (taggedPoint == point) {
                    return child;
                }
            }
        }
        return null;
    }


    private void updateAllPointsColors() {
        List<PointModel> allPoints = new ArrayList<>();
        for (ImageModel im : dataModel.images) {
            allPoints.addAll(im.points);
        }
        Collections.sort(allPoints, (p1, p2) -> p1.timestamp.compareTo(p2.timestamp));
        int total = allPoints.size();
        int startIndex = Math.max(0, total - 10);
        for (int i = 0; i < startIndex; i++) {
            allPoints.get(i).color = 0xFF352A87;
        }
        int lastCount = Math.min(10, total);
        int offset = 10 - lastCount;
        for (int i = 0; i < lastCount; i++) {
            int colorIndex = offset + i;
            allPoints.get(startIndex + i).color = PARULA_COLORS[colorIndex];
        }
        for (Map.Entry<CustomImageLayout, ImageModel> entry : layoutToImageMap.entrySet()) {
            applyPointColorsToLayout(entry.getValue(), entry.getKey());
        }
    }

    private void applyPointColorsToLayout(ImageModel imageModel, CustomImageLayout layout) {
        int childCount = layout.getChildCount();
        int pointIndex = 1;
        for (PointModel p : imageModel.points) {
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
        view.setBackground(gd);
    }

    private void removeImage(CustomImageLayout layout) {
        ImageModel imageModel = layoutToImageMap.get(layout);
        if (imageModel == null) return;
        dataModel.images.remove(imageModel);
        View containerLayout = (View) layout.getParent();
        if (containerLayout != null && containerLayout.getParent() instanceof ViewGroup) {
            ((ViewGroup) containerLayout.getParent()).removeView(containerLayout);
        }
        layoutToImageMap.remove(layout);
        DataStorage.saveData(this, dataModel);
        updateAllPointsColors();
        Toast.makeText(this, "Bild gelöscht.", Toast.LENGTH_SHORT).show();
    }

    private int exifToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) return 90;
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) return 180;
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        return 0;
    }

    private void startPulseAnimation(View pointView) {
        if (pulseAnimator != null && pulseAnimator.isRunning()) {
            pulseAnimator.cancel();
        }

        pulseAnimator = ValueAnimator.ofFloat(1f, 1.5f, 1f);
        pulseAnimator.setDuration(1000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            pointView.setScaleX(scale);
            pointView.setScaleY(scale);
        });

        pulseAnimator.start();
    }

    private void stopPulseAnimation(View pointView) {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pointView.setScaleX(1f);
            pointView.setScaleY(1f);
        }
    }

}
