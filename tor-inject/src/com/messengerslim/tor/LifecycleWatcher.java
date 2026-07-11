package com.messengerslim.tor;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Zapne Tor VPN ked ide Messenger do popredia a vypne ho ked ide na pozadie / zavrie sa.
 * Setri bateriu: Tor bezi len pocas pouzivania Messengera.
 *
 * Kratky odklad (GRACE) pri prechode na pozadie zabranuje zbytocnemu re-bootstrapu Toru
 * pri rychlom prepnuti appky / otoceni obrazovky.
 */
public class LifecycleWatcher implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "OrcaTor";
    private static final long GRACE_MS = 25_000; // cakaj 25s v pozadi pred vypnutim

    private final Application app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int started = 0;
    private boolean torWanted = false;

    private final Runnable stopTask = new Runnable() {
        @Override public void run() {
            if (started == 0 && torWanted) {
                torWanted = false;
                app.startService(new Intent(app, OrcaTorVpnService.class)
                        .setAction(OrcaTorVpnService.ACTION_STOP));
                Log.i(TAG, "Messenger v pozadi -> Tor vypnuty (setrenie baterie)");
            }
        }
    };

    public LifecycleWatcher(Application app) { this.app = app; }

    @Override
    public void onActivityStarted(Activity a) {
        started++;
        main.removeCallbacks(stopTask);
        if (!torWanted) {
            torWanted = true;
            startTor();
        }
    }

    @Override
    public void onActivityStopped(Activity a) {
        started = Math.max(0, started - 1);
        if (started == 0) {
            // Messenger islo do pozadia -> naplanuj vypnutie Toru po GRACE.
            main.postDelayed(stopTask, GRACE_MS);
        }
    }

    private void startTor() {
        try {
            if (VpnService.prepare(app) == null) {
                app.startForegroundService(new Intent(app, OrcaTorVpnService.class)
                        .setAction(OrcaTorVpnService.ACTION_START));
            } else {
                app.startActivity(new Intent(app, ConsentActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (Throwable t) { Log.e(TAG, "startTor", t); }
    }

    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityResumed(Activity a) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
