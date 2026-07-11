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
 * Spusti bundlovanu Tor binarku (libTor.so), caka na bootstrap 100% a potom zisti
 * IP guard uzlov (Torove priame OR spojenia) cez ControlPort — tie sa vynechaju z tunela,
 * aby sa Torov vlastny traffic nezacyklil.
 *
 * ClientUseIPv6 0 => guardy su IPv4, takze IPv6 sa da bezpecne cele routovat do tunela.
 */
public class TorController {

    private static final String TAG = "OrcaTor";
    private final Context ctx;
    private final int socksPort, dnsPort;
    private final int ctrlPort = 9051;
    private Process torProcess;

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
                "Log notice stdout\n";
        try (OutputStream os = new FileOutputStream(torrc)) {
            os.write(cfg.getBytes(StandardCharsets.UTF_8));
        }
        return torrc;
    }

    public void startBlocking() throws Exception {
        killStaleTor();
        File torrc = writeTorrc();
        ProcessBuilder pb = new ProcessBuilder(torBinaryPath(), "-f", torrc.getAbsolutePath());
        pb.redirectErrorStream(true);
        pb.directory(ctx.getFilesDir());
        torProcess = pb.start();

        InputStream in = torProcess.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        long deadline = System.currentTimeMillis() + 120_000;
        while ((line = r.readLine()) != null) {
            Log.i(TAG, line);
            if (line.contains("Bootstrapped 100%")) { drainAsync(r); return; }
            if (System.currentTimeMillis() > deadline)
                throw new IllegalStateException("Tor bootstrap timeout");
        }
        throw new IllegalStateException("Tor skoncil pred bootstrapom");
    }

    private void drainAsync(BufferedReader r) {
        Thread t = new Thread(() -> {
            try { String l; while ((l = r.readLine()) != null) Log.d(TAG, l); } catch (Exception ignored) {}
        }, "tor-log");
        t.setDaemon(true); t.start();
    }

    /**
     * IP guard uzlov cez Tor ControlPort (spolahlive; /proc ineho procesu blokuje hidepid).
     * orconn-status -> fingerprinty otvorenych OR spojeni; ns/id/<fp> -> ich IP.
     */
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

            // fingerprinty otvorenych OR spojeni
            List<String> fps = new ArrayList<>();
            send(o, "GETINFO orconn-status\r\n");
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(".")) break;
                int i = line.indexOf('$');
                if (i >= 0 && line.length() >= i + 41) fps.add(line.substring(i + 1, i + 41));
            }
            in.readLine(); // "250 OK"

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
                in.readLine(); // "250 OK"
            }
            send(o, "QUIT\r\n");
        } catch (Throwable t) { Log.w(TAG, "guardIps ctrl", t); }
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
