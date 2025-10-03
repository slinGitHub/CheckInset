package com.example.checkinset;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final String VERSION_URL = "https://raw.githubusercontent.com/slinGitHub/CheckInset/master/latest.json";

    private static final String PREFS_NAME = "update_checker_prefs";
    private static final String PREF_LAST_CHECK = "last_check_time";
    private static final long THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000; // 30 days

    private final Context context;
    private final String currentVersion;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public UpdateChecker(Context context, String currentVersion) {
        this.context = context;
        this.currentVersion = currentVersion;
    }

    /**
     * @param manualCheck true if started manually from settings, false if automatic (e.g. app start)
     */
    public void checkForUpdate(boolean manualCheck) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(PREF_LAST_CHECK, 0);
        long now = System.currentTimeMillis();

        if (!manualCheck && (now - lastCheck < THIRTY_DAYS_MILLIS)) {
            Log.d(TAG, "Skipping update check (last check < 30 days ago).");
            return;
        }

        executor.execute(() -> {
            try {
                URL url = new URL(VERSION_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();

                String jsonString = result.toString();
                Log.d(TAG, "Server response: " + jsonString);

                JSONObject json = new JSONObject(jsonString);
                String latestVersion = json.getString("latestVersion");
                String downloadUrl = json.getString("downloadUrl");

                handler.post(() -> {
                    if (!currentVersion.equals(latestVersion)) {
                        showUpdateDialog(latestVersion, downloadUrl);
                    } else if (manualCheck) {
                        // Only show "up to date" if user triggered the check
                        Toast.makeText(context, "App is up to date", Toast.LENGTH_SHORT).show();
                    }

                    // Save last check time (always after check)
                    prefs.edit().putLong(PREF_LAST_CHECK, now).apply();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error while checking for updates", e);
                handler.post(() -> {
                    if (manualCheck) {
                        Toast.makeText(context, "Update check failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showUpdateDialog(String latestVersion, String downloadUrl) {
        new AlertDialog.Builder(context)
                .setTitle("Update available")
                .setMessage("A new version (" + latestVersion + ") of the app is available.")
                .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                        context.startActivity(browserIntent);
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }
}
