package com.messengerslim.panel;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * Uprace inbox: skryje pas "Notifications are currently off" (notifikacie su vypnute zamerne,
 * R4) a plavajuce tlacidlo Meta AI. Bezi len par krat po vlozeni panelu - ziadny periodicky
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
        } catch (Throwable ignore) {}
    }
}
