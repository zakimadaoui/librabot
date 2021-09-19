package com.example.pidbbotcontroller;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    public static final String REF_ANGLE_PREF = "referance_pref";
    public static final String DEAD_GAP_PREF = "dead_gap_pref";
    public static final String VMAX_PREF = "vmax_pref";
    public static final String IMAX_PREF = "imax_pref";
    public static final String DELAY_PREF = "delay_pref";
    public static final String KP_PREF = "kp_pref";
    public static final String KI_PREF = "ki_pref";
    public static final String KD_PREF = "kd_pref";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        findViewById(R.id.back_button).setOnClickListener(view -> finish());

    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference.OnPreferenceChangeListener listener = (preference, newValue) -> {
                preference.setSummary(String.valueOf(newValue));
                return true;
            };

            EditTextPreference referencePref = findPreference(REF_ANGLE_PREF);
            Objects.requireNonNull(referencePref).setSummary(referencePref.getText());
            referencePref.setOnPreferenceChangeListener(listener);


            EditTextPreference deadGapPref = findPreference(DEAD_GAP_PREF);
            Objects.requireNonNull(deadGapPref).setSummary(deadGapPref.getText());
            deadGapPref.setOnPreferenceChangeListener(listener);


            EditTextPreference vmaxPref = findPreference(VMAX_PREF);
            Objects.requireNonNull(vmaxPref).setSummary(vmaxPref.getText());
            vmaxPref.setOnPreferenceChangeListener(listener);


            EditTextPreference imaxPref = findPreference(IMAX_PREF);
            Objects.requireNonNull(imaxPref).setSummary(imaxPref.getText());
            imaxPref.setOnPreferenceChangeListener(listener);


            EditTextPreference delayPref = findPreference(DELAY_PREF);
            Objects.requireNonNull(delayPref).setSummary(delayPref.getText());
            delayPref.setOnPreferenceChangeListener(listener);


            EditTextPreference kpPref = findPreference(KP_PREF);
            Objects.requireNonNull(kpPref).setSummary(kpPref.getText());
            kpPref.setOnPreferenceChangeListener(listener);


            EditTextPreference kiPref = findPreference(KI_PREF);
            Objects.requireNonNull(kiPref).setSummary(kiPref.getText());
            kiPref.setOnPreferenceChangeListener(listener);


            EditTextPreference kdPref = findPreference(KD_PREF);
            Objects.requireNonNull(kdPref).setSummary(kdPref.getText());
            kdPref.setOnPreferenceChangeListener(listener);



        }
    }


}