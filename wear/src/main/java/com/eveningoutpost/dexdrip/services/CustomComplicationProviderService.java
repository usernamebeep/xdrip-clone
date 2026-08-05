/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// provided by lurosys
// migrated to the AndroidX ComplicationDataSourceService API for Wear OS 3+/6

package com.eveningoutpost.dexdrip.services;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.os.RemoteException;
import android.util.Log;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationText;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.CountUpTimeReference;
import androidx.wear.watchface.complications.data.LongTextComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.RangedValueComplicationData;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText;
import androidx.wear.watchface.complications.data.TimeDifferenceStyle;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;

import com.activeandroid.ActiveAndroid;
import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.Inevitable;
import com.eveningoutpost.dexdrip.utilitymodels.PersistentStore;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;
import com.eveningoutpost.dexdrip.xdrip;

import java.time.Instant;

/**
 * Watch face complication data source: supplies the current glucose value (and, on tap, toggles
 * between showing the delta or the time since last reading).
 */
public class CustomComplicationProviderService extends ComplicationDataSourceService {

    private static final String TAG = "CompphonlicationProvider";
    private static final long STALE_MS = Constants.MINUTE_IN_MS * 15;
    private static final long FRESH_MS = Constants.MINUTE_IN_MS * 5;
    private static final float RANGED_LOW_MGDL = 70f;
    private static final float RANGED_HIGH_MGDL = 240f;
    private static final String WATCH_COLLECTOR_MARKER = "⌚"; // watch emoji - collection is running on the watch, not the phone

    enum COMPLICATION_STATE {
        DELTA(0),
        AGO(1),
        RESET(2);

        private final int enum_value;

        COMPLICATION_STATE(int value) {
            this.enum_value = value;
        }

        public int getValue() {
            return enum_value;
        }

        public static COMPLICATION_STATE get_enum(int value) {
            for (COMPLICATION_STATE state : COMPLICATION_STATE.values()) {
                if (state.getValue() == value) return state;
            }
            return null;
        }
    }

