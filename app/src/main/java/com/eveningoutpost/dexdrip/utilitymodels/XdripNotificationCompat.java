package com.eveningoutpost.dexdrip.utilitymodels;

import android.app.Notification;
import androidx.core.app.NotificationCompat;

import com.eveningoutpost.dexdrip.models.UserError;

/**
 * Created by jamorham on 18/10/2017.
 */

public class XdripNotificationCompat extends NotificationCompat {

    private final static String TAG = XdripNotificationCompat.class.getSimpleName();

    public static Notification build(NotificationCompat.Builder builder) {
        String id;
        try {
            id = NotificationChannels.getChan(builder).getId();
        } catch (Exception e) {
            // Fallback to generic alert channel if the guesser fails
            id = NotificationChannels.BG_ALERT_CHANNEL;
        }
        builder.setChannelId(id);

        // Ensure alerts are independent and not summaries
        builder.setGroup(null);
        builder.setGroupSummary(false);

        builder.setCategory(NotificationCompat.CATEGORY_ALARM);

        // TEST: setOngoing() is in Google's documented Wear OS bridging exclusion list
        // (developer.android.com/training/wearables/notifications/bridger) - checking whether
        // Samsung's own mirroring layer honors it too. Android 14+ restored swipe-dismiss for
        // ongoing notifications, so this should carry minimal UX cost on this device (Android 16).
        // Revert if inconclusive.
        builder.setOngoing(true);

        final Notification n = builder.build();

        UserError.Log.d(TAG, "NotifCompat: chan=" + id +
                " group=" + NotificationCompat.getGroup(n) +
                " summary=" + ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0));

        return n;
    }
}
