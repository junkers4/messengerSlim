package com.messengerslim.tor;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Stav Tor bootstrapu zdielany medzi procesmi. VpnService bezi vo vlastnom procese (:tor),
 * UI v hlavnom - staticke pole je preto na nic. Subor v filesDir vidia obe (rovnake UID).
 *
 * Obsah: "READY", "DOWN" alebo percenta bootstrapu ("0".."99").
 */
public final class TorStatus {

    public static final String READY = "READY";
    public static final String DOWN = "DOWN";

    private TorStatus() {}

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "tor_status");
    }

    public static void write(Context ctx, String value) {
        try (FileOutputStream os = new FileOutputStream(file(ctx))) {
            os.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignore) {}
    }

    public static String read(Context ctx) {
        try (FileInputStream in = new FileInputStream(file(ctx))) {
            byte[] b = new byte[16];
            int n = in.read(b);
            if (n <= 0) return DOWN;
            return new String(b, 0, n, StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return DOWN;
        }
    }

    /** Percenta bootstrapu, alebo -1 ak stav nie su percenta. */
    public static int percent(String status) {
        try { return Integer.parseInt(status); } catch (Throwable t) { return -1; }
    }
}
