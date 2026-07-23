package com.eveningoutpost.dexdrip;


import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;

import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;

public class NWPreferences extends PreferenceActivity {

    private SharedPreferences mPrefs;
    public PreferenceScreen screen;
    public PreferenceCategory category;
    public Preference collectionMethod;
    public PreferenceCategory watchcategory;
    public Preference showBridgeBattery;
    private static final String TAG = NWPreferences.class.getSimpleName();

    @Override
    protected void onPause(){
        super.onPause();
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        screen = (PreferenceScreen) findPreference("preferenceScreen");
        category = (PreferenceCategory) findPreference("collection_category");
        collectionMethod = findPreference("dex_collection_method");
        showBridgeBattery = findPreference("showBridgeBattery");
        watchcategory = (PreferenceCategory) findPreference("category");
        bindPreferenceSummaryToValue(collectionMethod);
        listenForChangeInSettings();
        setCollectionPrefs();

        final Preference alertDismissListenerPref = findPreference("enable_alert_dismiss_listener");
        if (alertDismissListenerPref != null) {
            alertDismissListenerPref.setOnPreferenceClickListener(preference -> {
                openNotificationListenerSettings();
                return true;
            });
        }
    }

    // Not every watch/OEM Settings app ships an activity for these actions (e.g. confirmed
    // missing on this Samsung Wear build via an ActivityNotFoundException crash), so each step
    // falls back rather than letting the exception propagate and take down the whole app.
    private void openNotificationListenerSettings() {
        try {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            return;
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "No notification listener settings screen on this device: " + e);
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
            return;
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "No app details settings screen on this device: " + e);
        }
        JoH.static_toast_long(getApplicationContext(), "No notification access screen on this watch. Grant via adb: "
                + "adb shell cmd notification allow_listener " + getPackageName()
                + "/com.eveningoutpost.dexdrip.services.WearAlertBridgeDismissListener");
    }

    private static void bindPreferenceSummaryToValue(Preference preference) {
        try {
            preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getString(preference.getKey(), ""));
        } catch (Exception e) {
            Log.e(TAG, "Got exception binding preference summary: " + e.toString());
        }
    }

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            Log.d(TAG, "Set preference summary: " + stringValue);
            preference.setSummary(stringValue);
            return true;
        }
    };

    public SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

        if(key.compareTo("dex_collection_method") == 0) {
            setCollectionPrefs();
        }

        }
    };

    public void listenForChangeInSettings() {
        mPrefs.registerOnSharedPreferenceChangeListener(prefListener);
        // TODO do we need an unregister!?
    }

    public void setCollectionPrefs() {
        //if (mPrefs.getBoolean("dex_collection_method", false)) {//DexCollectionType.DexcomG5
        if (DexCollectionType.hasBluetooth()) {
            screen.addPreference(category);
            Log.d("NWPreferences", "setCollectionPrefs addPreference category");
        }
        else {
            screen.removePreference(category);
            Log.d("NWPreferences", "setCollectionPrefs removePreference category");
        }
        if (DexCollectionType.hasBattery()) {
            watchcategory.addPreference(showBridgeBattery);
            Log.d("NWPreferences", "setCollectionPrefs addPreference showBridgeBattery");
        }
        else {
            watchcategory.removePreference(showBridgeBattery);
            Log.d("NWPreferences", "setCollectionPrefs removePreference showBridgeBattery");
        }

        if (collectionMethod != null && category != null) {
            //category.removePreference(collectionMethod);
        }

    }
}