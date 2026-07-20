package com.eveningoutpost.dexdrip;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.support.wearable.activity.WearableActivity;
//import android.support.v4.os.ResultReceiver;
import android.util.Log;

import android.view.View;

import com.eveningoutpost.dexdrip.models.JoH;

/**
 * Simple Activity for displaying Permission Rationale to user.
 */
public class LocationPermissionActivity extends WearableActivity {//KS

    private static final String TAG = LocationPermissionActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate ENTERING");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_permission);
        JoH.vibrateNotice();
    }

    public void onClickEnablePermission(View view) {
        Log.d(TAG, "onClickEnablePermission()");

        // On 23+ (M+) devices, GPS permission not granted. Request permission.
        // On 31+ (Android 12+) devices, Bluetooth access also needs its own runtime
        // permissions - without these, BLE scanning/connection silently fails even with
        // location granted.
        // On 33+ (Android 13+) devices, notifications also need their own runtime permission -
        // without it, every alert/status notification is silently dropped.
        final java.util.List<String> permissions = new java.util.ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        ActivityCompat.requestPermissions(
                this,
                permissions.toArray(new String[0]),
                PERMISSION_REQUEST_FINE_LOCATION);

    }

    /*
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        Log.d(TAG, "onRequestPermissionsResult()");

        if (requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) allGranted = false;
            }
            if (allGranted) {
                Log.i(TAG, "onRequestPermissionsResult() granted");
                finish();
            }
        }
    }
}