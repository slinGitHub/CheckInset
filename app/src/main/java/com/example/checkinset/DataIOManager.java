package com.example.checkinset;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.example.checkinset.model.DataModel;
import com.example.checkinset.model.ImageModel;
import com.example.checkinset.model.PointModel;
import com.example.checkinset.utils.DataStorage;

public class DataIOManager {
    private Context context;
    private DataModel dataModel;
    private DataStorage dataStorage;
    private MainActivity mainActivity;

    public DataIOManager(Context context, DataModel dataModel, DataStorage dataStorage, MainActivity mainActivity) {
        this.context = context;
        this.dataModel = dataModel;
        this.dataStorage = dataStorage;
        this.mainActivity = mainActivity;
    }

    public void exportData(ActivityResultLauncher<String> saveFileLauncher) {
        dataModel = dataStorage.loadData(context);
        if (dataModel == null || dataModel.images.isEmpty()) {
            Toast.makeText(context, "Keine Daten zum Exportieren vorhanden.", Toast.LENGTH_SHORT).show();
            return;
        }
        saveFileLauncher.launch("export.zip");
    }

    public void exportDataToZip(Uri zipUri) throws IOException {
        try (OutputStream os = context.getContentResolver().openOutputStream(zipUri);
             ZipOutputStream zos = new ZipOutputStream(os)) {

            // JSON-Daten erstellen
            JSONArray jsonArray = new JSONArray();
            for (ImageModel imgModel : dataModel.images) {
                JSONObject imgJson = new JSONObject();
                imgJson.put("title", imgModel.title);
                imgJson.put("imageName", new File(imgModel.originalImagePath).getName()); // Dateiname des Bildes
                imgJson.put("cartoonImageName", new File(imgModel.cartoonImagePath).getName());
                JSONArray pointsJson = new JSONArray();
                for (PointModel point : imgModel.points) {
                    JSONObject pointJson = new JSONObject();
                    pointJson.put("xPercent", point.xPercent);
                    pointJson.put("yPercent", point.yPercent);
                    pointJson.put("mark", point.mark);
                    pointJson.put("timestamp", point.timestamp);
                    pointsJson.put(pointJson);
                }
                imgJson.put("points", pointsJson);
                jsonArray.put(imgJson);
            }

            // JSON-Daten in ZIP schreiben
            String jsonData = jsonArray.toString(2);
            ZipEntry jsonEntry = new ZipEntry("data.json");
            zos.putNextEntry(jsonEntry);
            zos.write(jsonData.getBytes());
            zos.closeEntry();

            for (ImageModel imgModel : dataModel.images) {
                // Originalbild
                Bitmap originalBitmap = BitmapFactory.decodeFile(imgModel.originalImagePath);
                if (originalBitmap != null) {
                    String originalName = new File(imgModel.originalImagePath).getName();
                    ZipEntry originalEntry = new ZipEntry("images/" + originalName);
                    zos.putNextEntry(originalEntry);
                    originalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, zos);
                    zos.closeEntry();
                }

                // Cartoonbild
                Bitmap cartoonBitmap = BitmapFactory.decodeFile(imgModel.cartoonImagePath);
                if (cartoonBitmap != null) {
                    String cartoonName = new File(imgModel.cartoonImagePath).getName();
                    ZipEntry cartoonEntry = new ZipEntry("images/" + cartoonName);
                    zos.putNextEntry(cartoonEntry);
                    cartoonBitmap.compress(Bitmap.CompressFormat.JPEG, 100, zos);
                    zos.closeEntry();
                }
            }
            Toast.makeText(context, "Daten erfolgreich exportiert.", Toast.LENGTH_SHORT).show();

        } catch (JSONException e) {
            Toast.makeText(context, "Fehler beim Exportieren der Daten: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    public void importDataFromZip(Uri zipUri) throws IOException, JSONException {
        dataModel = new DataModel();

        try (InputStream is = context.getContentResolver().openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            Map<String, File> extractedImages = new HashMap<>();
            JSONArray jsonArray = null;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                if (entryName.endsWith("data.json")) {
                    // JSON-Daten lesen
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    jsonArray = new JSONArray(sb.toString());

                } else if (entryName.startsWith("images/")) {
                    String imageName = entryName.substring("images/".length());

                    // Schutz: Kein leerer Dateiname
                    if (imageName == null || imageName.trim().isEmpty()) {
                        continue;
                    }

                    File imageFile = new File(context.getExternalFilesDir(null), imageName);

                    // Schutz: Pfad darf kein Verzeichnis sein
                    if (imageFile.isDirectory()) {
                        continue;
                    }

                    try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }

                    extractedImages.put(imageName, imageFile);
                }

                zis.closeEntry();
            }

            if (jsonArray == null) {
                throw new JSONException("data.json nicht gefunden!");
            }

            // JSON verarbeiten
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject imgJson = jsonArray.getJSONObject(i);
                ImageModel imgModel = new ImageModel();

                imgModel.title = imgJson.getString("title");

                String originalName = imgJson.getString("imageName");
                String cartoonName = imgJson.getString("cartoonImageName");

                File originalFile = extractedImages.get(originalName);
                File cartoonFile = extractedImages.get(cartoonName);

                imgModel.originalImagePath = (originalFile != null) ? originalFile.getAbsolutePath() :
                        new File(context.getExternalFilesDir(null), originalName).getAbsolutePath();
                imgModel.cartoonImagePath = (cartoonFile != null) ? cartoonFile.getAbsolutePath() :
                        new File(context.getExternalFilesDir(null), cartoonName).getAbsolutePath();

                JSONArray pointsJson = imgJson.getJSONArray("points");
                for (int j = 0; j < pointsJson.length(); j++) {
                    JSONObject pointJson = pointsJson.getJSONObject(j);
                    PointModel pointModel = new PointModel();
                    pointModel.xPercent = (float) pointJson.getDouble("xPercent");
                    pointModel.yPercent = (float) pointJson.getDouble("yPercent");
                    pointModel.mark = pointJson.optInt("mark", 0);
                    pointModel.timestamp = pointJson.getString("timestamp");
                    imgModel.points.add(pointModel);
                }

                dataModel.images.add(imgModel);
            }

            dataStorage.saveData(context, dataModel);
            mainActivity.loadUIFromDataModel();

            mainActivity.updateDataModel(dataModel);

            Toast.makeText(context, "Daten erfolgreich importiert.", Toast.LENGTH_SHORT).show();

        } catch (IOException | JSONException e) {
            Toast.makeText(context, "Fehler beim Importieren: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

}
