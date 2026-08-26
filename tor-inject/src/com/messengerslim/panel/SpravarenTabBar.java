package com.messengerslim.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Vlastny spodny bar cez natativny Litho tab bar.
 * Natativne taby (android.view.View s content-desc "..., Tab N of 4") sa nedaju
 * prefarbit/premenovat/odstranit cez resources (kreslene na canvas). Preto ich
 * prekryjeme vlastnym barom (SK texty + vlastne ikonky, bez Stories) a tapy
 * DELEGUJEME touch-eventom na skryty natativny tab -> navigacia funguje bez
 * zasahu do Messengerovej nav logiky.
 */
public final class SpravarenTabBar {

    private static boolean started = false;
    private static FrameLayout overlay;      // nas bar
    private static LinearLayout rowRef;      // riadok s tabmi (vyska = natativny bar)
    private static int selected = 0;         // 0=Chaty,1=Oznamenia,2=Viac
    private static TabCell[] cells;

    // farebna schema tabov (ruzova / cervena / purpurova)
    private static final int[] TAB_COLOR = { 0xFFFF3D8B, 0xFFFF5252, 0xFFC061FF };
    private static final int INACTIVE = 0xFF8A8F98;

    public static void start(final View seed) {
        if (started || seed == null) return;
        started = true;
        final Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            public void run() {
                try { tick(seed); } catch (Throwable ignore) {}
                h.postDelayed(this, 400);
            }
        });
    }

    private static void tick(View seed) {
        View decor = seed.getRootView();
        if (!(decor instanceof ViewGroup)) return;
        ViewGroup decorVg = (ViewGroup) decor;

        // najdi natativne taby
        ArrayList<View> tabs = new ArrayList<View>();
        View[] byIndex = new View[5]; // 1..4
        collectTabs(decorVg, tabs, byIndex);

        boolean have = byIndex[1] != null && byIndex[3] != null && byIndex[4] != null
                && isShown(byIndex[1]);

        if (!have) {
            if (overlay != null) overlay.setVisibility(View.GONE);
            return;
        }

        View container = (View) byIndex[1].getParent();
        int barH = container.getHeight();
        if (barH <= 0) barH = byIndex[1].getHeight();
        if (barH <= 0) return;

        // pozicia natativneho baru voci decor
        int[] cloc = new int[2]; container.getLocationOnScreen(cloc);
        int[] dloc = new int[2]; decorVg.getLocationOnScreen(dloc);
        int top = cloc[1] - dloc[1];
        int decorH = decorVg.getHeight();
        int overlayH = decorH - top;          // az po spodok obrazovky -> ziadna diera dole
        if (overlayH < barH) overlayH = barH;

        ensureOverlay(decorVg, barH, byIndex);
        // pozicuj overlay presne na natativny bar a natiahni po spodok
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) overlay.getLayoutParams();
        lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
        lp.height = overlayH;
        lp.topMargin = top;
        lp.gravity = Gravity.TOP | Gravity.START;
        overlay.setLayoutParams(lp);
        // riadok tabov drz na vysku natativneho baru (obsah hore)
        if (rowRef != null) {
            ViewGroup.LayoutParams rlp = rowRef.getLayoutParams();
            if (rlp != null && rlp.height != barH) { rlp.height = barH; rowRef.setLayoutParams(rlp); }
        }
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
    }

    private static boolean isShown(View v) {
        try { return v.isShown(); } catch (Throwable t) { return false; }
    }

    private static void collectTabs(ViewGroup root, ArrayList<View> out, View[] byIndex) {
        int n = root.getChildCount();
        for (int i = 0; i < n; i++) {
            View c = root.getChildAt(i);
            CharSequence cd = c.getContentDescription();
            if (cd != null) {
                String s = cd.toString();
                int p = s.indexOf("Tab ");
                if (p >= 0 && s.contains(" of 4")) {
                    // vytiahni cislo za "Tab "
                    try {
                        int idx = Integer.parseInt(s.substring(p + 4, p + 5));
                        if (idx >= 1 && idx <= 4) { byIndex[idx] = c; out.add(c); }
                    } catch (Throwable ignore) {}
                }
            }
            if (c instanceof ViewGroup) collectTabs((ViewGroup) c, out, byIndex);
        }
    }

    private static void ensureOverlay(ViewGroup parent, int barH, View[] byIndex) {
        if (overlay != null && overlay.getParent() == parent) {
            // len preved delegaty (natativne taby sa mohli znovu vytvorit)
            if (cells != null) {
                cells[0].target = byIndex[1];
                cells[1].target = byIndex[3];
                cells[2].target = byIndex[4];
            }
            return;
        }
        Context ctx = parent.getContext();
        FrameLayout fl = new FrameLayout(ctx);
        fl.setBackgroundColor(0xFF0A0A0A);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(3f);

        cells = new TabCell[3];
        cells[0] = makeCell(ctx, 0, "Chaty", byIndex[1]);
        cells[1] = makeCell(ctx, 1, "Oznámenia", byIndex[3]);
        cells[2] = makeCell(ctx, 2, "Viac", byIndex[4]);
        for (TabCell tc : cells) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            row.addView(tc.view, lp);
        }

        // riadok tabov ukotveny hore (obsah na urovni natativneho baru),
        // FrameLayout pozadie natiahnute nizsie zakryje pripadnu dieru dole.
        fl.addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, barH, Gravity.TOP));
        rowRef = row;

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, barH, Gravity.TOP | Gravity.START);
        parent.addView(fl, flp);
        overlay = fl;
        refreshSelection();
    }

    private static TabCell makeCell(Context ctx, final int idx, String label, View target) {
        float d = ctx.getResources().getDisplayMetrics().density;
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        cell.setPadding(0, (int) (8 * d), 0, (int) (2 * d));

        IconView icon = new IconView(ctx, idx);
        int is = (int) (22 * d);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(is, is);
        cell.addView(icon, ilp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setPadding(0, (int) (3 * d), 0, 0);
        cell.addView(tv);

        final TabCell tc = new TabCell();
        tc.view = cell;
        tc.icon = icon;
        tc.text = tv;
        tc.target = target;
        cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selected = idx;
                refreshSelection();
                tapNative(tc.target);
            }
        });
        return tc;
    }

    private static void refreshSelection() {
        if (cells == null) return;
        for (int i = 0; i < cells.length; i++) {
            int base = TAB_COLOR[i % TAB_COLOR.length];
            if (i == selected) {
                cells[i].icon.setColor(base);
                cells[i].text.setTextColor(base);
                cells[i].icon.setFilled(true);
            } else {
                // dimnuta farba ikonky (stale farebne), text sedy
                int dim = (base & 0x00FFFFFF) | 0x80000000; // ~50% alpha
                cells[i].icon.setColor(dim);
                cells[i].text.setTextColor(INACTIVE);
                cells[i].icon.setFilled(false);
            }
        }
    }

    private static void tapNative(View tab) {
        if (tab == null) return;
        try {
            float x = Math.max(1f, tab.getWidth() / 2f);
            float y = Math.max(1f, tab.getHeight() / 2f);
            long t = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
            tab.dispatchTouchEvent(down);
            down.recycle();
            MotionEvent up = MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0);
            tab.dispatchTouchEvent(up);
            up.recycle();
            tab.performClick(); // fallback ak Litho pouziva click listener
        } catch (Throwable ignore) {}
    }

    private static final class TabCell {
        View view; IconView icon; TextView text; View target;
    }

    /** Vlastne ikonky kreslene na canvas: 0=chat bublina, 1=zvoncek, 2=menu. */
    private static final class IconView extends View {
        private final int type;
        private boolean filled = false;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        IconView(Context c, int type) {
            super(c);
            this.type = type;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(0xFF9AA0A6);
        }
        void setColor(int col) { p.setColor(col); invalidate(); }
        void setFilled(boolean f) { filled = f; invalidate(); }
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h);
            p.setStrokeWidth(s * (filled ? 0.12f : 0.085f));
            float cx = w / 2f, cy = h / 2f;
            if (type == 0) {
                // chat bublina so zaoblenim + chvostik
                float pad = s * 0.14f;
                RectF r = new RectF(pad, pad, w - pad, h - pad * 1.4f);
                float rad = s * 0.28f;
                cv.drawRoundRect(r, rad, rad, p);
                Path tail = new Path();
                tail.moveTo(w * 0.34f, r.bottom);
                tail.lineTo(w * 0.30f, h - pad * 0.4f);
                tail.lineTo(w * 0.5f, r.bottom);
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(tail, p);
                p.setStyle(Paint.Style.STROKE);
            } else if (type == 1) {
                // zvoncek
                float pad = s * 0.18f;
                Path bell = new Path();
                float top = pad, bot = h - pad * 1.5f;
                bell.moveTo(cx, top);
                bell.cubicTo(w - pad, top + s * 0.05f, w - pad, bot, w - pad * 0.7f, bot);
                bell.lineTo(pad * 0.7f, bot);
                bell.cubicTo(pad, bot, pad, top + s * 0.05f, cx, top);
                bell.close();
                cv.drawPath(bell, p);
                // klapadlo
                cv.drawArc(new RectF(cx - s * 0.10f, bot, cx + s * 0.10f, bot + s * 0.18f),
                        0, 180, false, p);
            } else {
                // menu - tri ciary
                float pad = s * 0.16f;
                float gap = s * 0.22f;
                cv.drawLine(pad, cy - gap, w - pad, cy - gap, p);
                cv.drawLine(pad, cy, w - pad, cy, p);
                cv.drawLine(pad, cy + gap, w - pad, cy + gap, p);
            }
        }
    }
}
