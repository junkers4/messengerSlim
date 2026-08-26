package com.messengerslim.panel;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/**
 * Panel na miesto Stories pasu v inboxe: pocasie (Open-Meteo, cez Tor) + meniny.
 * Vola sa z X/2ml.A04 tesne pred vratenim tray (LX/1W0 = ViewGroup, super LX/50d).
 * LX/50d rozklada len jedno "hlavne" dieta (public field A0N); nastavime A0N na nas panel.
 */
public final class WeatherPanel {

    // Bučovice (CZ, Jihomoravský kraj)
    private static final double LAT = 49.1497;
    private static final double LON = 17.0003;
    private static final String CITY = "Bučovice";

    private WeatherPanel() {}

    /**
     * Vstupny bod zo smali (X/2ml.A04). tray je LX/1W0.
     * A04 je ZDIELANA factory (pouziva sa aj pre zoznam sprav vo vlakne!),
     * takze panel vlozime IBA ak je vnutorny RecyclerView HORIZONTALNY = stories pas.
     * Vertikalny zoznam (chat / hlavny inbox list) nechame uplne na pokoji.
     * Orientacia nie je znama pri stavbe -> rozhodnutie odlozime (post + retry).
     */
    public static void install(final ViewGroup tray) {
        if (tray == null) return;
        decide(tray, 0);
    }

    private static void decide(final ViewGroup tray, final int attempt) {
        // A04/LX/1W0 sa pouziva aj pre zoznam sprav vo vlakne (vysoky, cela obrazovka)
        // aj pre stories pas v inboxe (nizky, ~130dp). Rozlisime podla vysky po layoute.
        int h = tray.getHeight();
        if (h > 0) {
            int screen = tray.getResources().getDisplayMetrics().heightPixels;
            if (h < screen / 2) {
                replaceWithPanel(tray); // nizky pas = stories -> panel
            }
            return; // vysoky = chat / hlavny list -> nechame normalny
        }
        if (attempt < 15) {
            tray.postDelayed(new Runnable() {
                public void run() { decide(tray, attempt + 1); }
            }, 50);
        }
    }

    private static void replaceWithPanel(ViewGroup tray) {
        try {
            if (tray.findViewWithTag("swp_panel") != null) return; // uz nas panel v tomto tray
            // dedup: po refreshi Messenger vyrobi novy tray -> nechceme 2x panel.
            // ak uz panel existuje inde v okne, tento tray len skryjeme.
            View root = tray.getRootView();
            if (root instanceof ViewGroup && ((ViewGroup) root).findViewWithTag("swp_panel") != null) {
                tray.setVisibility(View.GONE);
                return;
            }
            Context ctx = tray.getContext();
            // skry povodny obsah (stories RecyclerView), nech sa neprekryva
            for (int i = 0; i < tray.getChildCount(); i++) {
                View ch = tray.getChildAt(i);
                if (ch != null) ch.setVisibility(View.GONE);
            }
            View panel = build(ctx);
            panel.setTag("swp_panel");
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tray.addView(panel, lp);
            // LX/50d rozklada len public field A0N -> nasmerujeme ho na nas panel
            try {
                tray.getClass().getField("A0N").set(tray, panel);
            } catch (Throwable ignore) { }
        } catch (Throwable t) { /* ignore - chat ostava normalny */ }
    }

    private static View build(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int px = (int) (14 * d);
        box.setPadding(px, (int) (8 * d), px, (int) (8 * d));
        box.setMinimumHeight((int) (104 * d));

        final TextView weather = new TextView(ctx);
        weather.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        weather.setTextColor(Color.WHITE);
        weather.setGravity(Gravity.CENTER);
        weather.setText(CITY + " · …");

        TextView nameday = new TextView(ctx);
        nameday.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        nameday.setTextColor(0xFFB388FF); // fialova (branding akcent)
        nameday.setGravity(Gravity.CENTER);
        nameday.setPadding(0, (int) (3 * d), 0, 0);
        nameday.setText("Dnes má meniny: " + namedayToday());

        // news ticker (titulky v skratke, bezia horizontalne)
        final TextView news = new TextView(ctx);
        news.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        news.setTextColor(0xFFBEBEBE);
        news.setGravity(Gravity.CENTER);
        news.setPadding(0, (int) (5 * d), 0, 0);
        news.setSingleLine(true);
        news.setHorizontallyScrolling(true);
        news.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        news.setMarqueeRepeatLimit(-1);
        news.setSelected(true); // spusti marquee
        news.setText("📰 načítavam správy…");

        box.addView(weather);
        box.addView(nameday);
        box.addView(news);

        fetchWeatherAsync(weather);
        fetchNewsAsync(news);
        return box;
    }

