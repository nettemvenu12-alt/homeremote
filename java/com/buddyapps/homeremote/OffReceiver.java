package com.buddyapps.homeremote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Sleep timer: turns the AC (or, with extra "tv", the TV) off, or pauses the speaker (extra "speaker"). */
public class OffReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (i.getBooleanExtra("speaker", false)) { // pause, don't toggle: PLAY_PAUSE could start music again
            SpeakerActivity.media(c, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE);
            c.getSharedPreferences("speaker", Context.MODE_PRIVATE).edit().putLong("offAt", 0).apply();
            return;
        }
        boolean tv = i.getBooleanExtra("tv", false);
        // TV power is a toggle on IR; the timer assumes the TV is still on.
        if (tv) TvActivity.send(c, "Power"); else MainActivity.setPower(c, false);
        c.getSharedPreferences(tv ? "tv" : "ac", Context.MODE_PRIVATE).edit().putLong("offAt", 0).apply();
    }
}
