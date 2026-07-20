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
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.LongTextComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.RangedValueComplicationData;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;

import com.activeandroid.ActiveAndroid;
import com.eveningoutpost.dexdrip.BestGlucose;
import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.Inevitable;
import com.eveningoutpost.dexdrip.utilitymodels.PersistentStore;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.xdrip;

import static com.eveningoutpost.dexdrip.utilitymodels.BgGraphBuilder.unitizedDeltaString;

/**
 * Watch face complication data source: supplies the current glucose value (and, on tap, toggles
 * between showing the delta or the time since last reading).
 */
public class CustomComplicationProviderService extends ComplicationDataSourceService {

    private static final String TAG = "ComplicationProvider";
    private static final long STALE_MS = Constants.MINUTE_IN_MS * 15;
    private static final long FRESH_MS = Constants.MINUTE_IN_MS * 5;
    private static final float RANGED_LOW_MGDL = 70f;
    private static final float RANGED_HIGH_MGDL = 240f;

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
            // Home.java's own display uses BestGlucose.getDisplayGlucose().delta_arrow rather than
            // bgReading.displaySlopeArrow() - the latter is derived from calculated_value_slope,
            // a simpler per-reading slope that can disagree with the windowed/regression slope
            // BestGlucose computes, causing the complication's arrow to mismatch the phone's.
            final BestGlucose.DisplayGlucose dg = BestGlucose.getDisplayGlucose();
            final String slopeArrow = (dg != null && dg.delta_arrow != null && dg.delta_arrow.length() > 0)
                    ? dg.delta_arrow : bgReading.displaySlopeArrow();
            numberText = bgReading.displayValue(this) + " " + slopeArrow;
        } else {
            numberText = "old " + niceTimeSinceBgReading(bgReading);
            is_stale = true;
        }

        Log.d(TAG, "Returning complication text: " + numberText);

        COMPLICATION_STATE state = COMPLICATION_STATE.get_enum((int) PersistentStore.getLong(ComplicationTapBroadcastReceiver.COMPLICATION_STORE));
        if (state == null) state = COMPLICATION_STATE.DELTA;

        ComplicationData complicationData = null;

        if (dataType == ComplicationType.SHORT_TEXT) {
            final String titleText;
            UserError.Log.d(TAG, "SHORT_TEXT Current complication state:" + state);
            switch (state) {
                case DELTA:
                    titleText = getDeltaText(bgReading, is_stale);
                    break;
                case AGO:
                    titleText = niceTimeSinceBgReading(bgReading);
                    break;
                default:
                    titleText = "ERR!";
            }
            complicationData = new ShortTextComplicationData.Builder(
                    plain(numberText), plain(numberText))
                    .setTitle(plain(titleText))
                    .setTapAction(complicationPendingIntent)
                    .build();
        } else if (dataType == ComplicationType.LONG_TEXT) {
            final String numberTextLong = numberText + " " + getDeltaText(bgReading, is_stale) + " (" + niceTimeSinceBgReading(bgReading) + ")";
            Log.d(TAG, "Returning complication text Long: " + numberTextLong);

            // Loop status by @gregorybel
            final String externalStatusString = PersistentStore.getString("remote-status-string");
            Log.d(TAG, "Returning complication status: " + externalStatusString);

            UserError.Log.d(TAG, "LONG_TEXT Current complication state:" + state);
            complicationData = new LongTextComplicationData.Builder(
                    plain(numberTextLong), plain(numberTextLong))
                    .setTitle(plain(externalStatusString != null ? externalStatusString : ""))
                    .setTapAction(complicationPendingIntent)
                    .build();
        } else if (dataType == ComplicationType.RANGED_VALUE) {
            final float glucoseMgdl = bgReading != null ? (float) bgReading.getDg_mgdl() : RANGED_LOW_MGDL;
            final float clamped = Math.max(RANGED_LOW_MGDL, Math.min(RANGED_HIGH_MGDL, glucoseMgdl));
            complicationData = new RangedValueComplicationData.Builder(
                    clamped, RANGED_LOW_MGDL, RANGED_HIGH_MGDL, plain(numberText))
                    .setText(plain(numberText))
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

    private static String niceTimeSinceBgReading(BgReading bgReading) {
        return bgReading != null ? JoH.niceTimeSince(bgReading.timestamp).replaceAll(" ", "").replaceAll("(^[0-9]+[a-zA-Z])[a-zA-Z]*$", "$1") : "";
    }

    private static String getDeltaText(BgReading bgReading, boolean is_stale) {
        final boolean doMgdl = Pref.getString("units", "mgdl").equals("mgdl");
        return (!is_stale ? (bgReading != null ? unitizedDeltaString(false, false, Home.get_follower(), doMgdl) : "null") : "");
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
