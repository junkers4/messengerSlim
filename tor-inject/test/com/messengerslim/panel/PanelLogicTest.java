package com.messengerslim.panel;

import java.util.Calendar;

/**
 * Test cistej logiky panelu na PC (bez telefonu): java -cp cls:test-cls:android.jar ...PanelLogicTest
 * Spusta tor-inject/test/run.sh.
 */
public final class PanelLogicTest {
    private static int fails = 0;

    private static void eq(String what, Object want, Object got) {
        boolean ok = want == null ? got == null : want.equals(got);
        System.out.println((ok ? "OK   " : "FAIL ") + what + (ok ? "" : "  chcel " + want + ", je " + got));
        if (!ok) fails++;
    }

    private static Calendar day(int y, int m, int d) {
        Calendar c = Calendar.getInstance(); c.clear(); c.set(y, m - 1, d, 12, 0); return c;
    }

    public static void main(String[] a) {
        // datum narodenin
        eq("parse 24.10.", "24/10/0", fmt(Birthdays.parseDate("24.10.")));
        eq("parse 24. 10. 1990", "24/10/1990", fmt(Birthdays.parseDate("24. 10. 1990")));
        eq("parse 29.2.", "29/2/0", fmt(Birthdays.parseDate("29.2.")));
        eq("parse 31.4. zle", null, fmt(Birthdays.parseDate("31.4.")));
        eq("parse 13.13. zle", null, fmt(Birthdays.parseDate("13.13.")));
        eq("parse text zle", null, fmt(Birthdays.parseDate("abc")));

        // dni do narodenin a vek
        Calendar t = day(2026, 10, 24);
        eq("dnes", 0, new Birthdays.Entry("A", 24, 10, 1990).daysUntil(t));
        eq("zajtra", 1, new Birthdays.Entry("A", 25, 10, 0).daysUntil(t));
        eq("vcera -> o rok", 364, new Birthdays.Entry("A", 23, 10, 0).daysUntil(t));
        eq("cez novy rok", 9, new Birthdays.Entry("A", 2, 1, 0).daysUntil(day(2026, 12, 24)));
        eq("vek dnes", 36, new Birthdays.Entry("A", 24, 10, 1990).ageAtNext(t));
        eq("vek bez roku", -1, new Birthdays.Entry("A", 24, 10, 0).ageAtNext(t));
        eq("29.2. v neprestupnom", 127, new Birthdays.Entry("A", 29, 2, 0).daysUntil(t));

        // Velka noc a sviatky
        eq("Velka noc 2026", "5.4", ed(2026));
        eq("Velka noc 2027", "28.3", ed(2027));
        eq("Velka noc 2025", "20.4", ed(2025));
        eq("Velky piatok 2026", "Veľký piatok", WeatherPanel.holidaySk(day(2026, 4, 3)));
        eq("Velkonocny pondelok 2026", "Veľkonočný pondelok", WeatherPanel.holidaySk(day(2026, 4, 6)));
        eq("SNP", "Výročie SNP", WeatherPanel.holidaySk(day(2026, 8, 29)));
        eq("bezny den", null, WeatherPanel.holidaySk(day(2026, 10, 24)));

        // meniny podla mena (SK, inak CZ) a riadok do panelu
        eq("meniny Miška (Michaela) = CZ 19.10", "10/19/1", fmt(WeatherPanel.namedayOf("Miška (Michaela)")));
        eq("meniny Kvetoslava = SK 24.10", "10/24/0", fmt(WeatherPanel.namedayOf("Kvetoslava")));
        eq("meniny bez diakritiky", "10/24/0", fmt(WeatherPanel.namedayOf("kvetoslava")));
        eq("nezname meno", null, fmt(WeatherPanel.namedayOf("Xyzzy")));
        java.util.List<Birthdays.Entry> l = new java.util.ArrayList<>();
        l.add(new Birthdays.Entry("Test", 24, 10, 1990));
        l.add(new Birthdays.Entry("Miška (Michaela)", 29, 1, 0));
        eq("riadok narodeniny + meniny", "🎂 Dnes Test (36)", Birthdays.line(l, day(2026, 10, 24)));
        eq("riadok meniny o 3 dni", "🌸 meniny o 3 dni Miška (Michaela)",
                Birthdays.line(l.subList(1, 2), day(2026, 10, 16)));
        eq("riadok nic", null, Birthdays.line(l.subList(1, 2), day(2026, 6, 1)));
        eq("kedy 4 dni", "o 4 dni", Birthdays.when(4));
        eq("kedy 5 dni", "o 5 dní", Birthdays.when(5));

        System.out.println(fails == 0 ? "VSETKO OK" : fails + " CHYB");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static String fmt(int[] d) { return d == null ? null : d[0] + "/" + d[1] + "/" + d[2]; }

    private static String ed(int y) {
        Calendar e = WeatherPanel.easterSunday(y);
        return e.get(Calendar.DAY_OF_MONTH) + "." + (e.get(Calendar.MONTH) + 1);
    }
}