    /*
     * Called when the complication needs updated data from your data source. There are several
     * scenarios when this will happen:
     *
     *   1. An active watch face complication is changed to use this data source
     *   2. A complication using this data source becomes active
     *   3. The period of time you specified in the manifest has elapsed (UPDATE_PERIOD_SECONDS)
     *   4. You triggered an update from your own class via the
     *       ComplicationDataSourceUpdateRequester.requestUpdateAll() method.
     */
    @Override
    public void onComplicationRequest(ComplicationRequest request, ComplicationRequestListener listener) {
        final int complicationId = request.getComplicationInstanceId();
        final ComplicationType dataType = request.getComplicationType();
        Log.d(TAG, "onComplicationRequest() id: " + complicationId + " type: " + dataType);

        final ComponentName thisProvider = new ComponentName(this, getClass());
        // We pass the complication id, so we can only update the specific complication tapped.
        final PendingIntent complicationPendingIntent =
                ComplicationTapBroadcastReceiver.getToggleIntent(this, thisProvider, complicationId);

        BgReading bgReading = BgReading.last(true);
        if ((bgReading == null) || (JoH.msSince(bgReading.timestamp) >= FRESH_MS)) {
            try {
                ActiveAndroid.clearCache(); // we may be in another process!
            } catch (Exception e) {
                Log.d(TAG, "Couldn't clear cache: " + e);
            }
            bgReading = BgReading.last(true);
        }

        boolean is_stale = false;
        final String numberText;
        if (bgReading == null) {
            numberText = "null";
        } else if (JoH.msSince(bgReading.timestamp) < STALE_MS) {
            // displayValue()/displaySlopeArrow() prefer the synced dg_mgdl/dg_slope (the phone's
            // own BestGlucose.getDisplayGlucose() figures, see WatchUpdaterService#dataMap and
            // ListenerService#saveSingleIncomingBg) over the raw per-reading calculated_value -
            // the same fields the ongoing notification and getDeltaText() below read from, so all
            // wear surfaces and the phone's main screen show the identical number/arrow.
            // (activeSlopeArrow() was tried here instead but reverted - see find_new_curve()'s
            // fix for why its parabolic fit was numerically unstable.)
            numberText = bgReading.displayValue(this) + " " + bgReading.displaySlopeArrow();
        } else {
            numberText = "old";
            is_stale = true;
        }

        // A plain string "time since" is baked in at whatever moment we happen to be asked for
        // data (a new reading, or the system's own periodic poke) - it then sits frozen on the
        // watch face, so it can read "1m" when four minutes have actually gone by. A
        // TimeDifferenceComplicationText instead carries the reference timestamp itself, and the
        // watch face render system recomputes/redraws the elapsed m:ss time locally (as often as
        // once a second, per STOPWATCH style) without ever calling back into this service - so it
        // stays accurate with zero extra battery cost, independent of how often refresh() runs.
        final ComplicationText numberComplicationText = is_stale
                ? liveAgo(bgReading, "old ^1")
                : plain(numberText);

        Log.d(TAG, "Returning complication text: " + numberText);

        COMPLICATION_STATE state = COMPLICATION_STATE.get_enum((int) PersistentStore.getLong(ComplicationTapBroadcastReceiver.COMPLICATION_STORE));
        if (state == null) state = COMPLICATION_STATE.DELTA;

        ComplicationData complicationData = null;

        if (dataType == ComplicationType.SHORT_TEXT) {
            UserError.Log.d(TAG, "SHORT_TEXT Current complication state:" + state);
            final ComplicationText titleComplicationText;
            switch (state) {
                case DELTA: {
                    String titleText = getDeltaText(bgReading, is_stale);
                    if (isWatchCollector()) {
                        titleText = titleText + " " + WATCH_COLLECTOR_MARKER;
                    }
                    titleComplicationText = plain(titleText);
                    break;
                }
                case AGO:
                    // Live-updating, same reasoning as numberComplicationText above - this is the
                    // exact "1m" vs "actually 4m" symptom, since AGO's whole purpose is displaying
                    // elapsed time.
                    titleComplicationText = bgReading != null
                            ? liveAgo(bgReading, isWatchCollector() ? "^1 " + WATCH_COLLECTOR_MARKER : "^1")
                            : plain("");
                    break;
                default:
                    titleComplicationText = plain("ERR!");
            }
            complicationData = new ShortTextComplicationData.Builder(
                    numberComplicationText, numberComplicationText)
                    .setTitle(titleComplicationText)
                    .setTapAction(complicationPendingIntent)
                    .build();
        } else if (dataType == ComplicationType.LONG_TEXT) {
            UserError.Log.d(TAG, "LONG_TEXT Current complication state:" + state);
            final ComplicationText numberTextLongComplication;
            if (bgReading == null) {
                numberTextLongComplication = plain(numberText + " " + getDeltaText(null, false));
            } else if (is_stale) {
                // Already reads "old <live ago>" via numberComplicationText - no need to repeat
                // the elapsed time a second time in parentheses like the pre-live-text version did.
                numberTextLongComplication = numberComplicationText;
            } else {
                numberTextLongComplication = liveAgo(bgReading, numberText + " " + getDeltaText(bgReading, false) + " (^1)");
            }
            Log.d(TAG, "Returning complication text Long, stale=" + is_stale);

            // Loop status by @gregorybel
            final String externalStatusString = PersistentStore.getString("remote-status-string");
            Log.d(TAG, "Returning complication status: " + externalStatusString);

            complicationData = new LongTextComplicationData.Builder(
                    numberTextLongComplication, numberTextLongComplication)
                    .setTitle(plain(externalStatusString != null ? externalStatusString : ""))
                    .setTapAction(complicationPendingIntent)
                    .build();
        } else if (dataType == ComplicationType.RANGED_VALUE) {
            final float glucoseMgdl = bgReading != null ? (float) bgReading.getDg_mgdl() : RANGED_LOW_MGDL;
            final float clamped = Math.max(RANGED_LOW_MGDL, Math.min(RANGED_HIGH_MGDL, glucoseMgdl));
            complicationData = new RangedValueComplicationData.Builder(
                    clamped, RANGED_LOW_MGDL, RANGED_HIGH_MGDL, numberComplicationText)
                    .setText(numberComplicationText)
                    .setTapAction(complicationPendingIntent)
                    .build();
        } else {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "Unexpected complication type " + dataType);
            }
        }

        try {
            // Passing null tells the system to keep showing whatever it last had (equivalent to
            // the old API's ComplicationManager#noUpdateRequired()).
            listener.onComplicationData(complicationData);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to deliver complication data", e);
        }
    }

    /*
     * Supplies placeholder data shown in the watch face editor's complication picker, before the
     * user has actually added this complication to a watch face slot.
     */
    @Override
    public ComplicationData getPreviewData(ComplicationType type) {
        if (type == ComplicationType.SHORT_TEXT) {
            return new ShortTextComplicationData.Builder(plain("5.5"), plain("5.5"))
                    .setTitle(plain("+0.1"))
                    .build();
        } else if (type == ComplicationType.LONG_TEXT) {
            return new LongTextComplicationData.Builder(
                    plain("5.5 +0.1 (2m)"), plain("5.5 +0.1 (2m)"))
                    .build();
        } else if (type == ComplicationType.RANGED_VALUE) {
            return new RangedValueComplicationData.Builder(
                    100f, RANGED_LOW_MGDL, RANGED_HIGH_MGDL, plain("100"))
                    .setText(plain("100"))
                    .build();
        }
        return null;
    }

    private static PlainComplicationText plain(String text) {
        return new PlainComplicationText.Builder(text).build();
    }

    // surroundingText must contain the literal placeholder "^1", which the render system replaces
    // with the live-formatted elapsed time computed from bgReading's timestamp. STOPWATCH renders
    // as m:ss (h:mm:ss past an hour) and ticks every second - still driven by the OS render loop,
    // not by this service, so it costs nothing extra over the old once-a-minute SHORT_SINGLE_UNIT.
    private static ComplicationText liveAgo(BgReading bgReading, String surroundingText) {
        return new TimeDifferenceComplicationText.Builder(
                TimeDifferenceStyle.STOPWATCH,
                new CountUpTimeReference(Instant.ofEpochMilli(bgReading.timestamp)))
                .setText(surroundingText)
                .build();
    }

    private static String getDeltaText(BgReading bgReading, boolean is_stale) {
        return (!is_stale ? (bgReading != null ? bgReading.displayDelta(false, false) : "null") : "");
    }

    // Mirrors BaseWatchFace's isCollectorRunning check: true either because the watch has been
    // explicitly forced on as collector, or because its BLE collector service is actually running
    // right now (covers Notifications.checkPhoneDataStalenessFailover's local auto-start, which
    // never touches force_wearG5).
    private static boolean isWatchCollector() {
        final boolean enable_wearG5 = Pref.getBoolean("enable_wearG5", false);
        if (!enable_wearG5) return false;
        if (Pref.getBoolean("force_wearG5", false)) return true;
        final Class<?> serviceClass = DexCollectionType.getCollectorServiceClass();
        return serviceClass != null && JoH.isServiceRunningInForeground(serviceClass);
    }

    public static void refresh() {
        Inevitable.task("refresh-complication", 500, new Runnable() {
            @Override
            public void run() {
                if (JoH.ratelimit("complication-refresh", 5)) {
                    Log.d(TAG, "Complication refresh() executing");
                    final ComponentName componentName = new ComponentName(xdrip.getAppContext(), "com.eveningoutpost.dexdrip.services.CustomComplicationProviderService");
                    ComplicationDataSourceUpdateRequester.create(xdrip.getAppContext(), componentName).requestUpdateAll();
                }
            }
        });
        Log.d(TAG, "Complication refresh() called");
    }
}
