package com.messengerslim.panel;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * Uprace inbox: skryje pas "Notifications are currently off" (notifikacie su vypnute zamerne,
 * R4), plavajuce tlacidlo Meta AI a pole "Ask Meta AI or search"; nadpis "messenger" prekresli na "spravaren". Bezi len par krat po vlozeni panelu - ziadny periodicky
 * sken hierarchie (R8).
 */
final class Declutter {

    /** id_0x7f0b0d04: TextView pasu "Notifications are currently off. Tap to turn on." (584) */
    private static final int NOTIF_BANNER_ID = 0x7f0b0d04;
    private static final long[] AT_MS = { 0, 1500, 4000 };

    private Declutter() {}

    static void schedule(final View anyInInbox) {
        for (long ms : AT_MS) {
            anyInInbox.postDelayed(new Runnable() {
                public void run() { once(anyInInbox.getRootView()); }
            }, ms);
        }
    }

    private static void once(View root) {
        try {
            View banner = root.findViewById(NOTIF_BANNER_ID);
            if (banner instanceof android.widget.TextView
                    && String.valueOf(((android.widget.TextView) banner).getText()).startsWith("Notifications are")) {
                banner.setVisibility(View.GONE);
            }
            ArrayList<View> found = new ArrayList<>();
            root.findViewsWithText(found, "Meta AI", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
            for (View v : found) {
                // len samotne tlacidlo "Meta AI", nie vyhladavacie pole "Ask Meta AI or search"
                if ("Meta AI".contentEquals(String.valueOf(v.getContentDescription()))) {
                    v.setVisibility(View.GONE);
                }
            }
            // vyhladavacie pole "Ask Meta AI or search" - user ho nechce
            found.clear();
            root.findViewsWithText(found, "Ask Meta AI", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
            for (View v : found) {
                if (String.valueOf(v.getContentDescription()).startsWith("Ask Meta AI")) v.setVisibility(View.GONE);
            }
            found.clear();
            root.findViewsWithText(found, "Messenger", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
            int[] loc = new int[2];
            for (View v : found) {
                v.getLocationOnScreen(loc);
                // len nadpis hore v inboxe (Litho ho kresli ako obrazok, nie TextView)
                if ("Messenger".contentEquals(String.valueOf(v.getContentDescription()))
                        && loc[1] < v.getResources().getDisplayMetrics().heightPixels / 5
                        && !(v.getForeground() instanceof Wordmark)) {
                    v.setForeground(new Wordmark(night(v)));
                    v.setContentDescription("Spravaren");
                }
            }
        } catch (Throwable ignore) {}
    }

    private static boolean night(View v) {
        return (v.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Zakryje Messengerov nadpis farbou pozadia a napise "spravaren" rovnako velko. */
    static final class Wordmark extends Drawable {
        private final Paint bg = new Paint(), fg = new Paint(Paint.ANTI_ALIAS_FLAG);

        Wordmark(boolean night) {
            bg.setColor(night ? 0xFF000000 : 0xFFFFFFFF);
            fg.setColor(night ? 0xFFFFFFFF : 0xFF000000);
            fg.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            fg.setTextAlign(Paint.Align.LEFT);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawRect(b, bg);
            fg.setTextSize(b.height() * 0.40f);
            Paint.FontMetrics fm = fg.getFontMetrics();
            float y = b.centerY() - (fm.ascent + fm.descent) / 2f;
            c.drawText("spravaren", b.left, y, fg);
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
