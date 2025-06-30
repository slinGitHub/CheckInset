package com.example.checkinset;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class SettingsManager {

    public static int getDaysForLabelOff(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(prefs.getString("days_for_label_off", "30"));
    }

    public static int getlabelFadedDaysOff(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(prefs.getString("days_for_faded_label_off", "60"));
    }

}
