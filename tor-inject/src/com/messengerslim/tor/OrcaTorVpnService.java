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
 *  - tunel sa postavi HNED pri starte procesu (TorBootProvider na neho pocka), takze
 *    Messenger nema siet skor, nez existuje tunel - ziadne okno na starte.
 *  - siet sa NIKDY neotvara. Ked bootstrap vazne, priebezne sa z tunela vynimaju len tie
 *    IP, na ktore sa Tor prave pokusa spojit (dir authorities + guardy).
 *  - na pozadi sa Tor vypina kvoli baterke, ale tunel ostava ako blackhole.
 *  - watchdog: ak Tor spadne pocas behu, Messenger sa zablackholuje (fail-closed), nie leak.
 */
public class OrcaTorVpnService extends VpnService {

    private static final String TAG = "OrcaTor";
    private static final String SELF_PKG = "com.facebook.orca";
    public static final String ACTION_START = "com.messengerslim.tor.START";
    public static final String ACTION_STOP  = "com.messengerslim.tor.STOP";
    public static final String ACTION_SLEEP = "com.messengerslim.tor.SLEEP";
    private static final String CHANNEL_ID = "orca_tor";
    private static final int NOTIF_ID = 90210;

    private static final int SOCKS = 9050, DNSP = 5400, MTU = 8500;
    private static final String TUN4 = "198.18.0.1", TUN6 = "fc00::1", MAPDNS = "198.18.0.2";

    public static volatile boolean READY = false;

    /** Bootstrap sa nikdy nevzda otvorenim siete; po tomto case ostava blackhole. */
    private static final long BOOTSTRAP_TIMEOUT_MS = 180_000;

    /** Strop vynimiek v tuneli (~27 trass na jednu IP). */
    private static final int MAX_EXCLUSIONS = 24;

    /**
     * Odpocitane, ked tunel stoji (alebo sa ho postavit nepodarilo). TorBootProvider na to
     * caka pri starte procesu, takze Messenger nema sancu poslat paket pred tunelom.
     */
    private static final java.util.concurrent.CountDownLatch FAILCLOSED =
            new java.util.concurrent.CountDownLatch(1);

    /** Pocka, kym je tunel hore. Vrati true ak stoji. */
    public static boolean awaitFailClosed(long ms) {
        try { return FAILCLOSED.await(ms, java.util.concurrent.TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }

    /**
     * Sluzba bezi vo vlastnom procese (:tor), takze latch je hlavnemu procesu na nic.
     * Namiesto neho marker subor: existuje = tunel stoji = appka sa von priamo nedostane.
     */
    public static File marker(android.content.Context ctx) {
        return new File(ctx.getFilesDir(), "tunnel_up");
    }

    private void markTunnelUp() {
        try { marker(this).createNewFile(); } catch (Throwable ignored) {}
    }

    private void markTunnelDown() {
        try { marker(this).delete(); } catch (Throwable ignored) {}
    }

    private ParcelFileDescriptor tun;
    private TorController tor;
    private final TProxyService tproxy = new TProxyService();
    private volatile boolean tproxyRunning = false, running = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAll(); stopSelf(); return START_NOT_STICKY;
        }
        if (intent != null && ACTION_SLEEP.equals(intent.getAction())) {
            Log.i(TAG, "Messenger v pozadi -> Tor vypnuty, tunel ostava blackhole (fail-closed)");
            blackhole();
            return START_STICKY;
        }
        if (tun != null) markTunnelUp();   // uz stoji z predchadzajuceho behu
        if (!running) { running = true; new Thread(this::startAll, "orca-tor-start").start(); }
        return START_STICKY;
    }

