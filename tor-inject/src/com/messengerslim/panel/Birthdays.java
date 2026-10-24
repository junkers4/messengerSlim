package com.messengerslim.panel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Narodeniny, ktore si user zada sam. Len v telefone (SharedPreferences), nikam sa neposielaju,
 * ziadne notifikacie - zobrazia sa v paneli, ked sa appka otvori (R4: ziadne budenie).
 *
 * Ulozene ako riadky "meno|den|mesiac|rok" (rok 0 = nezadany).
 */
final class Birthdays {

    /** Kolko dni dopredu ukazovat v paneli. */
    static final int AHEAD_DAYS = 7;

    private static final String PREFS = "swp_panel", KEY = "birthdays";

    private Birthdays() {}

    static final class Entry {
        final String name; final int day, month, year;
        Entry(String name, int day, int month, int year) {
            this.name = name; this.day = day; this.month = month; this.year = year;
        }
        String raw() { return name.replace('|', '/') + "|" + day + "|" + month + "|" + year; }

        /** Dni do najblizsich narodenin (0 = dnes). */
        int daysUntil(Calendar today) {
            Calendar t = (Calendar) today.clone();
            t.set(Calendar.HOUR_OF_DAY, 0); t.set(Calendar.MINUTE, 0);
            t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0);
            Calendar b = (Calendar) t.clone();
            b.set(Calendar.MONTH, month - 1);
            // 29. 2. v neprestupnom roku -> 28. 2.
            b.set(Calendar.DAY_OF_MONTH, Math.min(day, b.getActualMaximum(Calendar.DAY_OF_MONTH)));
            if (b.before(t)) {
                b.add(Calendar.YEAR, 1);
                b.set(Calendar.MONTH, month - 1);
                b.set(Calendar.DAY_OF_MONTH, Math.min(day, b.getActualMaximum(Calendar.DAY_OF_MONTH)));
            }
            return (int) Math.round((b.getTimeInMillis() - t.getTimeInMillis()) / 86_400_000.0);
        }

        /** Vek, ktory oslavi pri najblizsich narodeninach, alebo -1 bez roku. */
        int ageAtNext(Calendar today) {
            if (year <= 0) return -1;
            Calendar n = (Calendar) today.clone();
            n.add(Calendar.DAY_OF_MONTH, daysUntil(today));
            return n.get(Calendar.YEAR) - year;
        }

