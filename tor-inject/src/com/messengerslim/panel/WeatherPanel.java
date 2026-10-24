package com.messengerslim.panel;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/**
 * Panel na miesto Stories pasu v inboxe: pocasie (Open-Meteo) + meniny/svatek/sviatky +
 * narodeniny (Birthdays) + spravy + overenie Toru (check.torproject.org).
 * Tuk na datum -> narodeniny, tuk na pocasie -> obnovit, podrzanie panelu -> novy Tor okruh.
 * Vola sa z X/2ml.A04 tesne pred vratenim tray (LX/1W0 = ViewGroup, super LX/50d).
 *
 * A04 je ZDIELANA factory pre KAZDY recycler v appke - zoznam sprav vo vlakne, inbox,
 * emoji mriezka, mriezka fotiek... Preto sa panel vlozi IBA ked tray naozaj JE Stories
 * pas: vodorovny RecyclerView, nizky, v hornej polovici obrazovky. Cokolvek ine ostava
 * nedotknute (predtym slaba podmienka "nizky tray" trafila aj emoji/foto tray a tie
 * ostali prazdne).
 */
public final class WeatherPanel {

    // Bucovice (CZ, Jihomoravsky kraj)
    private static final double LAT = 49.1497;
    private static final double LON = 17.0003;
    private static final String CITY = "Bučovice";

    private WeatherPanel() {}

    /** Vstupny bod zo smali (X/2ml.A04). tray je LX/1W0. */
    public static void install(final ViewGroup tray) {
        if (tray == null) return;
        decide(tray, 0);
    }

    // ---- rozpoznanie Stories pasu ------------------------------------------------

    /** Kolko krat (a ako dlho) cakame, kym sa tray usadi a dostane layout manager. */
    private static final int MAX_ATTEMPTS = 20;
    private static final long RETRY_MS = 100;

    private static void decide(final ViewGroup tray, final int attempt) {
        Boolean geo = geometry(tray);
        if (Boolean.FALSE.equals(geo)) return;          // urcite nie Stories pas

        if (geo != null) {
            // Litho nasadi vodorovny layout manager az ked pas primountuje - do vtedy hlasi
            // recycler zvisly. Preto na FALSE hned nekoncime, ale dame mu cas.
            if (Boolean.TRUE.equals(horizontal(tray))) { replaceWithPanel(tray); return; }
            if (attempt >= MAX_ATTEMPTS) {
                // cas vyprsal -> rozhodne geometria: nizky pas v hornej polovici okna.
                // Emoji/foto tray vyskakuju zospodu a dlhe zoznamy su vysoke, takze sem
                // dopadne uz len Stories pas.
                replaceWithPanel(tray);
                return;
            }
        }
        if (attempt < MAX_ATTEMPTS) {
            tray.postDelayed(new Runnable() {
                public void run() { decide(tray, attempt + 1); }
            }, RETRY_MS);
        }
    }

    /** Geometria: nizky pas v hornej polovici okna. null = este bez vysky. */
    private static Boolean geometry(ViewGroup tray) {
        int h = tray.getHeight();
        if (h <= 0) return null;
        DisplayMetrics dm = tray.getResources().getDisplayMetrics();
        float d = dm.density;
        if (h < 48 * d || h > 260 * d) return Boolean.FALSE;
        int[] loc = new int[2];
        tray.getLocationOnScreen(loc);
        if (loc[1] > dm.heightPixels / 2) return Boolean.FALSE;   // emoji/foto tray vyskakuju zospodu
        return Boolean.TRUE;
    }

    /** true/false podla orientacie vnutorneho RecyclerView, null kym nema layout manager. */
    private static Boolean horizontal(ViewGroup tray) {
        View rv = findRecycler(tray, 0);
        if (rv == null) return Boolean.FALSE;
        try {
            java.lang.reflect.Method get = rv.getClass().getMethod("getLayoutManager");
            get.setAccessible(true);
            Object lm = get.invoke(rv);
            if (lm == null) return null;       // este nie je nastaveny -> retry
            java.lang.reflect.Method mh = lm.getClass().getMethod("canScrollHorizontally");
            java.lang.reflect.Method mv = lm.getClass().getMethod("canScrollVertically");
            mh.setAccessible(true); mv.setAccessible(true);
            boolean ch = Boolean.TRUE.equals(mh.invoke(lm));
            boolean cv = Boolean.TRUE.equals(mv.invoke(lm));
            return Boolean.valueOf(ch && !cv);
        } catch (Throwable t) {
            return Boolean.FALSE;              // neznamy typ -> radsej nesiahat
        }
    }

