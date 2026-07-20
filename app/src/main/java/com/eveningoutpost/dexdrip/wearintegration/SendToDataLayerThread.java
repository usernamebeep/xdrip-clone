package com.eveningoutpost.dexdrip.wearintegration;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Emma Black on 12/26/14.
 */
class SendToDataLayerThread extends AsyncTask<DataMap,Void,Void> {
    private final Context context;
    private static int concurrency = 0;
    private static int state = 0;
    private static final String TAG = "jamorham wear";
    private static final ReentrantLock lock = new ReentrantLock();
    private static long lastlock = 0;
    private static final boolean testlockup = false; // always false in production
    String path;

    SendToDataLayerThread(String path, Context context) {
        this.path = path;
        this.context = context.getApplicationContext();
    }

    @Override
    protected void onPreExecute()
    {
        concurrency++;
        if ((concurrency > 12) || ((concurrency > 3 && (lastlock != 0) && (JoH.tsl() - lastlock) > 300000))) {//KS increase from 8 to 12
            // error if 9 concurrent threads or lock held for >5 minutes with concurrency of 4
            final String err = "Wear Integration deadlock detected!! "+((lastlock !=0) ? "locked" : "")+" state:"+state+" @"+ JoH.hourMinuteString();
            Home.toaststaticnext(err);
            UserError.Log.e(TAG,err);
        }
        if (concurrency<0) Home.toaststaticnext("Wear Integration impossible concurrency!!");
        UserError.Log.d(TAG, "SendDataToLayerThread pre-execute concurrency: " + concurrency);
    }

    @Override
    protected Void doInBackground(DataMap... params) {
        if (testlockup) {
            try {
                UserError.Log.e(TAG,"WARNING RUNNING TEST LOCK UP CODE - NEVER FOR PRODUCTION");
                Thread.sleep(1000000); // DEEEBBUUGGGG
            } catch (Exception e) {
            }
        }
        sendToWear(params);
        concurrency--;
        UserError.Log.d(TAG, "SendDataToLayerThread post-execute concurrency: " + concurrency);
        return null;
    }

    // Debug function to expose where it might be locking up
    private synchronized void sendToWear(final DataMap... params) {
        if (!lock.tryLock()) {
            Log.d(TAG, "Concurrent access - waiting for thread unlock");
            lock.lock(); // enforce single threading
            Log.d(TAG, "Thread unlocked - proceeding");
        }
        lastlock=JoH.tsl();
        try {
            if (state != 0) {
                UserError.Log.e(TAG, "WEAR STATE ERROR: state=" + state);
            }
            state = 1;
            final List<Node> nodes = Tasks.await(Wearable.getNodeClient(context).getConnectedNodes(), 15, TimeUnit.SECONDS);

            state = 2;
            for (Node node : nodes) {
                state = 3;
                for (DataMap dataMap : params) {
                    state = 4;
                    final byte[] payload = dataMap.toByteArray();
                    boolean sent = false;
                    int retryCount = 0;
                    // mirrors GlucoDataHandler's WearPhoneConnection.sendMessage() retry pattern:
                    // up to 2 retries with an increasing backoff, then give up and log clearly
                    while (!sent && retryCount <= 2) {
                        if (retryCount > 0) {
                            try {
                                Thread.sleep(retryCount * 5000L);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        state = 5;
                        try {
                            Tasks.await(Wearable.getMessageClient(context).sendMessage(node.getId(), path, payload), 15, TimeUnit.SECONDS);
                            state = 6;
                            sent = true;
                            if (retryCount > 0) {
                                UserError.Log.d(TAG, "DataMap retry #" + retryCount + ": " + dataMap + " sent to: " + node.getDisplayName());
                            } else {
                                UserError.Log.d(TAG, "DataMap: " + dataMap + " sent to: " + node.getDisplayName());
                            }
                        } catch (Exception e) {
                            state = 6;
                            UserError.Log.e(TAG, "ERROR: failed to send DataMap (attempt " + (retryCount + 1) + "): " + e);
                            retryCount++;
                        }
                    }
                    if (!sent) {
                        UserError.Log.e(TAG, "ERROR: giving up sending DataMap to " + node.getDisplayName() + " after " + retryCount + " retries, path=" + path);
                    }
                    state = 9;
                }
            }
            state = 0;
        } catch (Exception e) {
            UserError.Log.e(TAG, "Got exception in sendToWear: " + e.toString());
        } finally {
            lastlock=0;
            lock.unlock();
        }
    }
}
