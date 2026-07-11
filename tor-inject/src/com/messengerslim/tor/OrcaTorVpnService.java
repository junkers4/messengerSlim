package com.messengerslim.tor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import hev.htproxy.TProxyService;

/**
 * VpnService injektovany do Messengera. Smeruje vlastnu appku (com.facebook.orca) cez Tor.
 *
 * Slucku (Tor v rovnakom UID) rozbija guard-exclusion: IP guard uzlov sa vynechaju z trass
 * tunela, takze Torov vlastny traffic ide priamo a Messenger tunelom.
 *
 * KILL-SWITCH (fail-closed):
 *  - pri starte sa hned postavi tun s vynechanim MINULE znamych guardov -> Messenger nema
 *    siet, kym Tor nie je hotovy (ziadny leak). Ak bootstrap stalluje (novy guard), na
 *    chvilu sa siet otvori, nech Tor dobehne.
 *  - watchdog: ak Tor spadne pocas behu, Messenger sa zablackholuje (fail-closed), nie leak.
 */
public class OrcaTorVpnService extends VpnService {

    private static final String TAG = "OrcaTor";
    private static final String SELF_PKG = "com.facebook.orca";
    public static final String ACTION_START = "com.messengerslim.tor.START";
    public static final String ACTION_STOP  = "com.messengerslim.tor.STOP";
    private static final String CHANNEL_ID = "orca_tor";
    private static final int NOTIF_ID = 90210;

    private static final int SOCKS = 9050, DNSP = 5400, MTU = 8500;
    private static final String TUN4 = "198.18.0.1", TUN6 = "fc00::1", MAPDNS = "198.18.0.2";

    public static volatile boolean READY = false;

