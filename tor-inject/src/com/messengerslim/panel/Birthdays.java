package com.messengerslim.panel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Narodeniny, ktore si user zada sam. Len v telefone (SharedPreferences), nikam sa neposielaju,
 * ziadne notifikacie - zobrazia sa v paneli, ked sa appka otvori (R4: ziadne budenie).
 * K narodeninam panel ukaze aj meniny tychto ludi (meno sa hlada v SK/CZ kalendari).
 *
 * Ulozene ako riadky "meno|den|mesiac|rok" (rok 0 = nezadany).
 */
final class Birthdays {

    /** Kolko dni dopredu ukazovat v paneli. */
    static final int AHEAD_DAYS = 7;

    private static final String PREFS = "swp_panel", KEY = "birthdays";
    private static final String[] MONTHS = { "januára", "februára", "marca", "apríla", "mája", "júna",
            "júla", "augusta", "septembra", "októbra", "novembra", "decembra" };
    private static final int GOLD = 0xFFFFD54F, GREY = 0xFF9AA0A6, ROW_BG = 0xFF2A2A2E, TODAY_BG = 0xFF4A3B12;

    private Birthdays() {}

    static final class Entry {
        final String name; final int day, month, year;
        Entry(String name, int day, int month, int year) {
            this.name = name; this.day = day; this.month = month; this.year = year;
        }
        String raw() { return name.replace('|', '/').replace('\n', ' ') + "|" + day + "|" + month + "|" + year; }

        int daysUntil(Calendar today) { return Birthdays.daysUntil(today, day, month); }

        /** Vek, ktory oslavi pri najblizsich narodeninach, alebo -1 bez roku. */
        int ageAtNext(Calendar today) {
            if (year <= 0) return -1;
            Calendar n = (Calendar) today.clone();
            n.add(Calendar.DAY_OF_MONTH, daysUntil(today));
            return n.get(Calendar.YEAR) - year;
        }
    }

