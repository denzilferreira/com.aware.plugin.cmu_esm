package com.aware.plugin.cmu_esm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.aware.Aware;

public class Settings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    //Plugin settings in XML @xml/preferences
    public static final String STATUS_PLUGIN_CMU_ESM = "status_plugin_cmu_esm";
    public static final String UPDATE_QUESTIONS = "update_questions";

    //Plugin settings UI elements
    private static CheckBoxPreference status;
    private static Preference update_questions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        status = (CheckBoxPreference) findPreference(STATUS_PLUGIN_CMU_ESM);
        if( Aware.getSetting(this, STATUS_PLUGIN_CMU_ESM).length() == 0 ) {
            Aware.setSetting(this, STATUS_PLUGIN_CMU_ESM, true);
        }
        status.setChecked(Aware.getSetting(this, STATUS_PLUGIN_CMU_ESM).equals("true"));

        update_questions = (Preference) findPreference(UPDATE_QUESTIONS);
        update_questions.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent fetchQuestions = new Intent(getApplicationContext(), QuestionUpdater.class);
                startService(fetchQuestions);
                return true;
            }
        });

        Aware.startPlugin(this, "com.aware.plugin.cmu_esm");
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference setting = findPreference(key);
        if( setting.getKey().equals(STATUS_PLUGIN_CMU_ESM) ) {
            Aware.setSetting(this, key, sharedPreferences.getBoolean(key, false));
            status.setChecked(sharedPreferences.getBoolean(key, false));
        }
        if (Aware.getSetting(this, STATUS_PLUGIN_CMU_ESM).equals("true")) {
            Aware.startPlugin(getApplicationContext(), "com.aware.plugin.cmu_esm");
        } else {
            Aware.stopPlugin(getApplicationContext(), "com.aware.plugin.cmu_esm");
        }
    }
}