    private static View findRecycler(View v, int depth) {
        if (v == null || depth > 4) return null;
        for (Class<?> c = v.getClass(); c != null; c = c.getSuperclass()) {
            if ("androidx.recyclerview.widget.RecyclerView".equals(c.getName())) return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findRecycler(g.getChildAt(i), depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static void replaceWithPanel(ViewGroup tray) {
        try {
            if (tray.findViewWithTag("swp_panel") != null) return;
            // po refreshi inboxu vyrobi Messenger novy Stories tray -> nechceme 2x panel.
            View root = tray.getRootView();
            if (root instanceof ViewGroup && ((ViewGroup) root).findViewWithTag("swp_panel") != null) {
                tray.setVisibility(View.GONE);
                return;
            }
            Context ctx = tray.getContext();
            for (int i = 0; i < tray.getChildCount(); i++) {
                View ch = tray.getChildAt(i);
                if (ch != null) ch.setVisibility(View.GONE);
            }
            View panel = build(ctx);
            panel.setTag("swp_panel");
            tray.addView(panel, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            Declutter.schedule(tray);
            // LX/50d (584: LX/36w) rozklada len public field A0N -> nasmerujeme ho na nas panel
            try { tray.getClass().getField("A0N").set(tray, panel); } catch (Throwable ignore) {}
        } catch (Throwable t) { /* ignore - povodny obsah ostava */ }
    }

    // ---- panel -------------------------------------------------------------------

    private static View build(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int px = (int) (14 * d);
        box.setPadding(px, (int) (7 * d), px, (int) (7 * d));
        box.setMinimumHeight((int) (104 * d));

        // 1. riadok: mesto + teplota + stav
        final TextView weather = line(ctx, 19, Color.WHITE, 0);
        weather.setText(CITY + " · …");

        // 2. riadok: pocit, max/min, vietor, dazd, vychod/zapad slnka
        final TextView detail = line(ctx, 11, 0xFF9AA0A6, (int) (2 * d));
        detail.setText("");

        // 3. riadok: datum, meniny (SK), svatek (CZ), faza mesiaca, sviatok, zajtra meniny
        final TextView dayline = line(ctx, 12, 0xFFB388FF, (int) (3 * d));
        dayline.setText(dateLine());

        // 4. info riadok - vzdy prave jeden (panel ma miesto na 5 riadkov):
        //    narodeniny, inak statny sviatok, inak zajtrajsie meniny
        final TextView bday = line(ctx, 12, 0xFFFFD54F, (int) (2 * d));
        bday.setSingleLine(true);
        bday.setEllipsize(android.text.TextUtils.TruncateAt.END);
        final Runnable refreshBday = new Runnable() {
            public void run() { bday.setText(infoLine(bday.getContext())); }
        };
        refreshBday.run();
        View.OnClickListener openBday = new View.OnClickListener() {
            public void onClick(View v) { Birthdays.showDialog(v.getContext(), refreshBday); }
        };
        dayline.setOnClickListener(openBday);
        bday.setOnClickListener(openBday);

        // Tor znacka za pocasim: 🧅✓ overene cez check.torproject.org, ⚠️ ak nie
        final TextView torMark = line(ctx, 15, Color.WHITE, 0);
        torMark.setPadding((int) (8 * d), 0, 0, 0);
        torMark.setText("🧅…");

        // 4. riadok: bezici pas - titulky + "v tento den" z historie
        final TextView ticker = line(ctx, 13, 0xFFBEBEBE, (int) (4 * d));
        ticker.setSingleLine(true);
        ticker.setHorizontallyScrolling(true);
        ticker.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        ticker.setMarqueeRepeatLimit(-1);
        ticker.setSelected(true);
        ticker.setText("📰 načítavam správy…");

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER);
        head.addView(weather);
        head.addView(torMark);
        box.addView(head);
        box.addView(detail);
        box.addView(dayline);
        box.addView(bday);
        box.addView(ticker);

        weather.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                weather.setText(CITY + " · obnovujem…");
                fetchWeatherAsync(weather, detail);
                fetchTickerAsync(ticker);
                fetchTorAsync(torMark, 0, false);
            }
        });
        box.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                newCircuit(v.getContext());
                torMark.setText("🧅…");
                android.widget.Toast.makeText(v.getContext(), "🧅 Nový Tor okruh…", android.widget.Toast.LENGTH_SHORT).show();
                fetchTorAsync(torMark, 4000, true);
                return true;
            }
        });

        fetchWeatherAsync(weather, detail);
        fetchTickerAsync(ticker);
        fetchTorAsync(torMark, 0, false);
        return box;
    }

    private static TextView line(Context ctx, int sp, int color, int padTop) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, padTop, 0, 0);
        return tv;
    }

    // ---- pocasie ----

    /** Pokusov o stiahnutie dat - pri starte appky Tor este len nabieha, siet ide az potom. */
    private static final int TRIES = 5;
    private static final long RETRY_DELAY_MS = 8000;

    private static void fetchWeatherAsync(final TextView head, final TextView detail) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < TRIES; i++) {
                    final String[] r = fetchWeather();
                    final boolean ok = r[1] != null;
                    main.post(new Runnable() {
                        public void run() {
                            try {
                                head.setText(r[0]);
                                if (r[1] != null) detail.setText(r[1]);
                            } catch (Throwable ignore) {}
                        }
                    });
                    if (ok) return;
                    sleep(RETRY_DELAY_MS);
                }
            }
        }, "swp-weather").start();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** [0] = hlavny riadok, [1] = detailny riadok (alebo null). */
    private static String[] fetchWeather() {
        HttpURLConnection c = null;
        try {
            String u = "https://api.open-meteo.com/v1/forecast?latitude=" + LAT
                    + "&longitude=" + LON
                    + "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m"
                    + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,"
                    + "sunrise,sunset&hourly=precipitation_probability&forecast_hours=4"
                    + "&timezone=auto&forecast_days=1";
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (c.getResponseCode() != 200) return new String[] { CITY + " · počasie nedostupné", null };
            JSONObject root = new JSONObject(readAll(c));

            JSONObject cur = root.getJSONObject("current");
            long temp = Math.round(cur.getDouble("temperature_2m"));
            int wc = cur.optInt("weather_code", -1);
            String headLine = CITY + " · " + temp + "°C " + wmo(wc);
            String alert = rainSoon(root.optJSONObject("hourly"));
            if (alert != null) headLine += "  ·  " + alert;

            StringBuilder sb = new StringBuilder();
            if (cur.has("apparent_temperature")) {
                sb.append("pocit ").append(Math.round(cur.getDouble("apparent_temperature"))).append("°");
            }
            JSONObject dly = root.optJSONObject("daily");
            if (dly != null) {
                Double hi = first(dly, "temperature_2m_max");
                Double lo = first(dly, "temperature_2m_min");
                if (hi != null && lo != null) {
                    sep(sb).append("↑").append(Math.round(hi.doubleValue())).append("° ↓")
                           .append(Math.round(lo.doubleValue())).append("°");
                }
                Double rain = first(dly, "precipitation_probability_max");
                if (rain != null) sep(sb).append("☔").append(Math.round(rain.doubleValue())).append("%");
            }
            if (cur.has("wind_speed_10m")) {
                sep(sb).append("💨").append(Math.round(cur.getDouble("wind_speed_10m"))).append(" km/h");
            }
            if (dly != null) {
                String sr = firstStr(dly, "sunrise"), ss = firstStr(dly, "sunset");
                if (sr != null) sep(sb).append("🌅").append(hhmm(sr));
                if (ss != null) sep(sb).append("🌇").append(hhmm(ss));
            }
            return new String[] { headLine, sb.length() > 0 ? sb.toString() : null };
        } catch (Throwable e) {
            return new String[] { CITY + " · počasie nedostupné", null };
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignore) {}
        }
    }

    /** "☔ o 2 h dážď" ak v najblizsich 3 h prsi s pravdepodobnostou >= 50 %, inak null. */
    static String rainSoon(JSONObject hourly) {
        if (hourly == null) return null;
        JSONArray p = hourly.optJSONArray("precipitation_probability");
        if (p == null) return null;
        // [0] = aktualna hodina; ak uz prsi, ukazuje to wmo() v hlavnom riadku
        for (int h = 1; h <= 3 && h < p.length(); h++) {
            if (!p.isNull(h) && p.optInt(h) >= 50) return "☔ o " + h + " h dážď";
        }
        return null;
    }

    private static StringBuilder sep(StringBuilder sb) {
        if (sb.length() > 0) sb.append(" · ");
        return sb;
    }

    private static Double first(JSONObject o, String key) {
        JSONArray a = o.optJSONArray(key);
        if (a == null || a.length() == 0 || a.isNull(0)) return null;
        return Double.valueOf(a.optDouble(0));
    }

    private static String firstStr(JSONObject o, String key) {
        JSONArray a = o.optJSONArray(key);
        if (a == null || a.length() == 0) return null;
        return a.optString(0, null);
    }

    /** "2026-09-03T06:11" -> "6:11" */
    private static String hhmm(String iso) {
        int t = iso.indexOf('T');
        if (t < 0 || iso.length() < t + 6) return iso;
        String s = iso.substring(t + 1, t + 6);
        return s.startsWith("0") ? s.substring(1) : s;
    }

    private static String readAll(HttpURLConnection c) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    /** WMO weather code -> emoji + kratky SK popis. */
    private static String wmo(int code) {
        if (code < 0) return "";
        if (code == 0) return "☀️ jasno";
        if (code <= 2) return "🌤️ polojasno";
        if (code == 3) return "☁️ zamračené";
        if (code == 45 || code == 48) return "🌫️ hmla";
        if (code >= 51 && code <= 57) return "🌧️ mrholenie";
        if (code >= 61 && code <= 67) return "🌧️ dážď";
        if (code >= 71 && code <= 77) return "❄️ sneh";
        if (code >= 80 && code <= 82) return "🌦️ prehánky";
        if (code >= 85 && code <= 86) return "🌨️ snehové prehánky";
        if (code >= 95) return "⛈️ búrka";
        return "";
    }

    // ---- bezici pas: titulky + "v tento den" ----

    private static final String NEWS_RSS =
            "https://news.google.com/rss?hl=sk&gl=SK&ceid=SK:sk";

    private static void fetchTickerAsync(final TextView target) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                String news = null;
                for (int i = 0; i < TRIES && news == null; i++) {
                    news = fetchNews();
                    if (news == null) sleep(RETRY_DELAY_MS);
                }
                if (news != null) post(main, target, news);
                String hist = fetchHistory();
                if (hist == null) { sleep(RETRY_DELAY_MS); hist = fetchHistory(); }
                if (hist != null) post(main, target, (news != null ? news + "      •      " : "") + hist);
            }
        }, "swp-ticker").start();
    }

    private static void post(Handler main, final TextView t, final String s) {
        main.post(new Runnable() {
            public void run() {
                try { t.setText(s); t.setSelected(true); } catch (Throwable ignore) {}
            }
        });
    }

    private static String fetchNews() {
        try {
            String xml = get(NEWS_RSS);
            if (xml == null) return null;
            StringBuilder out = new StringBuilder("📰  ");
            int from = 0, taken = 0;
            while (taken < 6) {
                int it = xml.indexOf("<item>", from);
                if (it < 0) break;
                int ts = xml.indexOf("<title>", it);
                int te = xml.indexOf("</title>", ts);
                from = (te > 0) ? te : (it + 6);
                if (ts < 0 || te < 0) continue;
                String title = unescape(stripCdata(xml.substring(ts + 7, te).trim()));
                int dash = title.lastIndexOf(" - ");
                if (dash > 20) title = title.substring(0, dash);
                if (title.isEmpty()) continue;
                if (taken > 0) out.append("      •      ");
                out.append(title);
                taken++;
            }
            return (taken > 0) ? out.toString() : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Wikipedia "on this day" (cs) - co sa v tento den stalo. Bez API kluca. */
    private static String fetchHistory() {
        try {
            Calendar cal = Calendar.getInstance();
            String url = "https://cs.wikipedia.org/api/rest_v1/feed/onthisday/events/"
                    + (cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.DAY_OF_MONTH);
            String json = get(url);
            if (json == null) return null;
            JSONArray ev = new JSONObject(json).optJSONArray("events");
            if (ev == null || ev.length() == 0) return null;
            StringBuilder out = new StringBuilder("📜  V tento deň:  ");
            int taken = 0;
            for (int i = ev.length() - 1; i >= 0 && taken < 3; i--) {
                JSONObject e = ev.optJSONObject(i);
                if (e == null) continue;
                String text = e.optString("text", "").trim();
                if (text.length() < 8 || text.length() > 220) continue;
                if (taken > 0) out.append("      •      ");
                int year = e.optInt("year", 0);
                if (year != 0) out.append(year).append(" – ");
                out.append(text);
                taken++;
            }
            return taken > 0 ? out.toString() : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (c.getResponseCode() != 200) return null;
            return readAll(c);
        } catch (Throwable e) {
            return null;
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignore) {}
        }
    }

    private static String stripCdata(String s) {
        if (s.startsWith("<![CDATA[") && s.endsWith("]]>")) return s.substring(9, s.length() - 3);
        return s;
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&nbsp;", " ");
    }

    // ---- datum, meniny, svatek, faza mesiaca ----

    private static final String[] DAYS =
            { "Ne", "Po", "Ut", "St", "Št", "Pi", "So" };

    private static String dateLine() {
        Calendar cal = Calendar.getInstance();
        int m = cal.get(Calendar.MONTH), day = cal.get(Calendar.DAY_OF_MONTH);
        StringBuilder sb = new StringBuilder();
        sb.append(DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]).append(' ')
          .append(day).append(". ").append(m + 1).append(".");
        String sk = pick(MENINY, m, day);
        if (sk != null) sb.append("  ·  ").append(sk);
        String cz = pick(SVATKY, m, day);
        if (cz != null && !cz.equals(sk)) sb.append("  ·  CZ ").append(cz);
        sb.append("  ·  ").append(moon(cal));
        return sb.toString();
    }

    /** Info riadok: narodeniny > statny sviatok > zajtrajsie meniny. Tuk = narodeniny. */
    static String infoLine(Context ctx) {
        String b = Birthdays.line(ctx);
        if (b != null) return b;
        Calendar cal = Calendar.getInstance();
        String hol = holidaySk(cal);
        if (hol != null) return "🇸🇰 " + hol + "  ·  🎂 ťukni = narodeniny";
        Calendar tm = (Calendar) cal.clone();
        tm.add(Calendar.DAY_OF_MONTH, 1);
        String next = pick(MENINY, tm.get(Calendar.MONTH), tm.get(Calendar.DAY_OF_MONTH));
        return (next != null ? "zajtra má meniny " + next + "  ·  " : "") + "🎂 ťukni = narodeniny";
    }

    private static String pick(String[][] table, int month, int day) {
        try {
            String[] mm = table[month];
            if (day >= 1 && day <= mm.length && mm[day - 1].length() > 0) return mm[day - 1];
        } catch (Throwable ignore) {}
        return null;
    }

    /** Faza mesiaca zo synodickeho mesiaca (nov 2000-01-06 18:14 UTC). */
    private static String moon(Calendar cal) {
        double days = (cal.getTimeInMillis() - 947182440000L) / 86400000.0;
        double phase = days / 29.530588853;
        phase = phase - Math.floor(phase);
        int i = (int) Math.floor(phase * 8 + 0.5) % 8;
        switch (i) {
            case 0: return "🌑 nov";
            case 1: return "🌒 dorastá";
            case 2: return "🌓 prvá štvrť";
            case 3: return "🌔 dorastá";
            case 4: return "🌕 spln";
            case 5: return "🌖 cúva";
            case 6: return "🌗 posl. štvrť";
            default: return "🌘 cúva";
        }
    }

    /** Statne sviatky a dni pracovneho pokoja SR (vratane Velkej noci), inak null. */
    static String holidaySk(Calendar cal) {
        int m = cal.get(Calendar.MONTH) + 1, d = cal.get(Calendar.DAY_OF_MONTH);
        switch (m * 100 + d) {
            case 101: return "Deň vzniku SR";
            case 106: return "Zjavenie Pána";
            case 501: return "Sviatok práce";
            case 508: return "Deň víťazstva nad fašizmom";
            case 705: return "Sv. Cyril a Metod";
            case 829: return "Výročie SNP";
            case 901: return "Deň Ústavy SR";
            case 915: return "Sedembolestná Panna Mária";
            case 1101: return "Sviatok všetkých svätých";
            case 1117: return "Deň boja za slobodu a demokraciu";
            case 1224: return "Štedrý deň";
            case 1225: return "1. sviatok vianočný";
            case 1226: return "2. sviatok vianočný";
        }
        Calendar e = easterSunday(cal.get(Calendar.YEAR));
        Calendar fri = (Calendar) e.clone(); fri.add(Calendar.DAY_OF_MONTH, -2);
        Calendar mon = (Calendar) e.clone(); mon.add(Calendar.DAY_OF_MONTH, 1);
        if (sameDay(cal, fri)) return "Veľký piatok";
        if (sameDay(cal, e)) return "Veľkonočná nedeľa";
        if (sameDay(cal, mon)) return "Veľkonočný pondelok";
        return null;
    }

    /** Velkonocna nedela (gregoriansky kalendar, anonymny algoritmus). */
    static Calendar easterSunday(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25;
        int g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        Calendar r = Calendar.getInstance();
        r.clear();
        r.set(y, month - 1, day);
        return r;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    // ---- Tor: overenie cez check.torproject.org + novy okruh ----

    private static void fetchTorAsync(final TextView mark, final long delayMs, final boolean toast) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                sleep(delayMs);
                for (int i = 0; i < TRIES; i++) {
                    final String[] r = torCheck(get("https://check.torproject.org/api/ip"));
                    if (r != null) {
                        main.post(new Runnable() {
                            public void run() {
                                try {
                                    mark.setText(r[0]);
                                    if (toast || !"🧅✓".equals(r[0])) {
                                        android.widget.Toast.makeText(mark.getContext(), r[1],
                                                android.widget.Toast.LENGTH_LONG).show();
                                    }
                                } catch (Throwable ignore) {}
                            }
                        });
                        return;
                    }
                    sleep(RETRY_DELAY_MS);
                }
                post(main, mark, "🧅?");
            }
        }, "swp-tor").start();
    }

    /** {"IsTor":true,"IP":"1.2.3.4"} -> {znacka, sprava}, null pri chybe. */
    static String[] torCheck(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            String ip = o.optString("IP", "?");
            return o.optBoolean("IsTor", false)
                    ? new String[] { "🧅✓", "🧅 Cez Tor ✓  ·  exit " + ip }
                    : new String[] { "⚠️", "⚠️ Spojenie NEJDE cez Tor! (" + ip + ")" };
        } catch (Throwable t) {
            return null;
        }
    }

    /** Poziada VpnService v procese :tor o SIGNAL NEWNYM. */
    private static void newCircuit(Context ctx) {
        try {
            android.content.Intent i = new android.content.Intent("com.messengerslim.tor.NEWNYM");
            i.setClassName(ctx.getPackageName(), "com.messengerslim.tor.OrcaTorVpnService");
            ctx.startService(i);
        } catch (Throwable ignore) {}
    }

    private static java.util.Map<String, int[]> namedays;

    /** Kedy ma meniny niektore slovo z mena ("Miška (Michaela)" -> Michaela): {mesiac 1-12, den, 1=CZ}. */
    static synchronized int[] namedayOf(String fullName) {
        if (namedays == null) {
            namedays = new java.util.HashMap<>();
            index(SVATKY, 1);
            index(MENINY, 0);          // SK prepise CZ, ak je meno v oboch
        }
        for (String w : fullName.split("[^\\p{L}]+")) {
            int[] r = namedays.get(fold(w));
            if (r != null) return r;
        }
        return null;
    }

    private static void index(String[][] table, int cz) {
        for (int m = 0; m < table.length; m++)
            for (int d = 0; d < table[m].length; d++)
                for (String n : table[m][d].split("[,/]"))
                    if (n.trim().length() > 1) namedays.put(fold(n.trim()), new int[] { m + 1, d + 1, cz });
    }

    /** bez diakritiky a velkych pismen: "Miška" == "miska" */
    static String fold(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
    }

    public static String namedayToday() {
        Calendar cal = Calendar.getInstance();
        String n = pick(MENINY, cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        return n != null ? n : "—";
    }

    // Slovensky kalendar mien.
    private static final String[][] MENINY = {
        // Januar
        {"Nový rok","Alexandra","Daniela","Drahoslav","Andrea","Antónia","Bohuslava","Severín","Alexej","Dáša",
         "Malvína","Ernest","Rastislav","Radovan","Dobroslav","Kristína","Nataša","Bohdana","Drahomíra","Dalibor",
         "Vincent","Zora","Miloš","Timotej","Gejza","Tamara","Bohuš","Alfonz","Gašpar","Ema","Emil"},
        // Februar
        {"Tatiana","Erika","Blažej","Veronika","Agáta","Dorota","Vanda","Zoja","Zdenko","Gabriela",
         "Dezider","Perla","Arpád","Valentín","Pravoslav","Ida","Miloslava","Jaromír","Vlasta","Lívia",
         "Eleonóra","Etela","Roman","Matej","Frederik","Viktor","Alexander","Zlatica","Radomír"},
        // Marec
        {"Albín","Anežka","Bohumil","Kazimír","Fridrich","Radoslav","Tomáš","Alan","Františka","Branislav",
         "Angela","Gregor","Vlastimil","Matilda","Svetlana","Boleslav","Ľubica","Eduard","Jozef","Víťazoslav",
         "Blahoslav","Beňadik","Adrián","Gabriel","Marián","Emanuel","Alena","Soňa","Miroslav","Vieroslava","Benjamín"},
        // April
        {"Hugo","Zita","Richard","Izidor","Miroslava","Irena","Zoltán","Albert","Milena","Igor",
         "Július","Estera","Aleš","Justína","Fedor","Dana","Rudolf","Valér","Jela","Marcel",
         "Ervín","Slavomír","Vojtech","Juraj","Marek","Jaroslava","Jaroslav","Jarmila","Lea","Anastázia"},
        // Maj
        {"Sviatok práce","Žigmund","Galina","Florián","Lesana","Hermína","Monika","Ingrida","Roland","Viktória",
         "Blažena","Pankrác","Servác","Bonifác","Žofia","Svetozár","Gizela","Viola","Gertrúda","Bernard",
         "Zina","Júlia","Želmíra","Ela","Urban","Dušan","Iveta","Viliam","Vilma","Ferdinand","Petronela"},
        // Jun
        {"Žaneta","Xénia","Karolína","Lenka","Laura","Norbert","Róbert","Medard","Stanislava","Margaréta",
         "Dobroslava","Zlatko","Anton","Vasil","Vít","Blanka","Adolf","Vratislav","Alfréd","Valéria",
         "Alojz","Paulína","Sidónia","Ján","Tadeáš","Adriána","Ladislav","Beáta","Peter a Pavol","Melánia"},
        // Jul
        {"Diana","Berta","Miloslav","Prokop","Cyril a Metod","Patrik","Oliver","Ivan","Lujza","Amália",
         "Milota","Nína","Margita","Kamil","Henrich","Drahomír","Bohuslav","Kamila","Dušana","Eliáš",
         "Daniel","Magdaléna","Oľga","Vladimír","Jakub","Anna","Božena","Krištof","Marta","Libuša","Ignác"},
        // August
        {"Božidara","Gustáv","Jerguš","Dominika","Hortenzia","Jozefína","Štefánia","Oskar","Ľubomíra","Vavrinec",
         "Zuzana","Darina","Ľubomír","Mojmír","Marcela","Leonard","Milica","Elena","Lýdia","Anabela",
         "Jana","Tichomír","Filip","Bartolomej","Ľudovít","Samuel","Silvia","Augustín","Nikola","Ružena","Nora"},
        // September
        {"Drahoslava","Linda","Belo","Rozália","Regina","Alica","Marianna","Miriam","Martina","Oleg",
         "Bystrík","Mária","Ctibor","Ľudomil","Jolana","Ľudmila","Olympia","Eugénia","Konštantín","Ľuboslav",
         "Matúš","Móric","Zdenka","Ľuboš","Vladislav","Edita","Cyprián","Václav","Michal","Jarolím"},
        // Oktober
        {"Arnold","Levoslav","Stela","František","Viera","Natália","Eliška","Brigita","Dionýz","Slavomíra",
         "Valentína","Maximilián","Koloman","Boris","Terézia","Vladimíra","Hedviga","Lukáš","Kristián","Vendelín",
         "Uršuľa","Sergej","Alojzia","Kvetoslava","Aurel","Demeter","Sabína","Dobromila","Klára","Šimon","Aurélia"},
        // November
        {"Všetkých svätých","Pamiatka zosnulých","Hubert","Karol","Imrich","Renáta","René","Bohumír","Teodor","Tibor",
         "Maroš","Svätopluk","Stanislav","Irma","Leopold","Agnesa","Klaudia","Eugen","Alžbeta","Félix",
         "Elvíra","Cecília","Klement","Emília","Katarína","Kornel","Milan","Henrieta","Vratko","Ondrej"},
        // December
        {"Edmund","Bibiána","Oldrich","Barbora","Oto","Mikuláš","Ambróz","Marína","Izabela","Radúz",
         "Hilda","Otília","Lucia","Branislava","Ivica","Albína","Kornélia","Slávka","Judita","Dagmar",
         "Bohdan","Adela","Nadežda","Adam a Eva","1. sviatok vianočný","Štefan","Filoména","Ivana","Milada","Dávid","Silvester"}
    };

    // Cesky kalendar jmen (Bucovice su v CZ, tak nech je vidno oboje).
    private static final String[][] SVATKY = {
        // Januar
        {"Nový rok","Karina","Radmila","Diana","Dalimil","Tři králové","Vilma","Čestmír","Vladan","Břetislav",
         "Bohdana","Pravoslav","Edita","Radovan","Alice","Ctirad","Drahoslav","Vladislav","Doubravka","Ilona",
         "Běla","Slavomír","Zdeněk","Milena","Miloš","Zora","Ingrid","Otýlie","Zdislava","Robin",
         "Marika"},
        // Februar
        {"Hynek","Nela","Blažej","Jarmila","Dobromila","Vanda","Veronika","Milada","Apolena","Mojmír",
         "Božena","Slavěna","Věnceslav","Valentin","Jiřina","Ljuba","Miloslava","Gizela","Patrik","Oldřich",
         "Lenka","Petr","Svatopluk","Matěj","Liliana","Dorota","Alexandr","Lumír","Horymír"},
        // Marec
        {"Bedřich","Anežka","Kamil","Stella","Kazimír","Miroslav","Tomáš","Gabriela","Františka","Viktorie",
         "Anděla","Řehoř","Růžena","Rut","Ida","Elena","Vlastimil","Eduard","Josef","Světlana",
         "Radek","Leona","Ivona","Gabriel","Marian","Emanuel","Dita","Soňa","Taťána","Arnošt",
         "Kvido"},
        // April
        {"Hugo","Erika","Richard","Ivana","Miroslava","Vendula","Heřman","Ema","Dušan","Darja",
         "Izabela","Julius","Aleš","Vincent","Anastázie","Irena","Rudolf","Valérie","Rostislav","Marcela",
         "Alexandra","Evženie","Vojtěch","Jiří","Marek","Oto","Jaroslav","Vlastislav","Robert","Blahoslav"},
        // Maj
        {"Svátek práce","Zikmund","Alexej","Květoslav","Klaudie","Radoslav","Stanislav","Den vítězství","Ctibor","Blažena",
         "Svatava","Pankrác","Servác","Bonifác","Žofie","Přemysl","Aneta","Nataša","Ivo","Zbyšek",
         "Monika","Emil","Vladimír","Jana","Viola","Filip","Valdemar","Vilém","Maxmilián","Ferdinand",
         "Kamila"},
        // Jun
        {"Laura","Jarmil","Tamara","Dalibor","Dobroslav","Norbert","Iveta","Medard","Stanislava","Gita",
         "Bruno","Antonie","Antonín","Roland","Vít","Zbyněk","Adolf","Milan","Leoš","Květa",
         "Alois","Pavla","Zdeňka","Jan","Ivan","Adrian","Ladislav","Lubomír","Petr","Šárka"},
        // Jul
        {"Jaroslava","Patricie","Radomír","Prokop","Cyril a Metoděj","Mistr Jan Hus","Bohuslava","Nora","Drahoslava","Libuše",
         "Olga","Bořek","Markéta","Karolína","Jindřich","Luboš","Martina","Drahomíra","Čeněk","Eliáš",
         "Vítězslav","Magdalena","Libor","Kristýna","Jakub","Anna","Věroslav","Viktor","Marta","Bořivoj",
         "Ignác"},
        // August
        {"Oskar","Gustav","Miluše","Dominik","Kristián","Oldřiška","Lada","Soběslav","Roman","Vavřinec",
         "Zuzana","Klára","Alena","Alan","Hana","Jáchym","Petra","Helena","Ludvík","Bernard",
         "Johana","Bohuslav","Sandra","Bartoloměj","Radim","Luděk","Otakar","Augustýn","Evelína","Vladěna",
         "Pavlína"},
        // September
        {"Linda","Adéla","Bronislav","Jindřiška","Boris","Boleslav","Regína","Mariana","Daniela","Irma",
         "Denisa","Marie","Lubor","Radka","Jolana","Ludmila","Naděžda","Kryštof","Zita","Oleg",
         "Matouš","Darina","Berta","Jaromír","Zlata","Andrea","Jonáš","Václav","Michal","Jeroným"},
        // Oktober
        {"Igor","Olivie","Bohumil","František","Eliška","Hanuš","Justina","Věra","Sára","Marina",
         "Andrej","Marcel","Renáta","Agáta","Tereza","Havel","Hedvika","Lukáš","Michaela","Vendelín",
         "Brigita","Sabina","Theodor","Nina","Beáta","Erik","Šarlota","Jidáš","Silvie","Tadeáš",
         "Štěpánka"},
        // November
        {"Felix","Tobiáš","Hubert","Karel","Miriam","Liběna","Saskie","Bohumír","Bohdan","Evžen",
         "Martin","Benedikt","Tibor","Sáva","Leopold","Otmar","Mahulena","Romana","Alžběta","Nikola",
         "Albert","Cecilie","Klement","Emilie","Kateřina","Artur","Xenie","René","Zina","Ondřej"},
        // December
        {"Iva","Blanka","Svatoslav","Barbora","Jitka","Mikuláš","Benjamín","Květoslava","Vratislav","Julie",
         "Dana","Simona","Lucie","Lydie","Radana","Albína","Daniel","Miloslav","Ester","Dagmar",
         "Natálie","Šimon","Vlasta","Adam a Eva","1. svátek vánoční","Štěpán","Žaneta","Bohumila","Judita","David",
         "Silvestr"}
    };
}
