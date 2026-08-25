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
                Log.i(TAG, "LifecycleWatcher zaregistrovany (Tor sa spusti ked Messenger pride do popredia)");

                // Tunel sa NESPUSTA odtialto. ContentProvider.onCreate bezi pri starte procesu
                // este PRED prvou Activity, cize v pozadi - a Android z pozadia nepovoli promociu
                // sluzby na foreground (startForeground zlyha -> ANR "did not call startForeground"
                // -> kill :tor procesu po ~42s). Preto tunel dvíha az LifecycleWatcher.onActivityStarted,
                // kde uz appka je v popredi a FGS je povolena. Kedze background-wake je vypnuty
                // (ziadne FCM/boot), Messenger sa aj tak spusta iba do popredia.
                OrcaTorVpnService.marker(app).delete();
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
