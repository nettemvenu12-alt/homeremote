package com.buddyapps.homeremote;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;

/** Hisense TV IR remote. Codes from Flipper-IRDB (TVs/Hisense). */
public class TvActivity extends BaseActivity {
    // name -> {set A command, set B command}; -1 = not on that set.
    // Set A: Hisense Google TV, NEC addr 0x04 (55U6K/U7G). Tested 2026-11-07: Vol, Menu(=settings), Home, Netflix, Exit, Hotstar, LiveTV, digits.
    // Set B: older/VIDAA, NEC-ext 0x00 0xBF (32A4HAU).
    static final Map<String, int[]> CODES = new HashMap<>();
    static {
        Object[][] t = {
            {"Power", 0x08, 0x0D}, {"Input", 0x0B, 0x12}, {"Home", 0x4A, 0x20}, {"Back", 0x04, 0x48},
            {"Ok", 0x5A, 0x15}, {"Up", 0x56, 0x16}, {"Down", 0x57, 0x17}, {"Left", 0x58, 0x19}, {"Right", 0x59, 0x18},
            {"Vol+", 0x02, 0x44}, {"Vol-", 0x03, 0x43}, {"Mute", 0x09, 0x0E}, {"Ch+", 0x00, 0x4A}, {"Ch-", 0x01, 0x4B},
            {"Menu", 0x43, 0x14}, {"PlayPause", 0x6B, 0xCA}, {"Exit", 0x4D, 0x5C}, {"LiveTV", 0x40, 0x21},
            {"Apps", 0x0F, -1}, /* tested OK */ {"Netflix", 0x4C, 0x2D}, {"YouTube", 0x49, 0xAA}, {"Prime", 0x47, 0xBB}, {"Hotstar", 0xCE, 0x6A},
            {"123", 0x6A, -1},
        };
        for (Object[] r : t) CODES.put((String) r[0], new int[]{(int) r[1], (int) r[2]});
        // Set A digits 0x10..0x19 tested on user's TV 2026-11-07.
        for (int d = 0; d <= 9; d++) CODES.put("" + d, new int[]{0x10 + d, d});
    }