    /** Dni do najblizsieho day.month (0 = dnes); 29. 2. v neprestupnom roku = 28. 2. */
    static int daysUntil(Calendar today, int day, int month) {
        Calendar t = (Calendar) today.clone();
        t.set(Calendar.HOUR_OF_DAY, 0); t.set(Calendar.MINUTE, 0);
        t.set(Calendar.SECOND, 0); t.set(Calendar.MILLISECOND, 0);
        Calendar b = (Calendar) t.clone();
        b.set(Calendar.DAY_OF_MONTH, 1);
        b.set(Calendar.MONTH, month - 1);
        b.set(Calendar.DAY_OF_MONTH, Math.min(day, b.getActualMaximum(Calendar.DAY_OF_MONTH)));
        if (b.before(t)) {
            b.set(Calendar.DAY_OF_MONTH, 1);
            b.add(Calendar.YEAR, 1);
            b.set(Calendar.MONTH, month - 1);
            b.set(Calendar.DAY_OF_MONTH, Math.min(day, b.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        return (int) Math.round((b.getTimeInMillis() - t.getTimeInMillis()) / 86_400_000.0);
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

    static String when(int d) {
        if (d == 0) return "dnes";
        if (d == 1) return "zajtra";
        return "o " + d + " " + (d < 5 ? "dni" : "dní");
    }

    /** Riadok do panelu: narodeniny + meniny ludi zo zoznamu do AHEAD_DAYS, alebo null. */
    static String line(Context ctx) {
        return line(load(ctx), Calendar.getInstance());
    }

    static String line(List<Entry> list, Calendar now) {
        StringBuilder bd = new StringBuilder(), nd = new StringBuilder();
        for (Entry e : list) {
            int d = e.daysUntil(now);
            if (d <= AHEAD_DAYS) {
                int age = e.ageAtNext(now);
                if (bd.length() > 0) bd.append(", ");
                bd.append(d == 0 ? "Dnes" : when(d)).append(' ').append(e.name).append(age > 0 ? " (" + age + ")" : "");
            }
            int[] n = WeatherPanel.namedayOf(e.name);
            if (n != null) {
                int dn = daysUntil(now, n[1], n[0]);
                if (dn <= AHEAD_DAYS) {
                    if (nd.length() > 0) nd.append(", ");
                    nd.append(dn == 0 ? "dnes" : when(dn)).append(' ').append(e.name);
                }
            }
        }
        if (bd.length() == 0 && nd.length() == 0) return null;
        StringBuilder sb = new StringBuilder();
        if (bd.length() > 0) sb.append("🎂 ").append(bd);
        if (nd.length() > 0) sb.append(bd.length() > 0 ? "  ·  " : "").append("🌸 meniny ").append(nd);
        return sb.toString();
    }

    /** Parsuje "24.10." / "24.10" / "24.10.1990" / "24. 10. 1990" -> {den, mesiac, rok}. */
    static int[] parseDate(String s) {
        String[] p = s.replace(" ", "").split("[./-]");
        try {
            int d = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            int y = (p.length > 2 && !p[2].isEmpty()) ? Integer.parseInt(p[2]) : 0;
            if (y > 0 && y < 100) y += (y > Calendar.getInstance().get(Calendar.YEAR) % 100) ? 1900 : 2000;
            if (m < 1 || m > 12 || d < 1 || d > maxDay(m)) return null;
            if (y != 0 && (y < 1900 || y > Calendar.getInstance().get(Calendar.YEAR))) return null;
            return new int[] { d, m, y };
        } catch (Throwable t) {
            return null;
        }
    }

    /** Max den v mesiaci (februar 29, nech prejdu aj narodeniny 29. 2.). */
    static int maxDay(int month) {
        return month == 2 ? 29 : (month == 4 || month == 6 || month == 9 || month == 11) ? 30 : 31;
    }

    // ---- okno: zoznam ----

    static void showDialog(final Context ctx, final Runnable onChange) {
        final Activity act = activity(ctx);
        if (act == null) return;
        final List<Entry> list = load(act);
        final Calendar now = Calendar.getInstance();

        LinearLayout rows = new LinearLayout(act);
        rows.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(act, 16);
        rows.setPadding(pad, dp(act, 4), pad, dp(act, 4));
        final AlertDialog[] dlg = new AlertDialog[1];

        if (list.isEmpty()) {
            TextView empty = text(act, "Zatiaľ tu nikto nie je.\nŤukni na Pridať.", 15, GREY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(act, 24), 0, dp(act, 24));
            rows.addView(empty);
        }
        for (final Entry e : list) {
            int d = e.daysUntil(now);
            int age = e.ageAtNext(now);
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(act, 14), dp(act, 10), dp(act, 14), dp(act, 10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(d == 0 ? TODAY_BG : ROW_BG);
            bg.setCornerRadius(dp(act, 14));
            row.setBackground(bg);

            TextView name = text(act, (d == 0 ? "🎉 " : "🎂 ") + e.name, 17, d == 0 ? GOLD : 0xFFFFFFFF);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            StringBuilder sub = new StringBuilder();
            sub.append(e.day).append(". ").append(MONTHS[e.month - 1]).append("  ·  ").append(when(d));
            if (age > 0) sub.append("  ·  ").append(age).append(age >= 5 ? " rokov" : age > 1 ? " roky" : " rok");
            int[] n = WeatherPanel.namedayOf(e.name);
            if (n != null) sub.append("\n🌸 meniny ").append(n[1]).append(". ").append(MONTHS[n[0] - 1])
                    .append(n[2] == 1 ? " (CZ)" : "").append("  ·  ").append(when(daysUntil(now, n[1], n[0])));
            row.addView(name);
            row.addView(text(act, sub.toString(), 13, GREY));

            row.setOnClickListener(v -> { dlg[0].dismiss(); showEdit(act, e, onChange); });
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(act)
                        .setMessage("Zmazať " + e.name + "?")
                        .setPositiveButton("Zmazať", (x, w) -> {
                            List<Entry> all = load(act);
                            removeSame(all, e);
                            save(act, all);
                            dlg[0].dismiss(); onChange.run(); showDialog(act, onChange);
                        })
                        .setNegativeButton("Nie", null).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(act, 8);
            rows.addView(row, lp);
        }
        if (!list.isEmpty()) {
            TextView hint = text(act, "ťuk = upraviť  ·  podržanie = zmazať", 12, GREY);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(act, 10), 0, 0);
            rows.addView(hint);
        }
        ScrollView sv = new ScrollView(act);
        sv.addView(rows);

        dlg[0] = new AlertDialog.Builder(act)
                .setTitle("🎂 Narodeniny" + (list.isEmpty() ? "" : " (" + list.size() + ")"))
                .setView(sv)
                .setPositiveButton("Pridať", (x, w) -> showEdit(act, null, onChange))
                .setNegativeButton("Zavrieť", null)
                .create();
        dlg[0].show();
    }

    private static void removeSame(List<Entry> all, Entry e) {
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).raw().equals(e.raw())) { all.remove(i); return; }
        }
    }

    // ---- okno: pridat / upravit (meno + kolieska den / mesiac / rok) ----

    private static void showEdit(final Activity act, final Entry old, final Runnable onChange) {
        final Calendar now = Calendar.getInstance();
        final int thisYear = now.get(Calendar.YEAR);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(act, 20);
        box.setPadding(pad, dp(act, 8), pad, 0);

        final EditText name = new EditText(act);
        name.setHint("Meno (napr. Miška)");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setSingleLine(true);
        if (old != null) name.setText(old.name);
        box.addView(name);

        LinearLayout wheels = new LinearLayout(act);
        wheels.setOrientation(LinearLayout.HORIZONTAL);
        wheels.setGravity(Gravity.CENTER);

        final NumberPicker day = new NumberPicker(act);
        day.setMinValue(1); day.setMaxValue(31);
        day.setValue(old != null ? old.day : now.get(Calendar.DAY_OF_MONTH));

        final NumberPicker month = new NumberPicker(act);
        month.setMinValue(1); month.setMaxValue(12);
        month.setDisplayedValues(MONTHS);
        month.setValue(old != null ? old.month : now.get(Calendar.MONTH) + 1);

        // rok: prva polozka "—" = neviem
        final int yearMin = 1920;
        final String[] years = new String[thisYear - yearMin + 2];
        years[0] = "—";
        for (int i = 1; i < years.length; i++) years[i] = String.valueOf(thisYear - i + 1);
        final NumberPicker year = new NumberPicker(act);
        year.setMinValue(0); year.setMaxValue(years.length - 1);
        year.setDisplayedValues(years);
        year.setValue(old != null && old.year > 0 ? thisYear - old.year + 1 : 0);
        for (NumberPicker np : new NumberPicker[] { day, month, year }) {
            np.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,
                    np == month ? 1.6f : 1f);
            wheels.addView(np, lp);
        }
        box.addView(wheels);

        final TextView preview = text(act, "", 14, GOLD);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(0, dp(act, 6), 0, dp(act, 4));
        box.addView(preview);

        final Runnable update = () -> {
            day.setMaxValue(maxDay(month.getValue()));
            int y = year.getValue() == 0 ? 0 : thisYear - year.getValue() + 1;
            Entry e = new Entry(name.getText().toString().trim(), day.getValue(), month.getValue(), y);
            int d = e.daysUntil(now), age = e.ageAtNext(now);
            StringBuilder sb = new StringBuilder("🎂 ").append(when(d));
            if (age > 0) sb.append("  ·  bude mať ").append(age);
            int[] n = e.name.isEmpty() ? null : WeatherPanel.namedayOf(e.name);
            if (n != null) sb.append("\n🌸 meniny ").append(n[1]).append(". ").append(MONTHS[n[0] - 1])
                    .append(n[2] == 1 ? " (CZ)" : "");
            preview.setText(sb);
        };
        NumberPicker.OnValueChangeListener ch = (p, a, b) -> update.run();
        day.setOnValueChangedListener(ch);
        month.setOnValueChangedListener(ch);
        year.setOnValueChangedListener(ch);
        name.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { update.run(); }
        });
        update.run();

        new AlertDialog.Builder(act)
                .setTitle(old == null ? "Pridať narodeniny" : "Upraviť")
                .setView(box)
                .setPositiveButton("Uložiť", (x, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        Toast.makeText(act, "Zadaj meno", Toast.LENGTH_SHORT).show();
                        showEdit(act, old, onChange);
                        return;
                    }
                    int y = year.getValue() == 0 ? 0 : thisYear - year.getValue() + 1;
                    List<Entry> list = load(act);
                    if (old != null) removeSame(list, old);
                    list.add(new Entry(n, Math.min(day.getValue(), maxDay(month.getValue())), month.getValue(), y));
                    save(act, list);
                    onChange.run();
                    showDialog(act, onChange);
                })
                .setNegativeButton("Zrušiť", (x, w) -> showDialog(act, onChange))
                .show();
    }

    private static TextView text(Context c, String s, int sp, int color) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        return tv;
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
