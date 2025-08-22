package com.example.checkinset;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.example.checkinset.model.DataModel;
import com.example.checkinset.model.ImageModel;
import com.example.checkinset.model.PointModel;
import com.example.checkinset.utils.DataStorage;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import android.animation.ValueAnimator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Color;

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

    // Magma-Farben
    private static final int[] MAGMA_COLORS = {
            0xFF000004, 0xFF1B0C41, 0xFF4A0C6B, 0xFF781C6D,
            0xFFA6365D, 0xFFF1605D, 0xFFFCA85E, 0xFFFFE99F,
            0xFFFFFCBF, 0xFFFFFDD9
    };

    // Viridis Color Map Customized : Green to purple (13 Entrys)
    private static final int[] VIRIDIS_COLORS = {
            0xFFB6DE2B, // Grün (0% Transparenz)
            0xF96CCE59, // 5% Transparenz
            0xE91F9D8A, // 10% Transparenz
            0xD826838F, // 15% Transparenz
            0xCC31688E, // 20% Transparenz
            0xBF3E4A89, // 25% Transparenz
            0xB2482878, // 30% Transparenz
            0xA0440154  // Lila (37.5% Transparenz)
    };

    private static final int[] New_Colors = {
            0xFF0E8393, // Blau (0% Transparenz
            0xA0440154  // Lila (37.5% Transparenz)
    };

    private static final int[] Special_Colors = {
            0x4075D054,
            0x00FDBE3D
    };

    public static int[] generateColormap(int steps, int[] baseColors) {
        if (steps <= 1) {
            throw new IllegalArgumentException("Die Anzahl der Schritte muss größer als 1 sein.");
        }

        int[] colormap = new int[steps];
        float stepSize = (float) (baseColors.length - 1) / (steps - 1);

        for (int i = 0; i < steps; i++) {
            float position = i * stepSize;
            int lowerIndex = (int) Math.floor(position);
            int upperIndex = Math.min(lowerIndex + 1, baseColors.length - 1);
            float fraction = position - lowerIndex;

            colormap[i] = interpolateColor(baseColors[lowerIndex], baseColors[upperIndex], fraction);
        }

        return colormap;
    }

    /**
     * Interpoliert zwischen zwei Farben.
     *
     * @param color1 Erste Farbe (ARGB).
     * @param color2 Zweite Farbe (ARGB).
     * @param fraction Interpolationsfaktor (0.0 bis 1.0).
     * @return Die interpolierte Farbe (ARGB).
     */
    private static int interpolateColor(int color1, int color2, float fraction) {
        int a1 = Color.alpha(color1);
        int r1 = Color.red(color1);
        int g1 = Color.green(color1);
        int b1 = Color.blue(color1);

        int a2 = Color.alpha(color2);
        int r2 = Color.red(color2);
        int g2 = Color.green(color2);
        int b2 = Color.blue(color2);

        int a = (int) (a1 + fraction * (a2 - a1));
        int r = (int) (r1 + fraction * (r2 - r1));
        int g = (int) (g1 + fraction * (g2 - g1));
        int b = (int) (b1 + fraction * (b2 - b1));

        return Color.argb(a, r, g, b);
    }

    // Globales Datenmodell, geladen/gespeichert via JSON
    private DataModel dataModel;
    private DataStorage dataStorage;

    private ActivityResultLauncher<String> saveFileLauncher;
    private ActivityResultLauncher<String> openFileLauncher;

    // Map: Ordnet jedem CustomImageLayout das zugehörige ImageModel zu
    private final Map<CustomImageLayout, ImageModel> layoutToImageMap = new HashMap<>();

    private ImageManager imageManager;
    private String currentImageTitle;

    // Bottom Sheet Komponenten
    private LinearLayout bottomSheet;
    private TextView tvTimestamp, tvCoordinateX, tvCoordinateY, tvMark;
    private Button btnDeletePoint;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;

    // Global gespeicherter aktuell ausgewählter Punkt und zugehöriges Layout
    private PointModel currentPoint;
    private CustomImageLayout currentLayout;

    private ValueAnimator pulseAnimator;

    private DataIOManager dataIOManager;

    private boolean protectedViewOn = true; // default: an
    private boolean historyViewOn = false;
    private MenuItem toggleItem;

    private WavePulseAnimator waveAnimator;

    private static final String PREFS_NAME = "donation_prefs";
    private static final String KEY_LAST_SHOWN = "donation_last_shown";

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
        bottomSheet.setBackground(ContextCompat.getDrawable(this, R.drawable.bottom_sheet_background));

        tvTimestamp = findViewById(R.id.tvTimestamp);
        tvCoordinateX = findViewById(R.id.tvCoordinateX);
        tvCoordinateY = findViewById(R.id.tvCoordinateY);
        tvMark = findViewById(R.id.tvMark);
        btnDeletePoint = findViewById(R.id.btnDeletePoint);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        bottomSheet.post(() -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN));
        bottomSheetBehavior.setPeekHeight(bottomSheet.getHeight());


        //BottomSheet Date/Clock
        tvTimestamp.setOnClickListener(v -> {
            // Versuche das vorhandene Datum/Uhrzeit zu parsen, ansonsten aktuelles Datum/Uhrzeit verwenden
            Calendar calendar = Calendar.getInstance();
            try {
                // Erwartetes Format: "yyyy-MM-dd HH:mm"
                String text = tvTimestamp.getText().toString().trim();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                Date date = sdf.parse(text);
                calendar.setTime(date);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Öffne den DatePickerDialog
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    MainActivity.this,
                    (datePicker, year, month, dayOfMonth) -> {
                        // Datum wurde ausgewählt, jetzt den Kalender aktualisieren
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        // Öffne anschließend den TimePickerDialog
                        TimePickerDialog timePickerDialog = new TimePickerDialog(
                                MainActivity.this,
                                (timePicker, hourOfDay, minute) -> {
                                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                    calendar.set(Calendar.MINUTE, minute);
                                    // Neues Datum und Uhrzeit kombinieren und formatieren
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                                    String newDateTime = sdf.format(calendar.getTime());
                                    tvTimestamp.setText(newDateTime);
                                    // Aktualisiere das Datum im aktuellen PointModel
                                    if (currentPoint != null) {
                                        currentPoint.timestamp = newDateTime;
                                        refreshAllPoints();
                                        DataStorage.saveData(MainActivity.this, dataModel);
                                    }
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                true // 24-Stunden-Format
                        );
                        timePickerDialog.show();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();

        });

        // Für X-Koordinate
        //TextView tvCoordinateX = findViewById(R.id.tvCoordinateX);
        tvCoordinateX.setOnClickListener(v -> {
            // Aktuellen X-Wert holen (hier als float, z.B. aus dem aktuellen PointModel)
            float currentX = currentPoint != null ? currentPoint.xPercent * currentLayout.getWidth() : 0f;
            showCoordinateEditDialog("X", currentX, newValue -> {
                // Aktualisiere den aktuellen PointModel-Wert (hier als Prozentwert)
                if (currentPoint != null && currentLayout != null) {
                    currentPoint.xPercent = newValue / (float) currentLayout.getWidth();
                    // Aktualisiere die Anzeige
                    tvCoordinateX.setText(String.format(Locale.getDefault(), "%.2f", newValue));
                    loadUIFromDataModel();
                    DataStorage.saveData(MainActivity.this, dataModel);
                }
            });
        });

        // Für Y-Koordinate
        //TextView tvCoordinateY = findViewById(R.id.tvCoordinateY);
        tvCoordinateY.setOnClickListener(v -> {
            float currentY = currentPoint != null ? currentPoint.yPercent * currentLayout.getHeight() : 0f;
            showCoordinateEditDialog("Y", currentY, newValue -> {
                if (currentPoint != null && currentLayout != null) {
                    currentPoint.yPercent = newValue / (float) currentLayout.getHeight();
                    tvCoordinateY.setText(String.format(Locale.getDefault(), "%.2f", newValue));
                    loadUIFromDataModel();
                    DataStorage.saveData(MainActivity.this, dataModel);
                }
            });
        });

        tvMark.setOnClickListener(v -> {
            float currentMark = currentPoint != null ? currentPoint.mark : 0f;
            showCoordinateEditDialog("Mark", currentMark, newValue -> {
                if (currentPoint != null) {
                    int markValue = (int) newValue;
                    if (markValue >= 0 && markValue <= 3) {
                        currentPoint.mark = markValue;
                        tvMark.setText(String.format(Locale.getDefault(), "%d", markValue));
                        loadUIFromDataModel();
                        DataStorage.saveData(MainActivity.this, dataModel);
                    } else {
                        Toast.makeText(MainActivity.this, "Only 0,1,2,3 allowed.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });

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
                    Toast.makeText(MainActivity.this, "Point deleted.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        addPointButton.setOnClickListener(v -> {
            isAddingPoint = true;
            addPointButton.setEnabled(false);
            Toast.makeText(MainActivity.this, "Tap on a picture to set a point.", Toast.LENGTH_SHORT).show();
        });

        // Datenmodell laden
        dataModel = DataStorage.loadData(this);

        // UI aus dem Datenmodell aufbauen
        loadUIFromDataModel();

        // ImageManager initialisieren
        imageManager = new ImageManager(this, this, this);

        dataIOManager = new DataIOManager(this, dataModel, dataStorage, this);

        saveFileLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"),
                uri -> {
                    if (uri != null) {
                        try {
                            dataIOManager.exportDataToZip(uri);
                        } catch (IOException e) {
                            Toast.makeText(this, "Error when exporting the data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    } else {
                        Toast.makeText(this, "Export canceled.", Toast.LENGTH_SHORT).show();
                    }
                });
        openFileLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        try {
                            dataIOManager.importDataFromZip(uri);
                        } catch (IOException | JSONException e) {
                            Toast.makeText(this, "Fehler beim Importieren der Daten: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    } else {
                        Toast.makeText(this, "Import abgebrochen.", Toast.LENGTH_SHORT).show();
                    }
                });

        //Add image of white owl to toolbar
        getSupportActionBar().setIcon(R.drawable.ic_action_checkinsetbarowl);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.DarkColor1)); // Deine lila Farbe
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(0); // weiße Icons
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(Color.BLACK);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        getMenuInflater().inflate(R.menu.top_app_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }else if (id == R.id.action_capture_image) {
            // Vor der Kameraaufnahme evtl. Titel abfragen
            showTitleInputDialog(true);
            return true;
        } else if (id == R.id.action_pick_image) {
            showTitleInputDialog(false);
            return true;
        } else if (id == R.id.action_delete_image) {
            isDeletingImage = true;
            Toast.makeText(this, "Now tap on the picture you want to delete. (Incl. points)", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_export_data) {
            dataIOManager.exportData(saveFileLauncher);
            return true;
        } else if (id == R.id.action_import_data) {
            importData();
            return true;
        } else if (item.getItemId() == R.id.action_toggle_history) {
            historyViewOn = !historyViewOn; //Toggle protected view
            item.setIcon(historyViewOn ? R.drawable.outline_history_24 : R.drawable.outline_history_toggle_off_24);
            loadUIFromDataModel();
            return true;
        } else if (item.getItemId() == R.id.action_toggle_protected_images) {
            protectedViewOn = !protectedViewOn; //Toggle protected view
            item.setIcon(protectedViewOn ? R.drawable.ic_wappen_on : R.drawable.ic_wappen_off);
            loadUIFromDataModel();
            return true;
        } else if (item.getItemId() == R.id.action_aboutCheckInset) {
            Intent intent_settings_about = new Intent(this, SettingsAboutActivity.class);
            startActivity(intent_settings_about);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }


    private void importData() {
        openFileLauncher.launch("*/*");
    }

    private interface CoordinateUpdateListener {
        void onCoordinateUpdated(float newValue);
    }

    private void showCoordinateEditDialog(String label, float currentValue, CoordinateUpdateListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(label + " Koordinate bearbeiten");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.getDefault(), "%.2f", currentValue));
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                float newValue = Float.parseFloat(input.getText().toString());
                listener.onCoordinateUpdated(newValue);
            } catch (NumberFormatException e) {
                Toast.makeText(MainActivity.this, "Invalid value", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Abort", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showTitleInputDialog(boolean fromCamera) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter heading");

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
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Baut die komplette UI aus dem Datenmodell auf.
     */
    public void loadUIFromDataModel() {
        imageContainer.removeAllViews();
        layoutToImageMap.clear();

        for (ImageModel img : dataModel.images) {
            addImageToUI(img);
        }
    }

    public void updateDataModel(DataModel newModel) {
        this.dataModel = newModel;
        loadUIFromDataModel();
    }

    /**
     * Zeigt ein Bild samt Überschrift und Punkten.
     */
    private void addImageToUI(ImageModel imageModel) {
        final long[] lastTapTime = {0}; // Array, um eine mutable Variable zu haben
        String imagePath = protectedViewOn ? imageModel.cartoonImagePath : imageModel.originalImagePath ;
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap != null) {
            try {
                ExifInterface exif = new ExifInterface(imagePath);
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
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastTapTime[0] < 300) { // 300 ms als Schwellwert
                    // Doppelklick erkannt: Ermittle den am nächsten liegenden Punkt
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
                }
                lastTapTime[0] = currentTime;
                return true;
            }
            return false;
        });

        TextView titleView = new TextView(this);
        titleView.setText(imageModel.title);
        titleView.setTextSize(18);
        titleView.setTextColor(Color.WHITE); // Weißer Text
        titleView.setBackgroundColor(Color.argb(128, 0, 0, 0)); // 50% transparentes Schwarz
        titleView.setPadding(8, 8, 8, 8); // Padding für besseren Abstand

        // Layout-Parameter für die Positionierung der Überschrift
        FrameLayout.LayoutParams titleLayoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        titleLayoutParams.gravity = Gravity.TOP | Gravity.START; // Links oben positionieren
        titleView.setLayoutParams(titleLayoutParams);

        // Überschrift direkt zum CustomImageLayout hinzufügen
        customLayout.addView(titleView);

        // CustomImageLayout in den Container einfügen
        LinearLayout containerLayout = new LinearLayout(this);
        containerLayout.setOrientation(LinearLayout.VERTICAL);
        containerLayout.setPadding(0, 16, 0, 16);
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
                refreshAllPoints();
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
    public void onImageCaptured(String cartoonImagePath, String originalImagePath, String imageTitle) {
        ImageModel newImg = new ImageModel();
        newImg.originalImagePath = originalImagePath;
        newImg.cartoonImagePath = cartoonImagePath;
        newImg.title = imageTitle;
        dataModel.images.add(newImg);
        addImageToUI(newImg);
        DataStorage.saveData(this, dataModel);
    }

//    @Override
//    public void onImagePicked(String imagePath) {
//        ImageModel newImg = new ImageModel();
//        newImg.originalImagePath = imagePath;
//        newImg.cartoonImagePath = cartoonPath;
//        newImg.title = (currentImageTitle != null && !currentImageTitle.isEmpty())
//                ? currentImageTitle
//                : "Gallery image";
//        dataModel.images.add(newImg);
//        addImageToUI(newImg);
//        DataStorage.saveData(this, dataModel);
//    }

    @Override
    public void onError(String errorMessage) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAllPoints();

        // Wähle ein View als Parent, z. B. die Root-Layout ID
        View coordinator = findViewById(R.id.coordinatorLayout);
        showCoffeeDonationSnackbar(coordinator);
    }

    @Override
    public void onBackPressed() {
        int state = bottomSheetBehavior.getState();
        if (state == BottomSheetBehavior.STATE_EXPANDED
                || state == BottomSheetBehavior.STATE_HALF_EXPANDED)  {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            refreshAllPoints();
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
        refreshAllPoints();
        DataStorage.saveData(this, dataModel);
    }

    private void addPointView(CustomImageLayout layout, float xPercent, float yPercent, int color, PointModel point) {
        int size = (int) (16 * getResources().getDisplayMetrics().density);
        int actualX = (int) (layout.getWidth() * xPercent) - size / 2;
        int actualY = (int) (layout.getHeight() * yPercent) - size / 2;

        // 1) Container anlegen und mit unserem PointModel taggen
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setTag(point);
        FrameLayout.LayoutParams wrapperLp = new FrameLayout.LayoutParams(size, size);
        wrapperLp.leftMargin = actualX;
        wrapperLp.topMargin  = actualY;
        layout.addView(wrapper, wrapperLp);

        // 2) Kreis‑View
        View circle = new View(this);
        circle.setId(R.id.point_circle);
        setCircleBackground(circle, color);
        wrapper.addView(circle, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 3) Label‑View
        TextView overlay = new TextView(this);
        overlay.setId(R.id.point_label);
        overlay.setGravity(Gravity.CENTER);
        overlay.setTypeface(Typeface.DEFAULT_BOLD);
        overlay.setTextSize(10);
        overlay.setText(String.valueOf(getDaysDifference(point.timestamp)));
        wrapper.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }


    private long getDaysDifference(String timestamp) {
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date pointDate = df.parse(timestamp);
            long diffMillis = new Date().getTime() - pointDate.getTime();
            return diffMillis / (1000L * 60 * 60 * 24); // Ganzzahlige Tage
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
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
        int originalPointColor = point.color;

        // Zuerst alle Punkte auf den Standardradius zurücksetzen
        refreshAllPoints();

        if (bottomSheetBehavior == null) {
            Log.e("BottomSheet", "❌ Error: BottomSheetBehavior ist null!");
            return;
        }

        // Setze Text für den ausgewählten Punkt
        tvTimestamp.setText(point.timestamp);
        int absX = (int) (layout.getWidth() * point.xPercent);
        int absY = (int) (layout.getHeight() * point.yPercent);
        tvCoordinateX.setText(String.valueOf(absX));
        tvCoordinateY.setText(String.valueOf(absY));
        tvMark.setText(String.valueOf(point.mark)); // Mark-Wert anzeigen

        // Bottom Sheet anzeigen
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        // Neuer aktueller Punkt
        currentPoint = point;
        currentLayout = layout;

        // Punkt-View finden und Kreis weiß einfärben:
        View pointView = getPointView(layout, point);
        if (pointView != null) {
            View circle = pointView.findViewById(R.id.point_circle);
            // import android.graphics.Color;
            setCircleBackground(circle, Color.WHITE);

            //Text im Kreis schwarz einfärben
            TextView overlay = pointView.findViewById(R.id.point_label);
            overlay.setTextColor(Color.BLACK);
        }

        if (waveAnimator != null) {
            waveAnimator.stop();
        }

        int sizePx = (int) (25 * getResources().getDisplayMetrics().density);
        int actualX = (int) (layout.getWidth() * point.xPercent) - sizePx / 2;
        int actualY = (int) (layout.getHeight() * point.yPercent) - sizePx / 2;

        // Wellen-Animator initialisieren und starten
        waveAnimator = new WavePulseAnimator(
                (ViewGroup) layout, // oder das passende Container-View
                actualX,
                actualY,
                sizePx,
                Color.WHITE,
                0.5f,
                3f
        );
        waveAnimator.start();
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

    private void refreshAllPoints() {

        int labelFadedDays = SettingsManager.getlabelFadedDaysOff(this);; // Ab wieviel Tagen soll das Label ausgeblendet werden?
        int labelOffDays = SettingsManager.getDaysForLabelOff(this);
        int maxPoints = SettingsManager.getMaxPointsHistory(this);

        //Wave stoppen
        if (waveAnimator != null) {
            waveAnimator.stop();
        }

        List<PointModel> all = dataModel.images.stream()
                .flatMap(im -> im.points.stream())
                .sorted(Comparator.comparing(p -> p.timestamp))
                .collect(Collectors.toList());

        for (PointModel point : all) {
            long daysDifference = getDaysDifference(point.timestamp);
            //int colorIndex = (int) Math.min(daysDifference, CUSTOM_COLORMAP.length - 1); // Begrenze den Index auf die Colormap-Länge
            point.color = 0xFF602D58;
        }

        // Über alle Layouts gehen
        for (Map.Entry<CustomImageLayout, ImageModel> e : layoutToImageMap.entrySet()) {
            CustomImageLayout layout = e.getKey();
            for (PointModel p : e.getValue().points) {
                // Container finden
                View wrapper = (View) layout.findViewWithTag(p);
                if (wrapper == null) continue;

                // Kreis updaten
                View circle = wrapper.findViewById(R.id.point_circle);
                setCircleBackground(circle, p.color);

                // Label updaten
                TextView label = wrapper.findViewById(R.id.point_label);
                long daysDifference = getDaysDifference(p.timestamp);

                if (historyViewOn) {
                    // ✅ Alle Punkte behalten, keine Zahl anzeigen
                    label.setText("");
                    label.setTextColor(Color.TRANSPARENT); // optional, sonst leer

                    // Kreis
                    int baseSize = (int) (16 * getResources().getDisplayMetrics().density);
                    int newSize = (int) (baseSize * 0.5f);

                    // Farbe
                    // 100% — FF / 75% — BF / 50% — 7F / 25% — 3F
                    int alpha = 0xBF << 24; // 50% Alpha = 0x80
                    int fadedColor =  alpha | 0x00CCCCCC; // 0x40 = ~25% Alph

                    if (p.mark == 1) {
                        fadedColor = alpha | 0x00009A00; // 0x40 = ~25% Alpha
                    } else if (p.mark == 2) {
                        fadedColor = alpha | 0x00F79E1B; // 0x40 = ~25% Alpha
                    } else if (p.mark == 3) {
                        fadedColor = alpha | 0x00FF5F00; // 0x40 = ~25% Alpha
                    }

                    setCircleBackground(circle, fadedColor);

                    // Kreisgröße setzen
                    ViewGroup.LayoutParams params = circle.getLayoutParams();
                    params.width = newSize;
                    params.height = newSize;
                    circle.setLayoutParams(params);

                    // Wrapper-Layout neu positionieren, damit der Kreis mittig bleibt
                    FrameLayout.LayoutParams wrapperLp = (FrameLayout.LayoutParams) wrapper.getLayoutParams();
                    int actualX = (int) (layout.getWidth() * p.xPercent) - newSize / 2;
                    int actualY = (int) (layout.getHeight() * p.yPercent) - newSize / 2;
                    wrapperLp.width = newSize;
                    wrapperLp.height = newSize;
                    wrapperLp.leftMargin = actualX;
                    wrapperLp.topMargin = actualY;
                    wrapper.setLayoutParams(wrapperLp);

                } else {

                    // Label anzeigen
                    if (daysDifference <= labelOffDays) {
                        label.setText(String.valueOf(daysDifference));
                        label.setTextColor(Color.WHITE);


                    } else {

                        // Label anpassen
                        if (daysDifference <= labelFadedDays) {
                            label.setText(""); // Kein Label anzeigen
                            //int fadedColor = 0xCC602D58; // ARGB: 80% Alpha, Lila
                            int fadedColor = 0xCCCCCCCC; // ARGB: 80% Alpha, hellgrau
                            setCircleBackground(circle, fadedColor);

                            int baseSize = (int) (16 * getResources().getDisplayMetrics().density);

                            // Linearer Skalierungsfaktor von 1.0 (100%) bis 0.25 (25%)
                            float t = (float) (daysDifference - labelOffDays) / (labelFadedDays - labelOffDays);
                            t = Math.max(0f, Math.min(1f, t)); // Clamp zwischen 0 und 1
                            float scale = 1.0f - t * 0.75f; // 1.0 -> 0.25

                            int newSize = (int) (baseSize * scale);

                            // Kreisgröße setzen
                            ViewGroup.LayoutParams params = circle.getLayoutParams();
                            params.width = newSize;
                            params.height = newSize;
                            circle.setLayoutParams(params);

                            // Wrapper-Layout neu positionieren, damit der Kreis mittig bleibt
                            FrameLayout.LayoutParams wrapperLp = (FrameLayout.LayoutParams) wrapper.getLayoutParams();
                            int actualX = (int) (layout.getWidth() * p.xPercent) - newSize / 2;
                            int actualY = (int) (layout.getHeight() * p.yPercent) - newSize / 2;
                            wrapperLp.width = newSize;
                            wrapperLp.height = newSize;
                            wrapperLp.leftMargin = actualX;
                            wrapperLp.topMargin = actualY;
                            wrapper.setLayoutParams(wrapperLp);

                        } else {
                            // Punkt aus dem Layout entfernen
                            layout.removeView(wrapper);
                            continue;

                        }
                    }
                }

            }
        }
    }

    private void setCircleBackground(View view, int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        int alpha = Color.alpha(color);
        gd.setAlpha(alpha);
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
        refreshAllPoints();
        Toast.makeText(this, "Image deleted.", Toast.LENGTH_SHORT).show();
    }

    private int exifToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) return 90;
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) return 180;
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        return 0;
    }

    private void showCoffeeDonationSnackbar(View parentView) {
        if (!shouldShowDonationSnackbar()) return;

        String message = "Enjoying the app? Donate me a coffee to support its growth! ☕";

        // Snackbar erstellen (LENGTH_INDEFINITE für dauerhaft)
        Snackbar snackbar = Snackbar.make(parentView, message, Snackbar.LENGTH_INDEFINITE);

        setLastShownSnackbar(); // optional speichern, dass Snackbar gezeigt wurde

        // Support-Button hinzufügen
        snackbar.setAction("DONATE 5$", v -> {
            String url = "https://www.paypal.com/donate?hosted_button_id=CF3AHXTKNARRL"; // hier deinen Link einfügen
            parentView.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            snackbar.dismiss();
        });

        snackbar.setActionTextColor(Color.parseColor("#2A9D8F")); // grün
        snackbar.show();
    }

    // Prüfen, ob die Erinnerung angezeigt werden soll
    private boolean shouldShowDonationSnackbar() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long lastShown = prefs.getLong(KEY_LAST_SHOWN, 0);
        long now = System.currentTimeMillis();
        long threeMonthsMillis = 1000L * 60; // * 60 * 24 * 30 * 3; // ca. 3 Monate
        return now - lastShown > threeMonthsMillis;
    }

    // Zeitstempel speichern
    private void setLastShownSnackbar() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_SHOWN, System.currentTimeMillis()).apply();
    }


}