    final Handler h = new Handler(Looper.getMainLooper());
    ConsumerIrManager ir;
    SharedPreferences prefs;
    TextView status;
    int set;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ir = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);
        prefs = getSharedPreferences("tv", MODE_PRIVATE);
        set = prefs.getInt("set", 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 150, 28, 40);
        root.setBackgroundColor(Color.rgb(16, 20, 28));

        Button power = btn("POWER", () -> key("Power"));
        style(power, true, null);
        root.addView(row(btn("❄ AC", () -> { startActivity(new Intent(this, MainActivity.class)); finish(); }),
                         power, btn("🔊", () -> startActivity(new Intent(this, SpeakerActivity.class))), btn("⋯ More", this::more)));
        status = text("", 14, Color.GRAY);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 6, 0, 6);
        root.addView(status);

        // Left: touchpad + keys. Right: OTT apps column.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(touchpad());
        left.addView(row(btn("Home", () -> key("Home")), btn("Back", () -> key("Back")), btn("Exit", () -> key("Exit"))));
        left.addView(row(hold("◀", "Left"), hold("▲", "Up"), hold("▼", "Down"), hold("▶", "Right")));
        left.addView(row(btn("Settings", () -> key("Menu")), btn("OK", () -> key("Ok")), btn("Input", () -> key("Input"))));
        left.addView(row(hold("Vol −", "Vol-"), hold("Vol +", "Vol+"), btn("Mute", () -> key("Mute"))));
        left.addView(row(hold("Ch −", "Ch-"), hold("Ch +", "Ch+"), btn("⏯", () -> key("PlayPause"))));

        LinearLayout apps = new LinearLayout(this);
        apps.setOrientation(LinearLayout.VERTICAL);
        for (String[] a : new String[][]{{"Netflix", "Netflix"}, {"YouTube", "YouTube"}, {"Prime", "Prime"},
                {"Hotstar", "Hotstar"}, {"Live TV", "LiveTV"}, {"All apps", "Apps"}}) {
            Button ab = btn(a[0], () -> key(a[1]));
            ab.setTextSize(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
            lp.setMargins(8, 10, 8, 10);
            ab.setLayoutParams(lp);
            apps.addView(ab);
        }

        LinearLayout main = new LinearLayout(this);
        main.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3));
        main.addView(apps, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(main);
        refresh();

        ScrollView sv = new ScrollView(this); // only scrolls on very small screens
        root.setFocusableInTouchMode(true);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.rgb(16, 20, 28));
        sv.addView(root);
        setContentView(sv);
    }

    /** Less-used things live here to keep the remote on one screen. */
    void more() {
        String[] items = {"Number pad 0–9", "Sleep timer 30 min", "Sleep timer 1 hr", "Sleep timer 2 hr", "Cancel sleep timer",
                "Code set: " + (set == 0 ? "A (Google TV)" : "B (older/VIDAA)") + " – switch"};
        new android.app.AlertDialog.Builder(this).setItems(items, (d, w) -> {
            if (w == 0) numbers();
            else if (w <= 4) setTimer(new int[]{0, 30, 60, 120, 0}[w]);
            else { set = 1 - set; prefs.edit().putInt("set", set).apply(); refresh(); }
        }).show();
    }

    void numbers() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setPadding(20, 20, 20, 20);
        g.setBackgroundColor(Color.rgb(16, 20, 28));
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"123", "0", "Ok"}};
        for (String[] r : rows) {
            Button[] bs = new Button[3];
            for (int i = 0; i < 3; i++) { String k = r[i]; bs[i] = btn(k.equals("123") ? "123 pad" : k.equals("Ok") ? "OK" : k, () -> key(k)); }
            g.addView(row(bs));
        }
        new android.app.AlertDialog.Builder(this).setView(g).setPositiveButton("Close", null).show();
    }

    void refresh() {
        long at = prefs.getLong("offAt", 0);
        status.setText("Hisense TV  •  code set " + (set == 0 ? "A" : "B") + "  •  phone volume keys work"
                + (at > System.currentTimeMillis()
                ? "\n⏰ TV off at " + android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(at)) : ""));
    }

    /** Sends one key; static so the sleep timer can use it. Returns false if not sent. */
    static boolean send(Context c, String name) {
        ConsumerIrManager ir = (ConsumerIrManager) c.getSystemService(CONSUMER_IR_SERVICE);
        if (ir == null || !ir.hasIrEmitter()) return false;
        int set = c.getSharedPreferences("tv", MODE_PRIVATE).getInt("set", 0);
        int cmd = CODES.get(name)[set];
        if (cmd < 0) return false;
        int[] p = set == 0 ? Nec.pattern(0x04, 0xFB, cmd) : Nec.pattern(0x00, 0xBF, cmd);
        // Power is a toggle: the trailing repeat burst made it switch on and straight back off
        if (name.equals("Power")) p = java.util.Arrays.copyOf(p, p.length - 4);
        ir.transmit(Nec.FREQ, p);
        return true;
    }

    void key(String name) {
        status.setText(send(this, name) ? "Sent " + name + " ✓" : name + " not available");
    }

    Button hold(String label, String name) { return hold(label, () -> key(name)); }

    View touchpad() {
        TextView pad = text("swipe  ◀ ▲ ▼ ▶   •   tap = OK", 15, Color.GRAY);
        pad.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(48);
        g.setColor(Color.rgb(28, 34, 46));
        pad.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 900);
        lp.setMargins(10, 10, 10, 10);
        pad.setLayoutParams(lp);
        GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) { buzz(pad); key("Ok"); return true; }
            @Override public boolean onFling(MotionEvent a, MotionEvent z, float vx, float vy) {
                float dx = z.getX() - a.getX(), dy = z.getY() - a.getY();
                buzz(pad);
                key(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? "Right" : "Left") : (dy > 0 ? "Down" : "Up"));
                return true;
            }
        });
        pad.setOnTouchListener((v, e) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true); // keep the page from scrolling while swiping
            return gd.onTouchEvent(e);
        });
        return pad;
    }


    void buzz(View v) { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_VOLUME_UP) { key("Vol+"); return true; }
        if (code == KeyEvent.KEYCODE_VOLUME_DOWN) { key("Vol-"); return true; }
        return super.onKeyDown(code, e);
    }

    void setTimer(int minutes) {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 1,
                new Intent(this, OffReceiver.class).putExtra("tv", true), PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        long at = minutes == 0 ? 0 : System.currentTimeMillis() + minutes * 60_000L;
        if (at > 0) {
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); }
            catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); }
        }
        prefs.edit().putLong("offAt", at).apply();
        refresh();
    }

    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
