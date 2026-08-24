package com.messengerslim.tor;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Spusti bundlovanu Tor binarku (libTor.so), sleduje bootstrap a zisti IP guard uzlov
 * cez ControlPort. Guardy sa vynechaju z tunela, aby sa Torov vlastny traffic nezacyklil.
 *
 * Bootstrap bezi asynchronne (percenta v bootstrapPercent), aby VpnService vedel medzitym
 * fail-closed drzat / pustit tunel.
 */
public class TorController {

    private static final String TAG = "OrcaTor";
    private final Context ctx;
    private final int socksPort, dnsPort;
    private final int ctrlPort = 9051;
    private Process torProcess;

    /**
     * IPv4 Torovych directory authorities (Tor ich ma hardcoded). Vynimame ich z tunela, aby
     * Tor vedel stiahnut consensus aj ked este nema ziadny guard. Je to len pomocka pri
     * bootstrape - ak sa niektora zmeni, dynamicke vynimanie (discoveredPeerIps) to dobehne.
     */
    private static final String[] DIRAUTH_IPS = {
            "128.31.0.39",   // moria1
            "86.59.21.38",   // tor26
            "45.66.35.11",   // dizum
            "131.188.40.189",// gabelmoo
            "193.23.244.244",// dannenberg
            "171.25.193.9",  // maatuska
            "199.58.81.140", // longclaw
            "204.13.164.118",// bastet
            "216.218.219.41",// faravahar
            "66.111.2.131",  // Serge
    };

