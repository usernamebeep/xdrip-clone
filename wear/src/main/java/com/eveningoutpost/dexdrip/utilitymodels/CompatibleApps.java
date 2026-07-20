package com.eveningoutpost.dexdrip.utilitymodels;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.eveningoutpost.dexdrip.BuildConfig;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.xdrip;

public class CompatibleApps {

    public static final String EXTERNAL_ALG_PACKAGES = "EXTERNAL_ALG_PACKAGES";

    // ported minimally from app's CompatibleApps.java for DexResetHelper's hard-reset
    // confirmation notification - the app-side notifyAboutCompatibleApps()/InstalledApps
    // checks (Garmin, Fitbit, AndroidAPS, etc.) are phone-only and stay stubbed out here.

    private static String gs(int id) {
        return xdrip.getAppContext().getString(id);
    }

    public static void showNotification(String title, String content, PendingIntent yesIntent, PendingIntent noIntent, PendingIntent contentIntent, int notificationId) {

        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(xdrip.getAppContext(), (String) null)
                .setSmallIcon(R.drawable.ic_action_communication_invert_colors_on)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .addAction(R.drawable.tick_icon_small, gs(R.string.yes), yesIntent)
                .addAction(android.R.drawable.ic_delete, gs(R.string.no), noIntent);

        final NotificationManager mNotifyMgr = (NotificationManager) xdrip.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (mNotifyMgr != null) {
            mNotifyMgr.notify(notificationId, mBuilder.build());
        } else {
            JoH.static_toast_long("Cannot notify!");
        }
    }

    public static PendingIntent createActionIntent(int parent_id, int id, Feature action) {
        return PendingIntent.getBroadcast(xdrip.getAppContext(), id,
                new Intent(xdrip.getAppContext(), CompatibleApps.class)
                        .putExtra("action", action)
                        .putExtra("id", parent_id)
                        .putExtra("auth", BuildConfig.buildUUID),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static PendingIntent createChoiceIntent(int parent_id, int id, Feature action, String title, String msg) {
        return PendingIntent.getBroadcast(xdrip.getAppContext(), id,
                new Intent(xdrip.getAppContext(), CompatibleApps.class)
                        .putExtra("action", Feature.CHOICE)
                        .putExtra("choice", action)
                        .putExtra("id", parent_id)
                        .putExtra("title", title)
                        .putExtra("msg", msg)
                        .putExtra("auth", BuildConfig.buildUUID),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public enum Feature {
        UNKNOWN,
        CHOICE,
        CANCEL,
        ENABLE_GARMIN_FEATURES,
        ENABLE_ANDROIDAPS_FEATURE1,
        ENABLE_ANDROIDAPS_FEATURE2,
        ENABLE_FITBIT_FEATURES,
        ENABLE_LIBRE_ALARM,
        ENABLE_OOP,
        ENABLE_WEAR_OS_SYNC,
        HARD_RESET_TRANSMITTER,
        ENABLE_TASKER,
        FEATURE_X
    }

}
