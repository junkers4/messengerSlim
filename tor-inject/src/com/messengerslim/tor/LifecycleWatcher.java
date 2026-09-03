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
    private static final long GRACE_MS = 15_000;      // 15s v pozadi -> zhasni vsetko
    // Po 15s v pozadi sa zlozi Tor VPN (tunel dole, VPN kluc zmizne) A zabije sa aj samotny
    // Messenger. Tym padom nemoze bezat na pozadi ani nic leaknut. Pri dalsom otvoreni sa
    // proces nastartuje odznova a TorBootProvider postavi tunel skor, nez ide siet.

    private final Application app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int started = 0;
    private boolean torWanted = false;

    /** Videl tento proces uz nejaku Activity? TorBootProvider podla toho pozna wake z pozadia. */
    public static volatile boolean everForeground = false;

    /** 1. stupen: Tor prec (baterka), tunel ostava blackhole - appka sa von priamo nedostane. */
    private final Runnable stopTask = new Runnable() {
        @Override public void run() {
            if (started == 0 && torWanted) {
                torWanted = false;
                // 1) zloz Tor VPN (:tor proces): tunel dole, notifikacia prec -> VPN kluc zmizne
                app.startService(new Intent(app, OrcaTorVpnService.class)
                        .setAction(OrcaTorVpnService.ACTION_STOP));
                Log.i(TAG, "Messenger " + (GRACE_MS / 1000) + "s v pozadi -> zhasinam Tor VPN a ukoncujem Messenger");
                // 2) po chvili (nech STOP intent stihne dojst do :tor procesu) zabi aj samotny
                //    Messenger, aby nemohol bezat na pozadi ani nic leaknut
                main.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (started == 0) {
                            android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(0);
                        }
                    }
                }, 1200);
            }
        }
    };


    public LifecycleWatcher(Application app) { this.app = app; }

    @Override
    public void onActivityStarted(Activity a) {
        started++;
        everForeground = true;
        main.removeCallbacks(stopTask);
        if (!torWanted) {
            torWanted = true;
            TorGate.reset();   // Tor nabieha odznova -> ukaz pouzivatelovi priebeh
            startTor();
        }
    }

    @Override
    public void onActivityStopped(Activity a) {
        started = Math.max(0, started - 1);
        if (started == 0) {
            TorGate.detach();      // brana aj jej polling odchadzaju s poslednou Activity
            // Messenger islo do pozadia -> vypni Tor, tunel necha blackhole.
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
    @Override public void onActivityResumed(Activity a) { TorGate.attach(a); }
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
