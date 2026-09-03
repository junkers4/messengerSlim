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
                // Stav z minuleho behu (napr. "READY") by branu preskocil - proces startuje
                // odznova, takze Tor este nebezi.
                TorStatus.write(app, TorStatus.DOWN);
                disableWakeComponents(app);
                killIfBackgroundWake();
            }
        } catch (Throwable t) { Log.e(TAG, "boot hook", t); }
        return true;
    }

    /**
     * Proces mohol nastartovat aj bez pouzivatela - Android si bindne nejaku sluzbu
     * (napr. sync adapter kontaktov) a Messenger zije na pozadi BEZ tunela, lebo VPN sa
     * z pozadia dvihnut neda. To je presne ten unik, ktoremu sa vyhybame: ak do
     * BG_KILL_MS neprisla ziadna Activity, proces sa zabije.
     */
    private static final long BG_KILL_FAST_MS = 1_500;   // proces zjavne bezi len pre sluzbu
    private static final long BG_KILL_MS = 6_000;        // poistka, ked je dolezitost nejasna

    private void killIfBackgroundWake() {
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override public void run() {
                // Pri normalnom starte z ikonky ma proces uz teraz dolezitost popredia.
                // Ked ho zobudil bind sluzby, je SERVICE/CACHED - a taky proces tu nema co robit.
                if (!LifecycleWatcher.everForeground && importance() >= 300) kill("bind sluzby");
            }
        }, BG_KILL_FAST_MS);
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (!LifecycleWatcher.everForeground) kill("ziadna Activity");
            }
        }, BG_KILL_MS);
    }

    private void kill(String why) {
        Log.i(TAG, "Start z pozadia (" + why + ") -> ukoncujem proces, ziadny unik");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private int importance() {
        try {
            android.app.ActivityManager.RunningAppProcessInfo info =
                    new android.app.ActivityManager.RunningAppProcessInfo();
            android.app.ActivityManager.getMyMemoryState(info);
            return info.importance;
        } catch (Throwable t) {
            return 0;   // nevieme -> necha rozhodnut az poistku
        }
    }

    /**
     * Manifest ich uz ma enabled="false", ale sync framework aj tak vytvori proces, aby sa
     * na ne bindol. Trvaly override v PackageManageri to zavrie natvrdo.
     */
    private static final String[] WAKE_COMPONENTS = {
        "com.facebook.messaging.phonebookintegration.account.MessengerSyncAdapterService",
        "com.facebook.messaging.phonebookintegration.account.MessengerAuthenticatorService",
    };

    private void disableWakeComponents(Context ctx) {
        android.content.pm.PackageManager pm = ctx.getPackageManager();
        for (String cls : WAKE_COMPONENTS) {
            try {
                android.content.ComponentName cn =
                        new android.content.ComponentName(ctx.getPackageName(), cls);
                if (pm.getComponentEnabledSetting(cn)
                        != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(cn,
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP);
                    Log.i(TAG, "vypnuty budici komponent: " + cls);
                }
            } catch (Throwable ignore) {}
        }
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