        String label() {
            return name + "  ·  " + day + ". " + month + "." + (year > 0 ? " " + year : "");
        }
    }

    static List<Entry> load(Context ctx) {
        List<Entry> out = new ArrayList<>();
        String s = prefs(ctx).getString(KEY, "");
        for (String line : s.split("\n")) {
            String[] p = line.split("\\|");
            if (p.length < 4) continue;
            try {
                out.add(new Entry(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            } catch (NumberFormatException ignore) {}
        }
        final Calendar now = Calendar.getInstance();
        Collections.sort(out, (a, b) -> a.daysUntil(now) - b.daysUntil(now));
        return out;
    }

    private static void save(Context ctx, List<Entry> list) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : list) sb.append(e.raw()).append('\n');
        prefs(ctx).edit().putString(KEY, sb.toString()).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Riadok do panelu, alebo null ak nikto nema narodeniny do AHEAD_DAYS. */
    static String line(Context ctx) {
        Calendar now = Calendar.getInstance();
        StringBuilder today = new StringBuilder(), soon = new StringBuilder();
        for (Entry e : load(ctx)) {
            int d = e.daysUntil(now);
            if (d > AHEAD_DAYS) continue;
            int age = e.ageAtNext(now);
            String who = e.name + (age > 0 ? " (" + age + ")" : "");
            if (d == 0) { if (today.length() > 0) today.append(", "); today.append(who); }
            else {
                if (soon.length() > 0) soon.append(", ");
                soon.append(d == 1 ? "zajtra " : "o " + d + " " + (d < 5 ? "dni" : "dní") + " ").append(who);
            }
        }
        if (today.length() == 0 && soon.length() == 0) return null;
        StringBuilder sb = new StringBuilder("🎂 ");
        if (today.length() > 0) sb.append("Dnes: ").append(today);
        if (soon.length() > 0) sb.append(today.length() > 0 ? "  ·  " : "").append(soon);
        return sb.toString();
    }

    /** Parsuje "24.10." / "24.10" / "24.10.1990" / "24. 10. 1990" -> {den, mesiac, rok}. */
    static int[] parseDate(String s) {
        String[] p = s.replace(" ", "").split("[./-]");
        try {
            int d = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            int y = (p.length > 2 && !p[2].isEmpty()) ? Integer.parseInt(p[2]) : 0;
            if (y > 0 && y < 100) y += (y > Calendar.getInstance().get(Calendar.YEAR) % 100) ? 1900 : 2000;
            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, 2024);                    // prestupny -> 29. 2. prejde
            c.set(Calendar.MONTH, Math.max(0, Math.min(11, m - 1)));
            if (m < 1 || m > 12 || d < 1 || d > c.getActualMaximum(Calendar.DAY_OF_MONTH)) return null;
            if (y != 0 && (y < 1900 || y > Calendar.getInstance().get(Calendar.YEAR))) return null;
            return new int[] { d, m, y };
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- okno: zoznam + pridat + zmazat ----

    static void showDialog(final Context ctx, final Runnable onChange) {
        final Activity act = activity(ctx);
        if (act == null) return;
        final List<Entry> list = load(act);
        final List<String> rows = new ArrayList<>();
        final Calendar now = Calendar.getInstance();
        for (Entry e : list) {
            int d = e.daysUntil(now);
            rows.add(e.label() + (d == 0 ? "   🎂 dnes" : "   · o " + d + " d"));
        }

        ListView lv = new ListView(act);
        lv.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_list_item_1, rows));
        TextView empty = new TextView(act);
        empty.setText("Zatiaľ nič. Ťukni na Pridať.\nPodržanie mena ho zmaže.");
        int pad = dp(act, 20);
        empty.setPadding(pad, pad, pad, pad);

        final AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle("🎂 Narodeniny")
                .setView(rows.isEmpty() ? empty : lv)
                .setPositiveButton("Pridať", (d, w) -> showAdd(act, onChange))
                .setNegativeButton("Zavrieť", null)
                .create();
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, final int pos, long id) {
                final Entry e = list.get(pos);
                new AlertDialog.Builder(act)
                        .setMessage("Zmazať " + e.name + "?")
                        .setPositiveButton("Zmazať", (d, w) -> {
                            list.remove(pos); save(act, list);
                            dlg.dismiss(); onChange.run(); showDialog(act, onChange);
                        })
                        .setNegativeButton("Nie", null).show();
                return true;
            }
        });
        dlg.show();
    }

    private static void showAdd(final Activity act, final Runnable onChange) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(act, 20);
        box.setPadding(pad, dp(act, 8), pad, 0);
        final EditText name = new EditText(act);
        name.setHint("Meno");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText date = new EditText(act);
        date.setHint("Dátum: 24.10. alebo 24.10.1990");
        date.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        box.addView(name);
        box.addView(date);

        new AlertDialog.Builder(act)
                .setTitle("Pridať narodeniny")
                .setView(box)
                .setPositiveButton("Uložiť", (d, w) -> {
                    String n = name.getText().toString().trim();
                    int[] dt = parseDate(date.getText().toString());
                    if (n.isEmpty() || dt == null) {
                        Toast.makeText(act, "Zadaj meno a dátum, napr. 24.10.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    List<Entry> list = load(act);
                    list.add(new Entry(n, dt[0], dt[1], dt[2]));
                    save(act, list);
                    onChange.run();
                    showDialog(act, onChange);
                })
                .setNegativeButton("Zrušiť", null)
                .show();
    }

    private static Activity activity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }
}