    // ---- pocasie ----

    private static void fetchWeatherAsync(final TextView target) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                final String text = fetchWeather();
                main.post(new Runnable() {
                    public void run() {
                        try { target.setText(text); } catch (Throwable ignore) {}
                    }
                });
            }
        }, "swp-weather").start();
    }

    private static String fetchWeather() {
        HttpURLConnection c = null;
        try {
            String u = "https://api.open-meteo.com/v1/forecast?latitude=" + LAT
                    + "&longitude=" + LON + "&current=temperature_2m,weather_code";
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = c.getResponseCode();
            if (code != 200) return CITY + " · počasie nedostupné";
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject root = new JSONObject(sb.toString());
            JSONObject cur = root.getJSONObject("current");
            double temp = cur.getDouble("temperature_2m");
            int wc = cur.optInt("weather_code", -1);
            long t = Math.round(temp);
            return CITY + " · " + t + "°C " + wmo(wc);
        } catch (Throwable e) {
            return CITY + " · počasie nedostupné";
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignore) {}
        }
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

    // ---- news ticker (titulky) ----

    // Google News RSS (SK) - bez API kluca, ide cez Tor. Zmena zdroja = zmena URL.
    private static final String NEWS_RSS =
            "https://news.google.com/rss?hl=sk&gl=SK&ceid=SK:sk";

    private static void fetchNewsAsync(final TextView target) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                final String text = fetchNews();
                if (text == null) return;
                main.post(new Runnable() {
                    public void run() {
                        try {
                            target.setText(text);
                            target.setSelected(true); // re-spusti marquee
                        } catch (Throwable ignore) {}
                    }
                });
            }
        }, "swp-news").start();
    }

    private static String fetchNews() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(NEWS_RSS).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (c.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            String xml = sb.toString();

            StringBuilder out = new StringBuilder("📰  ");
            int from = 0, taken = 0;
            while (taken < 6) {
                int it = xml.indexOf("<item>", from);
                if (it < 0) break;
                int ts = xml.indexOf("<title>", it);
                int te = xml.indexOf("</title>", ts);
                from = (te > 0) ? te : (it + 6);
                if (ts < 0 || te < 0) continue;
                String title = xml.substring(ts + 7, te).trim();
                title = stripCdata(title);
                title = unescape(title);
                int dash = title.lastIndexOf(" - ");
                if (dash > 20) title = title.substring(0, dash); // odstran " - Zdroj"
                if (title.isEmpty()) continue;
                if (taken > 0) out.append("      •      ");
                out.append(title);
                taken++;
            }
            return (taken > 0) ? out.toString() : null;
        } catch (Throwable e) {
            return null;
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignore) {}
        }
    }

    private static String stripCdata(String s) {
        if (s.startsWith("<![CDATA[") && s.endsWith("]]>")) {
            return s.substring(9, s.length() - 3);
        }
        return s;
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&nbsp;", " ");
    }

    // ---- meniny (SK kalendar mien) ----

    public static String namedayToday() {
        Calendar cal = Calendar.getInstance();
        int m = cal.get(Calendar.MONTH);        // 0..11
        int day = cal.get(Calendar.DAY_OF_MONTH); // 1..31
        try {
            String[] month = MENINY[m];
            if (day >= 1 && day <= month.length) {
                String n = month[day - 1];
                if (n != null && n.length() > 0) return n;
            }
        } catch (Throwable ignore) {}
        return "—";
    }

    // Standardny slovensky kalendar mien. Sviatky bez mena su oznacene popisom.
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
}
