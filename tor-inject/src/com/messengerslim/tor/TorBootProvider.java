package com.messengerslim.tor;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * Hook: ContentProvider.onCreate bezi pri starte Messengera (pred Application.onCreate).
 * Zaregistruje LifecycleWatcher, ktory zapne Tor VPN len ked je Messenger v popredi
 * a vypne ho ked ide na pozadie (setrenie baterie). Ziadna druha appka/launcher.
 */
public class TorBootProvider extends ContentProvider {
    private static final String TAG = "OrcaTor";
    /** Max. cakanie na tunel pri starte procesu (drzi main thread - musi byt kratke). */
    private static final long TUNNEL_WAIT_MS = 2500;

    @Override
    public boolean onCreate() {
        try {
            Context ctx = getContext();
            if (ctx instanceof Application) {
                Application app = (Application) ctx;
                app.registerActivityLifecycleCallbacks(new LifecycleWatcher(app));
                Log.i(TAG, "LifecycleWatcher zaregistrovany (Tor len ked je Messenger otvoreny)");

                // Bezi na zaciatku startu procesu (initOrder max). Postav tunel HNED a pockaj
                // na neho - inak by Messenger stihol poslat prve pakety mimo Toru.
                if (android.net.VpnService.prepare(app) == null) {
                    try {
                        // stary marker prec - cakame na potvrdenie od :tor procesu
                        OrcaTorVpnService.marker(app).delete();
                        app.startForegroundService(new android.content.Intent(app, OrcaTorVpnService.class)
                                .setAction(OrcaTorVpnService.ACTION_START));
                        long t0 = android.os.SystemClock.elapsedRealtime();
                        boolean up = false;
                        java.io.File marker = OrcaTorVpnService.marker(app);
                        while (android.os.SystemClock.elapsedRealtime() - t0 < TUNNEL_WAIT_MS) {
                            if (marker.exists()) { up = true; break; }
                            try { Thread.sleep(20); } catch (InterruptedException ie) { break; }
                        }
                        Log.i(TAG, "Cakanie na tunel: " + (android.os.SystemClock.elapsedRealtime() - t0)
                                + " ms, hore=" + up);
                    } catch (Throwable t) {
                        // napr. FGS sa neda spustit z pozadia - Tor nabehne pri otvoreni appky
                        Log.w(TAG, "tunel pri starte procesu nenabehol: " + t);
                    }
                }
            }
        } catch (Throwable t) { Log.e(TAG, "boot hook", t); }
        return true;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