    /** IP, na ktore sa Tor pocas behu pokusal spojit (vytiahnute z jeho logu). */
    private final java.util.Set<Long> peerIps =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<Long>());

    private static final java.util.regex.Pattern PEER_RX =
            java.util.regex.Pattern.compile(" at (\\d{1,3}(?:\\.\\d{1,3}){3})");

    public volatile int bootstrapPercent = 0;
    public volatile boolean bootstrapped = false;
    public volatile boolean died = false;

    public TorController(Context ctx, int socksPort, int dnsPort) {
        this.ctx = ctx.getApplicationContext();
        this.socksPort = socksPort;
        this.dnsPort = dnsPort;
    }

    private String torBinaryPath() {
        return new File(ctx.getApplicationInfo().nativeLibraryDir, "libTor.so").getAbsolutePath();
    }

    private File dataDir() { File d = new File(ctx.getFilesDir(), "tor"); d.mkdirs(); return d; }
    private File pidFile() { return new File(dataDir(), "tor.pid"); }
    private File guardCache() { return new File(dataDir(), "guards.txt"); }

    private File writeTorrc() throws Exception {
        File torrc = new File(dataDir(), "torrc");
        String cfg =
                "DataDirectory " + dataDir().getAbsolutePath() + "\n" +
                "PidFile " + pidFile().getAbsolutePath() + "\n" +
                "SocksPort 127.0.0.1:" + socksPort + "\n" +
                "DNSPort 127.0.0.1:" + dnsPort + "\n" +
                "ControlPort 127.0.0.1:" + ctrlPort + "\n" +
                "CookieAuthentication 1\n" +
                "ClientUseIPv6 0\n" +
                "ClientPreferIPv6ORPort 0\n" +
                "AvoidDiskWrites 1\n" +
                "ClientOnly 1\n" +
                // bootstrap len cez hardcoded dir authorities -> mnozina IP, ktore
                // treba vynat z tunela, je mala a znama (pozri DIRAUTH_IPS)
                "UseDefaultFallbackDirs 0\n" +
                "Log notice stdout\n";
        try (OutputStream os = new FileOutputStream(torrc)) {
            os.write(cfg.getBytes(StandardCharsets.UTF_8));
        }
        return torrc;
    }

    /** Spusti Tor a citaj jeho log na pozadi; aktualizuje bootstrapPercent/bootstrapped/died. */
    public void startAsync() throws Exception {
        killStaleTor();
        File torrc = writeTorrc();
        ProcessBuilder pb = new ProcessBuilder(torBinaryPath(), "-f", torrc.getAbsolutePath());
        pb.redirectErrorStream(true);
        pb.directory(ctx.getFilesDir());
        torProcess = pb.start();

        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    torProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    Log.i(TAG, line);
                    java.util.regex.Matcher pm = PEER_RX.matcher(line);
                    while (pm.find()) {
                        try {
                            long ip = RouteUtil.parse(pm.group(1));
                            if (isRoutablePeer(ip)) peerIps.add(ip);
                        } catch (Exception ignored) {}
                    }
                    int idx = line.indexOf("Bootstrapped ");
                    if (idx >= 0) {
                        try {
                            String rest = line.substring(idx + 13);
                            int pct = Integer.parseInt(rest.substring(0, rest.indexOf('%')).trim());
                            bootstrapPercent = pct;
                            if (pct >= 100) bootstrapped = true;
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
            died = true;
        }, "tor-log");
        t.setDaemon(true);
        t.start();
    }

    /** Caka na bootstrap 100% do timeoutMs. Vrati true ak uspech. */
    public boolean waitBootstrapped(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (bootstrapped) return true;
            if (died) return false;
            try { Thread.sleep(200); } catch (InterruptedException e) { return false; }
        }
        return bootstrapped;
    }

    /** Odfiltruj loopback, privatne rozsahy a nase tun adresy - tie do vynimiek nepatria. */
    private static boolean isRoutablePeer(long ip) {
        long a = (ip >> 24) & 255, b = (ip >> 16) & 255;
        if (a == 0 || a == 127 || a == 10) return false;
        if (a == 172 && b >= 16 && b <= 31) return false;
        if (a == 192 && b == 168) return false;
        if (a == 198 && (b == 18 || b == 19)) return false;
        if (a >= 224) return false;
        return true;
    }

    /** IP dir authorities - staticka pomocka pre fail-closed bootstrap. */
    public static List<Long> dirAuthIps() {
        List<Long> res = new ArrayList<>();
        for (String s : DIRAUTH_IPS) {
            try { res.add(RouteUtil.parse(s)); } catch (Exception ignored) {}
        }
        return res;
    }

    /** IP, na ktore sa Tor pokusal spojit (z logu) - dopĺňa sa pocas bootstrapu. */
    public List<Long> discoveredPeerIps() {
        synchronized (peerIps) { return new ArrayList<>(peerIps); }
    }

    public boolean isAlive() { return torProcess != null && torProcess.isAlive() && !died; }

    /** IP guard uzlov cez ControlPort (orconn-status -> ns/id/<fp>). */
    public List<Long> guardIps() {
        List<Long> res = new ArrayList<>();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", ctrlPort), 3000);
            s.setSoTimeout(5000);
            OutputStream o = s.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    s.getInputStream(), StandardCharsets.ISO_8859_1));

            byte[] cookie = readFile(new File(dataDir(), "control_auth_cookie"));
            send(o, "AUTHENTICATE " + hex(cookie) + "\r\n");
            String r = in.readLine();
            if (r == null || !r.startsWith("250")) { Log.w(TAG, "ctrl auth: " + r); return res; }

            List<String> fps = new ArrayList<>();
            send(o, "GETINFO orconn-status\r\n");
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(".")) break;
                int i = line.indexOf('$');
                if (i >= 0 && line.length() >= i + 41) fps.add(line.substring(i + 1, i + 41));
            }
            in.readLine();

            for (String fp : fps) {
                send(o, "GETINFO ns/id/$" + fp + "\r\n");
                while ((line = in.readLine()) != null) {
                    if (line.equals(".")) break;
                    if (line.startsWith("r ")) {
                        String[] p = line.split(" ");
                        if (p.length >= 7) {
                            try { long ip = RouteUtil.parse(p[6]); if (!res.contains(ip)) res.add(ip); }
                            catch (Exception ignored) {}
                        }
                    }
                }
                in.readLine();
            }
            send(o, "QUIT\r\n");
        } catch (Throwable t) { Log.w(TAG, "guardIps ctrl", t); }
        return res;
    }

    /** Ulozi guardy pre buduci fail-closed start. */
    public void saveGuards(List<Long> guards) {
        try (OutputStream os = new FileOutputStream(guardCache())) {
            StringBuilder sb = new StringBuilder();
            for (long g : guards) sb.append(g).append("\n");
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { Log.w(TAG, "saveGuards", e); }
    }

    /** Nacita minule guardy (na okamzity fail-closed pri starte). */
    public List<Long> loadGuards() {
        List<Long> res = new ArrayList<>();
        File f = guardCache();
        if (!f.exists()) return res;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String l;
            while ((l = br.readLine()) != null) {
                l = l.trim(); if (l.isEmpty()) continue;
                try { res.add(Long.parseLong(l)); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return res;
    }

    private void send(OutputStream o, String s) throws Exception {
        o.write(s.getBytes(StandardCharsets.ISO_8859_1)); o.flush();
    }

    private byte[] readFile(File f) throws Exception {
        byte[] buf = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int n = 0; while (n < buf.length) { int k = fis.read(buf, n, buf.length - n); if (k < 0) break; n += k; }
        }
        return buf;
    }

    private String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 255));
        return sb.toString();
    }

    private int readPid() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(pidFile()), StandardCharsets.UTF_8))) {
            String s = br.readLine();
            return s == null ? -1 : Integer.parseInt(s.trim());
        } catch (Throwable t) { return -1; }
    }

    public void stop() {
        if (torProcess != null) { torProcess.destroy(); torProcess = null; }
    }

    private void killStaleTor() {
        int pid = readPid();
        if (pid > 0) {
            try { android.os.Process.killProcess(pid); Log.i(TAG, "Zabity stary tor pid=" + pid); }
            catch (Throwable ignored) {}
        }
        pidFile().delete();
    }
}
