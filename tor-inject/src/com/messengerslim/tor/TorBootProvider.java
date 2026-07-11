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

    @Override
    public boolean onCreate() {
        try {
            Context ctx = getContext();
            if (ctx instanceof Application) {
                Application app = (Application) ctx;
                app.registerActivityLifecycleCallbacks(new LifecycleWatcher(app));
                Log.i(TAG, "LifecycleWatcher zaregistrovany (Tor len ked je Messenger otvoreny)");
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
