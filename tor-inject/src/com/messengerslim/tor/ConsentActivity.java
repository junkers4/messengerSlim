package com.messengerslim.tor;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;

/**
 * Neviditelna aktivita: vypyta jednorazovy VPN suhlas a spusti Tor VPN sluzbu.
 * Spusta ju LifecycleWatcher pri prvom starte Messengera (ked este nie je suhlas).
 */
public class ConsentActivity extends Activity {
    private static final int REQ = 7;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        Intent p = VpnService.prepare(this);
        if (p != null) startActivityForResult(p, REQ);
        else { startSvc(); finish(); }
    }

    @Override protected void onActivityResult(int rc, int res, Intent d) {
        super.onActivityResult(rc, res, d);
        if (rc == REQ && res == RESULT_OK) startSvc();
        finish();
    }

    private void startSvc() {
        startForegroundService(new Intent(this, OrcaTorVpnService.class)
                .setAction(OrcaTorVpnService.ACTION_START));
    }
}
