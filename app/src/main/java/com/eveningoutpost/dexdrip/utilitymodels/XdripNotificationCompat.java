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

        final Notification n = builder.build();

        // Marking the notification ongoing suppresses Wear OS/Samsung bridging (confirmed via
        // real-device testing - ongoing notifications are in Google's documented bridging
        // exclusion list, developer.android.com/training/wearables/notifications/bridger, and
        // Samsung's own mirroring layer honors it too). Only do this when the watch is already
        // independently alerting on its own (setLocalOnly(true) upstream, driven by
        // watch_alert_mode) - otherwise (e.g. watch_alert_mode "none") this phone notification is
        // the ONLY alert the user would see on the watch, so it must still be allowed to bridge.
        if ((n.flags & Notification.FLAG_LOCAL_ONLY) != 0) {
            n.flags |= Notification.FLAG_ONGOING_EVENT;
        }

        UserError.Log.d(TAG, "NotifCompat: chan=" + id +
                " group=" + NotificationCompat.getGroup(n) +
                " summary=" + ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0));

        return n;
    }
}