    private void startAll() {
        java.util.LinkedHashSet<Long> excl = new java.util.LinkedHashSet<>();
        try {
            startForeground(NOTIF_ID, notif());
            tor = new TorController(this, SOCKS, DNSP);

            // 1) FAIL-CLOSED HNED: tunel drzi Messenger bez siete este skor, nez sa Tor pusti.
            //    Vynate su len dir authorities + minule guardy - adresy, na ktore Messenger
            //    nikdy nechodi, takze cez ne nic neunikne.
            excl.addAll(TorController.dirAuthIps());
            excl.addAll(tor.loadGuards());
            tun = buildTun(new java.util.ArrayList<>(excl));
            if (tun == null) { Log.e(TAG, "fail-closed establish=null"); stopAll(); stopSelf(); return; }
            markTunnelUp();
            Log.i(TAG, "Fail-closed pri starte (vynimiek=" + excl.size() + ")");
        } catch (Throwable t) {
            Log.e(TAG, "fail-closed start zlyhal", t); stopAll(); stopSelf(); return;
        } finally {
            FAILCLOSED.countDown();   // appka moze pokracovat v starte
        }

        try {
            // 2) Spusti Tor.
            tor.startAsync();

            // 3) Cakaj na bootstrap. Siet sa NIKDY neotvara - namiesto toho priebezne
            //    vynimame z tunela presne tie IP, na ktore sa Tor prave pokusa spojit.
            long deadline = System.currentTimeMillis() + BOOTSTRAP_TIMEOUT_MS;
            while (!tor.bootstrapped) {
                if (tor.died) { Log.e(TAG, "Tor spadol pocas bootstrapu -> ostava blackhole"); blackhole(); return; }
                if (System.currentTimeMillis() > deadline) { Log.e(TAG, "Bootstrap timeout -> ostava blackhole"); blackhole(); return; }
                boolean changed = mergeExclusions(excl, tor.discoveredPeerIps());
                changed |= mergeExclusions(excl, tor.guardIps());
                if (changed) rebuildTun(excl);
                Thread.sleep(750);
            }

            // 4) Zisti aktualne guardy a uloz ich pre buduci fail-closed start.
            List<Long> guards = tor.guardIps();
            for (int i = 0; i < 8 && guards.isEmpty(); i++) { Thread.sleep(500); guards = tor.guardIps(); }
            Log.i(TAG, "Guard IP na vynechanie z tunela: " + guards.size());
            if (!guards.isEmpty()) tor.saveGuards(guards);

            // 5) Finalny tun: uz len dir authorities + aktualne guardy (minimalna mnozina).
            excl.clear();
            excl.addAll(TorController.dirAuthIps());
            excl.addAll(guards);
            if (!rebuildTun(excl)) { Log.e(TAG, "final establish=null"); stopAll(); stopSelf(); return; }

            // 6) hev: pumpuj tun <-> Tor SOCKS.
            File cfg = writeHev();
            tproxy.TProxyStartService(cfg.getAbsolutePath(), tun.getFd());
            tproxyRunning = true;

            READY = true;
            Log.i(TAG, "Messenger je teraz smerovany cez Tor.");
            startWatchdog();
        } catch (Throwable t) {
            Log.e(TAG, "start zlyhal", t);
            blackhole();
        }
    }

    /**
     * Prida nove IP do mnoziny vynimiek; true ak sa nieco pridalo. Mnozina je zastropovana -
     * kazda vynimka stoji ~27 trass v tuneli, takze pri prekroceni vyhadzujeme najstarsie
     * objavene IP (dir authorities sa nikdy nevyhadzuju).
     */
    private boolean mergeExclusions(java.util.Set<Long> excl, List<Long> add) {
        boolean changed = false;
        for (long ip : add) if (excl.add(ip)) changed = true;
        if (excl.size() > MAX_EXCLUSIONS) {
            java.util.List<Long> keep = TorController.dirAuthIps();
            java.util.Iterator<Long> it = excl.iterator();
            while (excl.size() > MAX_EXCLUSIONS && it.hasNext()) {
                Long ip = it.next();
                if (!keep.contains(ip)) it.remove();
            }
        }
        return changed;
    }

    /** Atomicky vymeni tun za novy s aktualnymi vynimkami. */
    private boolean rebuildTun(java.util.Set<Long> excl) {
        ParcelFileDescriptor n = buildTun(new java.util.ArrayList<>(excl));
        if (n == null) return false;
        ParcelFileDescriptor old = tun; tun = n;
        try { if (old != null) old.close(); } catch (Exception ignored) {}
        Log.i(TAG, "Tunel prestaveny (vynimiek=" + excl.size() + ")");
        return true;
    }

    /** Fail-closed: Tor stoji, tunel ostava bez hev = Messenger bez siete. Nikdy nie priamo von. */
    private void blackhole() {
        READY = false;
        if (tproxyRunning) { try { tproxy.TProxyStopService(); } catch (Throwable ignored) {} tproxyRunning = false; }
        if (tor != null) { try { tor.stop(); } catch (Throwable ignored) {} }
        ParcelFileDescriptor n = buildTun(null);   // route-all, bez hev
        if (n != null) {
            ParcelFileDescriptor old = tun; tun = n;
            try { if (old != null) old.close(); } catch (Exception ignored) {}
        }
        running = false;   // dalsie otvorenie Messengera Tor znova nastartuje
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
        markTunnelDown();
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
