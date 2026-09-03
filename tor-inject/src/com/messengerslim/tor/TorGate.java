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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Celoobrazovkova brana, kym Tor nabieha. Prekryje appku uplne a zjeda vsetky doteky,
 * takze pouzivatel nemoze nic spustit skor, nez tunel stoji - ziadna prevadzka pocas
 * bootstrapu. (Siet je fail-closed aj na urovni tunela; toto je viditelna polovica.)
 *
 * Stav sa cita z TorStatus (subor v filesDir), lebo VpnService bezi v procese :tor.
 */
public final class TorGate {

    private static final long POLL_MS = 400;
    private static final long GIVE_UP_MS = 90_000;

    private static FrameLayout gate;
    private static TextView title, percentView, hint;
    private static View progressFill;
    private static View progressTrack;
    private static OnionView onion;
    private static Handler handler;
    private static Runnable poll;
    private static boolean done = false;
    private static long shownAt = 0;

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
            FrameLayout box = new FrameLayout(a);
            box.setBackgroundColor(0xFF0B0B10);
            box.setClickable(true);
            box.setFocusable(true);
            box.setOnTouchListener(new View.OnTouchListener() {   // nic neprejde do appky
                public boolean onTouch(View v, MotionEvent e) { return true; }
            });

            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);

            onion = new OnionView(a);
            int size = (int) (108 * d);
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(size, size);
            olp.bottomMargin = (int) (26 * d);
            col.addView(onion, olp);

            title = new TextView(a);
            title.setText("Connecting to Tor");
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
            title.setGravity(Gravity.CENTER);
            col.addView(title);

            percentView = new TextView(a);
            percentView.setText("");
            percentView.setTextColor(0xFFB388FF);
            percentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            percentView.setGravity(Gravity.CENTER);
            percentView.setPadding(0, (int) (10 * d), 0, (int) (14 * d));
            col.addView(percentView);

            FrameLayout track = new FrameLayout(a);
            track.setBackgroundColor(0xFF241C33);
            View f = new View(a);
            f.setBackgroundColor(0xFF9A63FF);
            track.addView(f, new FrameLayout.LayoutParams(0, (int) (3 * d)));
            LinearLayout.LayoutParams tlp =
                    new LinearLayout.LayoutParams((int) (220 * d), (int) (3 * d));
            col.addView(track, tlp);
            progressTrack = track;
            progressFill = f;

            hint = new TextView(a);
            hint.setText("No traffic leaves this device until the tunnel is up.");
            hint.setTextColor(0xFF7C8088);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding((int) (36 * d), (int) (22 * d), (int) (36 * d), 0);
            col.addView(hint);

            box.addView(col, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            root.addView(box, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            box.bringToFront();
            gate = box;
            shownAt = System.currentTimeMillis();
            startPolling(a);
        } catch (Throwable ignore) {}
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
                        if (title != null) title.setText("Connected");
                        if (percentView != null) percentView.setText("100 %");
                        if (hint != null) hint.setText("All traffic now goes through Tor.");
                        setProgress(1f);
                        if (onion != null) onion.setLit(true);
                        handler.postDelayed(new Runnable() {
                            public void run() { fadeOut(); }
                        }, 700);
                        return;
                    }
                    int pct = TorStatus.percent(s);
                    if (percentView != null) percentView.setText(pct >= 0 ? pct + " %" : "starting…");
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
            if (title != null) title.setText("Tor is taking longer than usual");
            if (percentView != null) percentView.setText("");
            if (hint != null) {
                hint.setText("Still no traffic is leaving this device.\n\nTap to try again.");
            }
            if (gate != null) {
                gate.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            a.startService(new Intent(a, OrcaTorVpnService.class)
                                    .setAction(OrcaTorVpnService.ACTION_START));
                        } catch (Throwable ignore) {}
                        shownAt = System.currentTimeMillis();
                        if (title != null) title.setText("Connecting to Tor");
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

    /** Zlozi branu a zastavi polling. */
    public static void detach() {
        try {
            if (handler != null && poll != null) handler.removeCallbacks(poll);
            poll = null;
            if (gate != null && gate.getParent() instanceof ViewGroup) {
                ((ViewGroup) gate.getParent()).removeView(gate);
            }
        } catch (Throwable ignore) {}
        gate = null; title = null; percentView = null; hint = null;
        progressFill = null; progressTrack = null; onion = null;
    }

    /** Appka sa otvara nanovo (Tor nabieha odznova) -> brana sa zase ukaze. */
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
