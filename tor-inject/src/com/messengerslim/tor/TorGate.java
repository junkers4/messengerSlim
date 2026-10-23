package com.messengerslim.tor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Pasik hore, kym Tor nabieha. Appka je pod nim normalne pouzitelna - stare spravy sa daju
 * citat hned a napisana sprava pocka vo fronte Messengera. Von aj tak nic neodide: tunel je
 * fail-closed, kym Tor nie je READY (bez hev = blackhole).
 *
 * Vynimka: ked VpnService pri zaseknutom bootstrape docasne otvori siet (stav OPEN), pasik
 * sa roztiahne na celu obrazovku a zjeda doteky, aby pouzivatel nic neposlal mimo Tor.
 *
 * Stav sa cita z TorStatus (subor v filesDir), lebo VpnService bezi v procese :tor.
 */
public final class TorGate {

    private static final long POLL_MS = 400;
    private static final long GIVE_UP_MS = 90_000;

    private static FrameLayout gate;
    private static LinearLayout bar;
    private static TextView title, hint;
    private static View progressFill;
    private static View progressTrack;
    private static OnionView onion;
    private static Handler handler;
    private static Runnable poll;
    private static boolean done = false;
    private static boolean blocking = false;
    private static long shownAt = 0;

    private static final View.OnTouchListener EAT = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent e) { return true; }   // nic neprejde do appky
    };

    private TorGate() {}

    /** Zavola sa, ked Activity pride do popredia. */
    public static void attach(final Activity a) {
        try {
            if (done || a == null) return;
            if (TorStatus.READY.equals(TorStatus.read(a))) { done = true; return; }

            View decor = a.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) decor;
            if (gate != null && gate.getParent() == root) return;
            detach();

            final float d = a.getResources().getDisplayMetrics().density;

            // cela plocha; v rezime pasika je priehladna a doteky prepusta do appky
            FrameLayout box = new FrameLayout(a);

            LinearLayout b = new LinearLayout(a);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setBackgroundColor(0xF20B0B10);

            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (14 * d), (int) (7 * d), (int) (14 * d), (int) (7 * d));

            onion = new OnionView(a);
            LinearLayout.LayoutParams olp =
                    new LinearLayout.LayoutParams((int) (20 * d), (int) (20 * d));
            olp.rightMargin = (int) (10 * d);
            row.addView(onion, olp);

            title = new TextView(a);
            title.setText("Connecting to Tor…");
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            row.addView(title);
            b.addView(row);

            FrameLayout track = new FrameLayout(a);
            track.setBackgroundColor(0xFF241C33);
            View f = new View(a);
            f.setBackgroundColor(0xFF9A63FF);
            track.addView(f, new FrameLayout.LayoutParams(0, (int) (2 * d)));
            b.addView(track, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (2 * d)));
            progressTrack = track;
            progressFill = f;

            hint = new TextView(a);
            hint.setTextColor(0xFF9AA0A8);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding((int) (36 * d), (int) (28 * d), (int) (36 * d), 0);
            hint.setVisibility(View.GONE);
            b.addView(hint);

            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
            blp.topMargin = statusBarHeight(a, root);
            box.addView(b, blp);

            root.addView(box, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            box.bringToFront();
            gate = box;
            bar = b;
            blocking = false;
            shownAt = System.currentTimeMillis();
            startPolling(a);
        } catch (Throwable ignore) {}
    }

    private static int statusBarHeight(Activity a, View root) {
        try {
            WindowInsets in = root.getRootWindowInsets();
            if (in != null && in.getSystemWindowInsetTop() > 0) return in.getSystemWindowInsetTop();
        } catch (Throwable ignore) {}
        try {
            int id = a.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) return a.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignore) {}
        return 0;
    }

    /** OPEN = siet je docasne otvorena mimo Tor -> zakry appku a zjedz doteky. */
    private static void setBlocking(boolean on) {
        if (gate == null || bar == null || on == blocking) return;
        blocking = on;
        if (on) {
            gate.setBackgroundColor(0xF20B0B10);
            gate.setClickable(true);
            gate.setOnTouchListener(EAT);
            hint.setText("Tor is finishing its connection.\nThe app is paused so nothing leaves outside Tor.");
            hint.setVisibility(View.VISIBLE);
        } else {
            gate.setBackgroundColor(Color.TRANSPARENT);
            gate.setClickable(false);
            gate.setOnTouchListener(null);
            hint.setVisibility(View.GONE);
        }
    }

    private static void startPolling(final Activity a) {
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        if (poll != null) handler.removeCallbacks(poll);
        poll = new Runnable() {
            public void run() {
                try {
                    if (gate != null) gate.bringToFront();   // drz sa nad appkou
                    String s = TorStatus.read(a);
                    if (TorStatus.READY.equals(s)) {
                        done = true;
                        setBlocking(false);
                        if (title != null) title.setText("Connected through Tor");
                        setProgress(1f);
                        if (onion != null) onion.setLit(true);
                        handler.postDelayed(new Runnable() {
                            public void run() { fadeOut(); }
                        }, 900);
                        return;
                    }
                    setBlocking(TorStatus.OPEN.equals(s));
                    int pct = TorStatus.percent(s);
                    if (title != null) {
                        title.setText(pct >= 0 ? "Connecting to Tor… " + pct + " %" : "Connecting to Tor…");
                    }
                    if (pct >= 0) setProgress(pct / 100f);

                    if (System.currentTimeMillis() - shownAt > GIVE_UP_MS) { stalled(a); return; }
                    handler.postDelayed(this, POLL_MS);
                } catch (Throwable t) {
                    detach();
                }
            }
        };
        handler.post(poll);
    }

    /** Bootstrap dlho nikam nevedie - povedz to a ponukni novy pokus. */
    private static void stalled(final Activity a) {
        try {
            setBlocking(false);
            if (title != null) title.setText("Tor is slow – tap here to retry");
            if (bar != null) {
                bar.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            a.startService(new Intent(a, OrcaTorVpnService.class)
                                    .setAction(OrcaTorVpnService.ACTION_START));
                        } catch (Throwable ignore) {}
                        v.setOnClickListener(null);
                        v.setClickable(false);
                        shownAt = System.currentTimeMillis();
                        if (title != null) title.setText("Connecting to Tor…");
                        startPolling(a);
                    }
                });
            }
        } catch (Throwable ignore) {}
    }

    private static void setProgress(float frac) {
        try {
            if (progressFill == null || progressTrack == null) return;
            int w = progressTrack.getWidth();
            if (w <= 0) return;
            ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
            lp.width = (int) (w * Math.max(0f, Math.min(1f, frac)));
            progressFill.setLayoutParams(lp);
        } catch (Throwable ignore) {}
    }

    private static void fadeOut() {
        try {
            if (gate == null) { detach(); return; }
            gate.animate().alpha(0f).setDuration(320).withEndAction(new Runnable() {
                public void run() { detach(); }
            }).start();
        } catch (Throwable t) { detach(); }
    }

    /** Zlozi pasik a zastavi polling. */
    public static void detach() {
        try {
            if (handler != null && poll != null) handler.removeCallbacks(poll);
            poll = null;
            if (gate != null && gate.getParent() instanceof ViewGroup) {
                ((ViewGroup) gate.getParent()).removeView(gate);
            }
        } catch (Throwable ignore) {}
        gate = null; bar = null; title = null; hint = null;
        progressFill = null; progressTrack = null; onion = null;
        blocking = false;
    }

    /** Appka sa otvara nanovo (Tor nabieha odznova) -> pasik sa zase ukaze. */
    public static void reset() { done = false; }

    /** Torova cibula - kreslena, ziadny obrazok v resources. */
    private static final class OnionView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean lit = false;

        OnionView(android.content.Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void setLit(boolean b) { lit = b; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h);
            float cx = w / 2f, cy = h / 2f + s * 0.06f;
            float r = s * 0.40f;
            p.setStrokeWidth(s * 0.055f);

            // stopka
            p.setColor(lit ? 0xFF7CE0A0 : 0xFF7D4698);
            Path stem = new Path();
            stem.moveTo(cx, cy - r);
            stem.cubicTo(cx + s * 0.06f, cy - r * 1.28f, cx + s * 0.14f, cy - r * 1.30f,
                    cx + s * 0.17f, cy - r * 1.18f);
            cv.drawPath(stem, p);

            // vrstvy cibule zvonka dnu
            float[] scale = { 1.0f, 0.72f, 0.46f, 0.22f };
            for (int i = 0; i < scale.length; i++) {
                int alpha = 0xFF - i * 0x18;
                int base = lit ? 0x7CE0A0 : 0x9A63FF;
                p.setColor((alpha << 24) | base);
                cv.drawPath(onionPath(cx, cy, r * scale[i]), p);
            }
        }

        private Path onionPath(float cx, float cy, float r) {
            Path o = new Path();
            o.moveTo(cx, cy - r);
            o.cubicTo(cx + r * 1.05f, cy - r * 0.50f, cx + r * 0.98f, cy + r * 0.62f, cx, cy + r);
            o.cubicTo(cx - r * 0.98f, cy + r * 0.62f, cx - r * 1.05f, cy - r * 0.50f, cx, cy - r);
            o.close();
            return o;
        }
    }
}
