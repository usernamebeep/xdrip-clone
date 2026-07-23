package com.eveningoutpost.dexdrip.services;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.eveningoutpost.dexdrip.models.UserError.Log;

/**
 * Watches for the user swiping away the phone's bg-alert notification once it has been
 * auto-bridged to the watch by the stock Wear OS notification bridge (this is the default path
 * when watch_alert_mode == "none", i.e. the watch is not independently raising its own alert - see
 * AlertPlayer.watchAlertsEnabled()). That bridged copy has no deleteIntent of its own, so without
 * this listener the phone is never told the user dismissed it on the watch and keeps alerting
 * until it separately times out.
 */
public class WearAlertBridgeDismissListener extends NotificationListenerService {
    private static final String TAG = WearAlertBridgeDismissListener.class.getSimpleName();

    // Must match the app module's Notifications.exportAlertNotificationId (app/src/main/java/
    // .../utilitymodels/Notifications.java) - notification ids aren't shared across the app/wear
    // gradle modules, and Wear OS notification bridging preserves the origin device's id/tag/
    // package name for the mirrored copy shown on the watch, so this has to be kept in sync by hand.
    private static final int PHONE_BG_ALERT_NOTIFICATION_ID = 6;

    @Override
    public void onNotificationRemoved(final StatusBarNotification sbn, final RankingMap rankingMap, final int reason) {
        if (reason != REASON_CANCEL && reason != REASON_CANCEL_ALL) {
            // not a user swipe/clear-all dismissal (e.g. the phone itself cancelling the
            // notification once the alert is snoozed/stopped shows up as REASON_APP_CANCEL here)
            return;
        }
        if (!getPackageName().equals(sbn.getPackageName()) || sbn.getId() != PHONE_BG_ALERT_NOTIFICATION_ID) {
            return;
        }
        Log.d(TAG, "Bridged bg alert notification dismissed on watch - relaying snooze to phone");
        final Intent intent = new Intent(getApplicationContext(), SnoozeOnNotificationDismissService.class);
        intent.putExtra("alertType", "bg_alerts");
        startService(intent);
    }
}
