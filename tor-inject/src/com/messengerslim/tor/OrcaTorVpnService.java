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
 * Slucku (Torov vlastny traffic) rozbija tym, ze IP guard uzlov vynecha z trass tunela.
 *
 *   Messenger -> tun (0.0.0.0/0 OKREM guard IP) -> hev -> Tor SOCKS 127.0.0.1:9050 -> guard(priamo) -> exit
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
            startForeground(NOTIF_ID, notif("Spustam Tor..."));

            tor = new TorController(this, SOCKS, DNSP);
            tor.startBlocking();

            // Zisti guard IP (par pokusov, kym Tor otvori OR spojenie).
            List<Long> guards = tor.guardIps();
            for (int i = 0; i < 8 && guards.isEmpty(); i++) {
                Thread.sleep(500); guards = tor.guardIps();
            }
            Log.i(TAG, "Guard IP na vynechanie z tunela: " + guards.size());

            List<RouteUtil.Route> routes = RouteUtil.allExcept(guards);

            Builder b = new Builder();
            b.setSession("Messenger Tor");
            b.setMtu(MTU);
            b.addAddress(TUN4, 32);
            for (RouteUtil.Route r : routes) b.addRoute(r.address, r.prefix);
            b.addAddress(TUN6, 128);
            b.addRoute("::", 0);
            b.addDnsServer(MAPDNS);
            try { b.addAllowedApplication(SELF_PKG); }
            catch (PackageManager.NameNotFoundException e) { Log.e(TAG, "self pkg?", e); }

            tun = b.establish();
            if (tun == null) { Log.e(TAG, "establish=null"); stopAll(); stopSelf(); return; }

            File cfg = writeHev();
            tproxy.TProxyStartService(cfg.getAbsolutePath(), tun.getFd());
            tproxyRunning = true;

            READY = true;
            updateNotif("Messenger ide cez Tor");
            Log.i(TAG, "Messenger je teraz smerovany cez Tor.");
        } catch (Throwable t) {
            Log.e(TAG, "start zlyhal", t);
            stopAll(); stopSelf();
        }
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

    private Notification notif(String txt) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Tor", NotificationManager.IMPORTANCE_LOW));
        Notification.Builder nb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return nb.setContentTitle("Messenger cez Tor").setContentText(txt)
                .setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build();
    }
    private void updateNotif(String t) { getSystemService(NotificationManager.class).notify(NOTIF_ID, notif(t)); }
}
