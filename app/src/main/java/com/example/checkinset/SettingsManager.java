package com.example.checkinset;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class SettingsManager {

    public static int getDaysForLabelOff(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(prefs.getString("days_for_label_off", "60"));
    }

    public static int getDaysForViridisColors(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(prefs.getString("days_for_viridis_colors", "30"));
    }
}
