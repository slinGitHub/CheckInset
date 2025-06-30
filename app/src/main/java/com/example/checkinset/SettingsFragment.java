
package com.example.checkinset;

import android.os.Bundle;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // Hide label on day
        EditTextPreference labelOffPreference = findPreference("days_for_label_off");
        if (labelOffPreference != null) {
            labelOffPreference.setSummaryProvider(preference -> {
                String value = labelOffPreference.getText();
                return (value != null && !value.isEmpty()) ? "Current value: " + value + " days" : "No value set";
            });
        }

        // Colored until day
        EditTextPreference fadedLabelOffPreference = findPreference("days_for_faded_label_off");
        if (fadedLabelOffPreference != null) {
            fadedLabelOffPreference.setSummaryProvider(preference -> {
                String value = fadedLabelOffPreference.getText();
                return (value != null && !value.isEmpty()) ? "Current value: " + value + " days" : "No value set";
            });
        }
    }
}