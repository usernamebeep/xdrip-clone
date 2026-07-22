package com.eveningoutpost.dexdrip.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Notifications;

/**
 * Minimal, momentary foreground service used as a fallback to guarantee a genuinely active
 * foreground Context for firing a direct Vibrator call. AlertPlayer.vibrateDirect() prefers
 * borrowing DexCollectionService's context when that collector is already running (no extra
 * startup latency), but the collector isn't always alive (it stops/restarts with the BLE
 * connection state) - this service exists so vibration stays reliable regardless. Stops itself
 * shortly after the pattern finishes playing.
 */
public class AlertVibrationService extends Service {

    private static final String TAG = AlertVibrationService.class.getSimpleName();
    public static final String EXTRA_PATTERN = "pattern";
    private static final String CHANNEL_ID = "xdrip_alert_vibration_service";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(Notifications.alertVibrationServiceNotificationId, buildNotification());
        final long[] pattern = (intent != null) ? intent.getLongArrayExtra(EXTRA_PATTERN) : null;
        if (pattern != null) {
            // startForeground() returning doesn't necessarily mean the system has already
            // finished recognizing this process as foreground - vibrating in the very next line
            // still produced the wrong/default pattern on a real device (confirmed via real-device
            // testing), unlike an already-long-running foreground service's context. This delay
            // gives that promotion time to actually take effect before firing.
            new Handler(Looper.getMainLooper()).postDelayed(() -> fire(pattern), 400);
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                final NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Alert delivery", NotificationManager.IMPORTANCE_MIN);
                channel.setShowBadge(false);
                mgr.createNotificationChannel(channel);
            }
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Delivering alert")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build();
    }

    @SuppressWarnings("deprecation")
    private void fire(long[] pattern) {
        try {
            final Vibrator vibrator = getVibrator();
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "AlertVibrationService fire failed: " + e);
        }
        long total = 0;
        for (long ms : pattern) total += ms;
        new Handler(Looper.getMainLooper()).postDelayed(this::stopSelf, total + 500);
    }

    private Vibrator getVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm != null ? vm.getDefaultVibrator() : null;
        }
        return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
