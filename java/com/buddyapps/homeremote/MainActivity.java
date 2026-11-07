package com.buddyapps.homeremote;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends BaseActivity {
    static final String[] MODES = {"Auto", "Cool ❄", "Dry 💧", "Fan 🌀", "Heat 🔥"};
    static final int[] FANS = {0, 2, 4, 5};
    static final String[] FAN_NAMES = {"Auto", "Low", "Medium", "High"};
    static final int[] SWINGS = {0b111, 0b010, 0b011, 0b100};
    static final String[] SWING_NAMES = {"Off", "Up-Down", "Left-Right", "Both"};
    static final int TURBO = 0b011, WINDFREE = 0b101, ECO = 0b111;

    ConsumerIrManager ir;
    SharedPreferences prefs;
    SamsungAc ac = new SamsungAc();
    int fanIdx = 0, swingIdx = 0;
    TextView temp, info, status;
    Button power, mode, fan, swing, turbo, quiet, eco, windfree, display, ion;
    TextView timerInfo;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ir = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);
        prefs = getSharedPreferences("ac", MODE_PRIVATE);
        load();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 170, 36, 60); // Android 15 draws edge-to-edge, clear the status bar
        root.setBackgroundColor(Color.rgb(16, 20, 28));

        root.addView(row(btn("📺 TV remote", () -> startActivity(new Intent(this, TvActivity.class))), btn("🔊 Speaker", () -> startActivity(new Intent(this, SpeakerActivity.class)))));
        TextView title = text("Samsung AC", 22, Color.LTGRAY);
        root.addView(title);
        temp = text("", 84, Color.WHITE);
        temp.setGravity(Gravity.CENTER);
        root.addView(temp);
        info = text("", 18, Color.rgb(150, 200, 255));
        info.setGravity(Gravity.CENTER);
        root.addView(info);
        status = text("", 15, Color.GRAY);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 8, 0, 24);
        root.addView(status);

        power = btn("", () -> { ac.on = !ac.on; send(true); });
        power.setTextSize(24);
        root.addView(row(power));
        Button down = btn("−", () -> { ac.temp = Math.max(16, ac.temp - 1); send(false); });
        Button up = btn("+", () -> { ac.temp = Math.min(30, ac.temp + 1); send(false); });
        for (Button t : new Button[]{down, up}) { t.setTextSize(34); style(t, false, null); }
        root.addView(row(down, up));
        root.addView(label("Mode / Fan / Swing"));
        mode = btn("", () -> { ac.mode = (ac.mode + 1) % MODES.length; send(false); });
        fan = btn("", () -> {
            fanIdx = (fanIdx + 1) % FANS.length; ac.fan = FANS[fanIdx];
            if (ac.special == TURBO) ac.special = 0;
            send(false);
        });
        swing = btn("", () -> { swingIdx = (swingIdx + 1) % SWINGS.length; ac.swing = SWINGS[swingIdx]; send(false); });
        root.addView(row(mode, fan, swing));
        root.addView(label("Special"));
        turbo = btn("Turbo", () -> {
            boolean on = ac.special != TURBO;
            ac.special = on ? TURBO : 0;
            ac.fan = on ? 7 : FANS[fanIdx];
            if (on) ac.quiet = false;
            send(false);
        });
        quiet = btn("Quiet", () -> {
            ac.quiet = !ac.quiet;
            if (ac.quiet) { ac.special = ac.special == TURBO ? 0 : ac.special; fanIdx = 0; ac.fan = 0; }
            send(false);
        });
        eco = btn("Eco", () -> { ac.special = ac.special == ECO ? 0 : ECO; send(false); });
        windfree = btn("WindFree", () -> { ac.special = ac.special == WINDFREE ? 0 : WINDFREE; send(false); });
        root.addView(row(turbo, quiet));
        root.addView(row(eco, windfree));
        display = btn("Display light", () -> { ac.display = !ac.display; send(false); });
        ion = btn("Virus Doctor", () -> { ac.ion = !ac.ion; send(false); });
        root.addView(row(display, ion));

        root.addView(label("Presets"));
        root.addView(row(
            btn("Night 🌙", () -> preset(26, 0, SWINGS[1], 0, true, false)),
            btn("Fast cool ⚡", () -> preset(18, 0, SWINGS[0], TURBO, false, true)),
            btn("Comfort 🙂", () -> preset(24, 0, SWINGS[1], 0, false, true))));

        root.addView(label("Sleep timer – AC turns off (keep phone pointed at AC)"));
        root.addView(row(btn("30 min", () -> setTimer(30)), btn("1 hr", () -> setTimer(60)),
                         btn("2 hr", () -> setTimer(120)), btn("Cancel", () -> setTimer(0))));
        timerInfo = text("", 15, Color.rgb(150, 200, 255));
        timerInfo.setPadding(12, 8, 0, 0);
        root.addView(timerInfo);

        ScrollView sv = new ScrollView(this);
        root.setFocusableInTouchMode(true); // keeps ScrollView from jumping to the first button
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.rgb(16, 20, 28));
        sv.addView(root);
        setContentView(sv);
        refresh(ir != null && ir.hasIrEmitter() ? "Point phone at the AC" : "This phone has no IR emitter");
    }

    /** One tap: turn on in Cool with these settings. */
    void preset(int t, int fIdx, int sw, int special, boolean quietOn, boolean displayOn) {
        boolean wasOn = ac.on;
        ac.on = true; ac.mode = 1; ac.temp = t; fanIdx = fIdx; ac.swing = sw; swingIdx = indexOf(SWINGS, sw);
        ac.special = special; ac.fan = special == TURBO ? 7 : FANS[fanIdx];
        ac.quiet = quietOn; ac.display = displayOn;
        send(!wasOn);
    }

    static int indexOf(int[] a, int v) { for (int i = 0; i < a.length; i++) if (a[i] == v) return i; return 0; }

    void setTimer(int minutes) {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(this, OffReceiver.class), PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        long at = minutes == 0 ? 0 : System.currentTimeMillis() + minutes * 60_000L;
        if (at > 0) {
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); }
            catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); } // inexact if exact alarms are denied
        }
        prefs.edit().putLong("offAt", at).apply();
        refresh(at > 0 ? "Timer set" : "Timer cancelled");
    }

    /** Used by the timer and the Quick Settings tile. Returns false without an IR emitter. */
    static boolean setPower(Context c, boolean on) {
        ConsumerIrManager ir = (ConsumerIrManager) c.getSystemService(CONSUMER_IR_SERVICE);
        if (ir == null || !ir.hasIrEmitter()) return false;
        SharedPreferences p = c.getSharedPreferences("ac", MODE_PRIVATE);
        SamsungAc a = fromPrefs(p);
        a.on = on;
        ir.transmit(SamsungAc.FREQ, SamsungAc.pattern(a.bytes(true)));
        p.edit().putBoolean("on", on).apply();
        return true;
    }

    @Override protected void onResume() {
        super.onResume();
        load(); // the timer or tile may have changed the state
        refresh(status.getText().toString());
    }

    void send(boolean powerChange) {
        if (ir == null || !ir.hasIrEmitter()) { refresh("No IR emitter"); return; }
        if (!ac.on && !powerChange) { save(); refresh("AC is off – setting saved, tap POWER"); return; }
        ir.transmit(SamsungAc.FREQ, SamsungAc.pattern(ac.bytes(powerChange)));
        save();
        refresh("Sent ✓");
    }

    void refresh(String msg) {
        temp.setText(ac.temp + "°");
        info.setText(MODES[ac.mode] + "   •   Fan " + (ac.special == TURBO ? "Turbo" : FAN_NAMES[fanIdx]));
        status.setText(msg);
        style(power, ac.on, ac.on ? "POWER  •  ON" : "POWER  •  OFF");
        style(mode, false, "Mode\n" + MODES[ac.mode]);
        style(fan, false, "Fan\n" + FAN_NAMES[fanIdx]);
        style(swing, ac.swing != SWINGS[0], "Swing\n" + SWING_NAMES[swingIdx]);
        style(turbo, ac.special == TURBO, null);
        style(quiet, ac.quiet, null);
        style(eco, ac.special == ECO, null);
        style(windfree, ac.special == WINDFREE, null);
        style(display, ac.display, null);
        style(ion, ac.ion, null);
        long at = prefs.getLong("offAt", 0);
        timerInfo.setText(at > System.currentTimeMillis()
                ? "⏰ AC off at " + android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(at)) : "");
    }

    void load() {
        ac = fromPrefs(prefs);
        fanIdx = prefs.getInt("fanIdx", 0);
        swingIdx = prefs.getInt("swingIdx", 0);
    }

    static SamsungAc fromPrefs(SharedPreferences prefs) {
        SamsungAc ac = new SamsungAc();
        int fanIdx = prefs.getInt("fanIdx", 0), swingIdx = prefs.getInt("swingIdx", 0);
        ac.on = prefs.getBoolean("on", false);
        ac.temp = prefs.getInt("temp", 24);
        ac.mode = prefs.getInt("mode", 1);
        ac.special = prefs.getInt("special", 0);
        ac.quiet = prefs.getBoolean("quiet", false);
        ac.display = prefs.getBoolean("display", true);
        ac.ion = prefs.getBoolean("ion", false);
        ac.fan = ac.special == TURBO ? 7 : FANS[fanIdx];
        ac.swing = SWINGS[swingIdx];
        return ac;
    }

    void save() { saveState(prefs, ac, fanIdx, swingIdx); }

    static void saveState(SharedPreferences prefs, SamsungAc ac, int fanIdx, int swingIdx) {
        prefs.edit().putBoolean("on", ac.on).putInt("temp", ac.temp).putInt("mode", ac.mode)
                .putInt("fanIdx", fanIdx).putInt("swingIdx", swingIdx).putInt("special", ac.special)
                .putBoolean("quiet", ac.quiet).putBoolean("display", ac.display).putBoolean("ion", ac.ion).apply();
    }


}
