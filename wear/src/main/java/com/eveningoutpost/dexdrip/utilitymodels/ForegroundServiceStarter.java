package com.eveningoutpost.dexdrip.utilitymodels;

import android.app.Service;
import android.content.Context;
import android.os.Build;

import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.models.UserError.Log;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST;
import static com.eveningoutpost.dexdrip.utilitymodels.Notifications.ongoingNotificationId;

/**
 * jamorham
 *
 * Previously a no-op stub on Wear OS ("has no effect... may need to revisit if there are
 * performance issues on android 8 on wear"). On modern Wear OS, a BLE collector service that
 * never calls startForeground() gets killed by the system's app-idle enforcement within
 * roughly a minute - long before it can scan for and pair with a transmitter. Mirrors the
 * phone's implementation so the watch's own collector can actually stay alive.
 */
public class ForegroundServiceStarter {

    private static final String TAG = "FOREGROUND";

    final private Service mService;
    final private Context mContext;

    public ForegroundServiceStarter(Context context, Service service) {
        mContext = context;
        mService = service;
    }

    public void start() {
        if (mService == null) {
            Log.e(TAG, "SERVICE IS NULL - CANNOT START!");
            return;
        }
        Log.d(TAG, "should be moving to foreground");
        final long end = System.currentTimeMillis() + (60000 * 5);
        final long start = end - (60000 * 60 * 3) - (60000 * 10);
        foregroundStatus();
        Log.d(TAG, "CALLING START FOREGROUND: " + mService.getClass().getSimpleName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                mService.startForeground(ongoingNotificationId, new Notifications().createOngoingNotification(new BgGraphBuilder(mContext, start, end), mContext), FOREGROUND_SERVICE_TYPE_MANIFEST);
            } catch (IllegalArgumentException e) {
                UserError.Log.e(TAG, "Got exception trying to use Android 10+ service starting for " + mService.getClass().getSimpleName() + " " + e);
                mService.startForeground(ongoingNotificationId, new Notifications().createOngoingNotification(new BgGraphBuilder(mContext, start, end), mContext));
            }
        } else {
            mService.startForeground(ongoingNotificationId, new Notifications().createOngoingNotification(new BgGraphBuilder(mContext, start, end), mContext));
        }
    }

    public void stop() {
        Log.d(TAG, "should be moving out of foreground");
        mService.stopForeground(true);
    }

    protected void foregroundStatus() {
        Inevitable.task("foreground-status", 2000, () -> UserError.Log.d("XFOREGROUND", mService.getClass().getSimpleName() + (JoH.isServiceRunningInForeground(mService.getClass()) ? " is running in foreground" : " is not running in foreground")));
    }

    public static boolean shouldRunCollectorInForeground() {
        return true;
    }

}
