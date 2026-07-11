package com.messengerslim.tor;

import java.util.ArrayList;
import java.util.List;

/**
 * Vypocet IPv4 tras, ktore pokryju 0.0.0.0/0 OKREM zadanych IP (Torove guard uzly).
 * Vdaka tomu Torov vlastny traffic na guard ide priamo (mimo tunela) a slucka nevznikne.
 */
public final class RouteUtil {

    private RouteUtil() {}

    public static final class Route {
        public final String address; public final int prefix;
        Route(String a, int p) { address = a; prefix = p; }
    }

    /** long (unsigned 32b) IP z retazca "a.b.c.d". */
    public static long parse(String ip) {
        String[] p = ip.split("\\.");
        return ((Long.parseLong(p[0]) & 255) << 24)
             | ((Long.parseLong(p[1]) & 255) << 16)
             | ((Long.parseLong(p[2]) & 255) << 8)
             |  (Long.parseLong(p[3]) & 255);
    }

    private static String toStr(long ip) {
        return ((ip >> 24) & 255) + "." + ((ip >> 16) & 255) + "."
             + ((ip >> 8) & 255) + "." + (ip & 255);
    }

    /**
     * Rekurzivne rozdel 0.0.0.0/0 tak, aby vysledok pokryl vsetko okrem 'excluded' /32.
     */
    public static List<Route> allExcept(List<Long> excluded) {
        List<Route> out = new ArrayList<>();
        split(0L, 0, excluded, out);
        return out;
    }

    private static void split(long base, int prefix, List<Long> excluded, List<Route> out) {
        // Su v tomto bloku nejake vylucene IP?
        long size = prefix == 0 ? (1L << 32) : (1L << (32 - prefix));
        long end = base + size - 1;
        boolean containsAny = false;
        boolean isExactlyExcluded = false;
        for (long ex : excluded) {
            if (ex >= base && ex <= end) {
                containsAny = true;
                if (prefix == 32 && ex == base) isExactlyExcluded = true;
            }
        }
        if (!containsAny) { out.add(new Route(toStr(base), prefix)); return; }
        if (prefix == 32) { if (!isExactlyExcluded) out.add(new Route(toStr(base), prefix)); return; }
        // Rozdel na dve poloviny.
        int childPrefix = prefix + 1;
        long half = 1L << (32 - childPrefix);
        split(base, childPrefix, excluded, out);
        split(base + half, childPrefix, excluded, out);
    }
}
