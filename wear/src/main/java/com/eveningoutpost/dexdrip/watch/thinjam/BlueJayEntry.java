package com.eveningoutpost.dexdrip.watch.thinjam;

import android.os.Build;

// jamorham

public class BlueJayEntry {

// very lightweight entry point class to avoid loader overhead when not in use

    public static boolean isPhoneCollectorDisabled() {
        return false; // stub
    }

    public static boolean isNative() {
        return Build.MODEL.startsWith("BlueJay U");
    }

}
