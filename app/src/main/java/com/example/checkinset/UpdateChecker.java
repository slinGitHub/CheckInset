package com.example.checkinset;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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

    private final Context context;
    private final String currentVersion;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public UpdateChecker(Context context, String currentVersion) {
        this.context = context;
        this.currentVersion = currentVersion;
    }

    public void checkForUpdate() {
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
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_up_to_date), Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error while checking for updates", e);
                handler.post(() ->
                        Toast.makeText(context, context.getString(R.string.toast_update_failed), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showUpdateDialog(String latestVersion, String downloadUrl) {
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.update_available_title))
                .setMessage(context.getString(R.string.update_available_message, latestVersion))
                .setPositiveButton(context.getString(R.string.update_button_download), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                        context.startActivity(browserIntent);
                    }
                })
                .setNegativeButton(context.getString(R.string.update_button_later), null)
                .show();
    }
}