    private ParcelFileDescriptor tun;
    private TorController tor;
    private final TProxyService tproxy = new TProxyService();
    private volatile boolean tproxyRunning = false, running = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAll(); stopSelf(); return START_NOT_STICKY;
        }
        if (!running) { running = true; new Thread(this::startAll, "orca-tor-start").start(); }
        return START_STICKY;
    }

    private void startAll() {
        try {
            startForeground(NOTIF_ID, notif());
            tor = new TorController(this, SOCKS, DNSP);

            // 1) FAIL-CLOSED: postav tun s vynechanim MINULE znamych guardov. Messenger je
            //    zachyteny (bez hev = bez siete), ale Torov guard-traffic prejde vynimkou.
            List<Long> cached = tor.loadGuards();
            boolean failClosed = false;
            if (!cached.isEmpty()) {
                tun = buildTun(cached);
                if (tun != null) { failClosed = true; Log.i(TAG, "Fail-closed pri starte (cache guardov=" + cached.size() + ")"); }
            }

            // 2) Spusti Tor.
            tor.startAsync();

            // 3) Cakaj na bootstrap. Ak fail-closed a stalluje (novy guard/stary consensus),
            //    docasne otvor siet nech Tor dobehne.
            if (!tor.waitBootstrapped(12_000)) {
                if (failClosed && tun != null) {
                    Log.i(TAG, "Bootstrap stalluje -> docasne otvaram siet pre Tor");
                    ParcelFileDescriptor tmp = tun; tun = null;
                    try { tmp.close(); } catch (Exception ignored) {}
                    failClosed = false;
                }
                if (!tor.waitBootstrapped(120_000)) {
                    Log.e(TAG, "Tor bootstrap zlyhal"); stopAll(); stopSelf(); return;
                }
            }

            // 4) Zisti aktualne guardy a uloz ich pre buduci fail-closed start.
            List<Long> guards = tor.guardIps();
            for (int i = 0; i < 8 && guards.isEmpty(); i++) { Thread.sleep(500); guards = tor.guardIps(); }
            Log.i(TAG, "Guard IP na vynechanie z tunela: " + guards.size());
            if (!guards.isEmpty()) tor.saveGuards(guards);

            // 5) Finalny tun s aktualnou guard-exclusion (atomicky nahradi).
            ParcelFileDescriptor newTun = buildTun(guards);
            if (newTun == null) { Log.e(TAG, "final establish=null"); stopAll(); stopSelf(); return; }
            ParcelFileDescriptor old = tun; tun = newTun;
            try { if (old != null) old.close(); } catch (Exception ignored) {}

            // 6) hev: pumpuj tun <-> Tor SOCKS.
            File cfg = writeHev();
            tproxy.TProxyStartService(cfg.getAbsolutePath(), tun.getFd());
            tproxyRunning = true;

            READY = true;
            Log.i(TAG, "Messenger je teraz smerovany cez Tor.");
            startWatchdog();
        } catch (Throwable t) {
            Log.e(TAG, "start zlyhal", t);
            stopAll(); stopSelf();
        }
    }

    /** Ak Tor spadne pocas behu -> zablackholuj Messenger (fail-closed), nie leak. */
    private void startWatchdog() {
        Thread w = new Thread(() -> {
            while (running) {
                try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
                if (running && tor != null && !tor.isAlive()) {
                    Log.w(TAG, "Tor spadol -> fail-closed blackhole (Messenger bez siete)");
                    if (tproxyRunning) { try { tproxy.TProxyStopService(); } catch (Throwable ignored) {} tproxyRunning = false; }
                    ParcelFileDescriptor bh = buildTun(null); // route-all, bez hev = blackhole
                    if (bh != null) { ParcelFileDescriptor old = tun; tun = bh; try { if (old != null) old.close(); } catch (Exception ignored) {} }
                    READY = false;
                    running = false; // umozni restart pri dalsom otvoreni Messengera
                    return;
                }
            }
        }, "tor-watchdog");
        w.setDaemon(true); w.start();
    }

    /** Postavi tun pre Messenger. guards==null/prazdne => vsetky trasy (blackhole ked bez hev). */
    private ParcelFileDescriptor buildTun(List<Long> guards) {
        Builder b = new Builder();
        b.setSession("Spravaren");
        b.setMtu(MTU);
        b.addAddress(TUN4, 32);
        if (guards == null || guards.isEmpty()) {
            b.addRoute("0.0.0.0", 0);
        } else {
            for (RouteUtil.Route r : RouteUtil.allExcept(guards)) b.addRoute(r.address, r.prefix);
        }
        b.addAddress(TUN6, 128);
        b.addRoute("::", 0);
        b.addDnsServer(MAPDNS);
        try { b.addAllowedApplication(SELF_PKG); }
        catch (PackageManager.NameNotFoundException e) { Log.e(TAG, "self pkg?", e); }
        return b.establish();
    }

    private File writeHev() throws Exception {
        File f = new File(getFilesDir(), "hev.yml");
        String y =
            "tunnel:\n  mtu: " + MTU + "\n  ipv4: " + TUN4 + "\n  ipv6: '" + TUN6 + "'\n" +
            "socks5:\n  port: " + SOCKS + "\n  address: 127.0.0.1\n  udp: 'udp'\n" +
            "mapdns:\n  address: " + MAPDNS + "\n  port: 53\n  network: 100.64.0.0\n" +
            "  netmask: 255.192.0.0\n  cache-size: 10000\n" +
            "misc:\n  log-file: stderr\n  log-level: warn\n";
        try (OutputStream os = new FileOutputStream(f)) { os.write(y.getBytes(StandardCharsets.UTF_8)); }
        return f;
    }

    private void stopAll() {
        running = false; READY = false;
        if (tproxyRunning) { try { tproxy.TProxyStopService(); } catch (Throwable ignored) {} tproxyRunning = false; }
        if (tor != null) { try { tor.stop(); } catch (Throwable ignored) {} tor = null; }
        if (tun != null) { try { tun.close(); } catch (Exception ignored) {} tun = null; }
        try { stopForeground(true); } catch (Throwable ignored) {}
    }

    @Override public void onDestroy() { stopAll(); super.onDestroy(); }
    @Override public void onRevoke() { stopAll(); stopSelf(); super.onRevoke(); }

    private Notification notif() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Spravaren", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return nb.setContentTitle("Spravaren")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build();
    }
}
