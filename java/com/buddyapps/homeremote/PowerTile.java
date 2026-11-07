package com.buddyapps.homeremote;

import android.content.Context;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: AC on/off from the notification panel. */
public class PowerTile extends TileService {
    @Override public void onStartListening() { update(); }

    @Override public void onClick() {
        boolean on = !getSharedPreferences("ac", Context.MODE_PRIVATE).getBoolean("on", false);
        MainActivity.setPower(this, on);
        update();
    }

    void update() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = getSharedPreferences("ac", Context.MODE_PRIVATE).getBoolean("on", false);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(on ? "AC on" : "AC off");
        t.updateTile();
    }
}
