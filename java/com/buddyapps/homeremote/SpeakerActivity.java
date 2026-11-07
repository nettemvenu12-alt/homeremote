package com.buddyapps.homeremote;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Zebronics Juke Bar 3900 over Bluetooth: media keys, volume, phone-side EQ/bass, sleep timer. No IR codes exist for it. */
public class SpeakerActivity extends BaseActivity {
    // Kept static so the sound mode stays on after leaving this screen (released only when the app process dies).
    static DynamicsProcessing dp; // 10-band EQ + limiter (API 28+); falls back to the 5-band Equalizer
    static Equalizer eq;
    static BassBoost bass;
    static LoudnessEnhancer loud;
    static Virtualizer virt;

    AudioManager am;
    SharedPreferences prefs;
    TextView status, timerInfo, fxInfo;
    SeekBar volBar;
    final List<Button> modeBtns = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        prefs = getSharedPreferences("speaker", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 150, 28, 40);

        root.addView(row(btn("❄ AC", () -> go(MainActivity.class)), btn("📺 TV", () -> go(TvActivity.class))));
        root.addView(text("Zebronics Speaker", 22, Color.LTGRAY));
        status = text("", 15, Color.rgb(150, 200, 255));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 10, 0, 10);
        root.addView(status);

        Button play = big("⏯  Play / Pause", () -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        style(play, true, null);
        root.addView(row(big("⏮", () -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)), play, big("⏭", () -> media(KeyEvent.KEYCODE_MEDIA_NEXT))));

        root.addView(label("Volume"));
        volBar = new SeekBar(this);
        volBar.setMax(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volBar.setPadding(40, 30, 40, 30);
        volBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                if (user) { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); refresh(); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(volBar);
        root.addView(row(btn("Vol −", () -> vol(AudioManager.ADJUST_LOWER)), btn("Mute", () -> vol(AudioManager.ADJUST_TOGGLE_MUTE)),
                         btn("Vol +", () -> vol(AudioManager.ADJUST_RAISE))));

        // When the soundbar is fed by the TV (BT/AUX/HDMI-ARC) its level follows the TV, so send TV volume over IR.
        root.addView(label("Speaker on TV – point phone at the TV"));
        root.addView(row(hold("TV Vol −", () -> tvKey("Vol-")), btn("TV Mute", () -> tvKey("Mute")), hold("TV Vol +", () -> tvKey("Vol+"))));
        // Phone EQ can't reach TV→speaker audio; the TV's own Sound menu is the only bass/mode control then.
        root.addView(row(btn("🔉 TV Sound settings", this::tvSoundMenu)));
        root.addView(row(hold("◀", () -> tvKey("Left")), hold("▲", () -> tvKey("Up")), hold("▼", () -> tvKey("Down")),
                         hold("▶", () -> tvKey("Right")), btn("OK", () -> tvKey("Ok")), btn("Back", () -> tvKey("Back"))));

        root.addView(label("Sound mode (phone equalizer)"));
        LinearLayout modes = new LinearLayout(this);
        for (String m : concat(SoundModes.NAMES, "Custom")) {
            Button mb = btn(m, () -> setMode(m));
            mb.setTextSize(12);
            modeBtns.add(mb);
            modes.addView(mb);
        }
        root.addView(modes);
        root.addView(label("Bass boost"));
        SeekBar bassBar = new SeekBar(this);
        bassBar.setMax(1000);
        bassBar.setProgress(prefs.getInt("bass", 0));
        bassBar.setPadding(40, 30, 40, 30);
        bassBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) { if (user) setBass(v); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(bassBar);
        root.addView(label("Loudness boost (limiter keeps it from cracking)"));
        root.addView(slider(18, prefs.getInt("loudDb", 0), this::setLoud));
        Button customBtn = btn("🎚  Custom 10-band EQ  ▾", null);
        LinearLayout custom = new LinearLayout(this);
        custom.setOrientation(LinearLayout.VERTICAL);
        custom.setVisibility(View.GONE);
        customBtn.setOnClickListener(v -> custom.setVisibility(custom.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
        root.addView(row(customBtn));
        for (int i = 0; i < SoundModes.BANDS10.length; i++) {
            final int band = i;
            int f = SoundModes.BANDS10[i];
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(f >= 1000 ? (f / 1000) + "k" : "" + f, 13, Color.GRAY);
            name.setMinWidth(110);
            r.addView(name);
            SeekBar sb = slider(24, Math.round(prefs.getFloat("c" + i, 0) + 12), v -> {
                prefs.edit().putFloat("c" + band, v - 12).apply();
                setMode("Custom");
            });
            r.addView(sb, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            custom.addView(r);
        }
        root.addView(custom);
        root.addView(label("Balance  L ◀ ▶ R"));
        root.addView(slider(20, prefs.getInt("bal", 10), v -> { prefs.edit().putInt("bal", v).apply(); applyEq(); }));
        root.addView(label("Surround 3D (movies)"));
        root.addView(slider(1000, prefs.getInt("virt", 0), this::setVirt));
        fxInfo = text("", 13, Color.GRAY);
        root.addView(fxInfo);

        root.addView(label("Sleep timer – music pauses"));
        root.addView(row(btn("15 min", () -> setTimer(15)), btn("30 min", () -> setTimer(30)),
                         btn("1 hr", () -> setTimer(60)), btn("Cancel", () -> setTimer(0))));
        timerInfo = text("", 15, Color.rgb(150, 200, 255));
        root.addView(timerInfo);

        List<View> apps = new ArrayList<>();
        for (String[] a : new String[][]{{"YT Music", "com.google.android.apps.youtube.music"}, {"YouTube", "app.revanced.android.youtube"},
                {"YouTube", "com.google.android.youtube"}, {"Amazon Music", "com.amazon.mp3"}, {"Spotify", "com.spotify.music"}}) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(a[1]);
            boolean dup = false;
            for (View v : apps) if (((Button) v).getText().toString().equals(a[0])) dup = true;
            if (launch != null && !dup && apps.size() < 3) apps.add(btn(a[0], () -> startActivity(launch)));
        }
        if (!apps.isEmpty()) { root.addView(label("Open music app")); root.addView(row(apps.toArray(new View[0]))); }

        root.addView(row(btn("Bluetooth settings", () -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))));
        root.addView(label("Juke Bar 3900: input mode (BT/AUX/USB) – no IR codes published; use the soundbar's buttons."));

        ScrollView sv = new ScrollView(this);
        root.setFocusableInTouchMode(true);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.rgb(16, 20, 28));
        sv.addView(root);
        setContentView(sv);

        initFx();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    static String[] concat(String[] a, String x) { String[] r = java.util.Arrays.copyOf(a, a.length + 1); r[a.length] = x; return r; }

    /** Global (session 0) effects, as Wavelet does; each is optional because phones may refuse some. */
    void initFx() {
        if (dp == null && eq == null && android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                        true, SoundModes.BANDS10.length, false, 0, false, 0, true).build();
                dp = new DynamicsProcessing(0, 0, cfg);
                // fast limiter just under 0 dBFS: boosts and loudness can't clip ("crack")
                dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f));
                dp.setEnabled(true);
            } catch (RuntimeException e) { dp = null; }
        }
        if (dp == null && eq == null) {
            try { eq = new Equalizer(0, 0); eq.setEnabled(true); } catch (RuntimeException e) { eq = null; }
        }
        try { if (bass == null) { bass = new BassBoost(0, 0); bass.setEnabled(true); } } catch (RuntimeException e) { bass = null; }
        if (dp == null) {
            try { if (loud == null) { loud = new LoudnessEnhancer(0); loud.setEnabled(true); } } catch (RuntimeException e) { loud = null; }
        }
        try { if (virt == null) { virt = new Virtualizer(0, 0); virt.setEnabled(true); } } catch (RuntimeException e) { virt = null; }
        setMode(prefs.getString("mode", "Normal"));
        setBass(prefs.getInt("bass", 0));
        setVirt(prefs.getInt("virt", 0));
        String miss = (dp == null && eq == null ? " equalizer" : "") + (bass == null || !bass.getStrengthSupported() ? " bass boost" : "")
                + (virt == null || !virt.getStrengthSupported() ? " surround" : "");
        fxInfo.setText((dp != null ? "✓ 10-band EQ + limiter active (no cracking)" : eq != null ? "5-band EQ (no limiter on this phone)" : "")
                + (miss.isEmpty() ? "" : "\nNot supported on this phone:" + miss));
    }

    void setMode(String m) {
        prefs.edit().putString("mode", m).apply();
        applyEq();
        for (Button b : modeBtns) style(b, b.getText().toString().equals(m), null);
    }

    float[] gains() {
        String m = prefs.getString("mode", "Normal");
        if (!m.equals("Custom")) return SoundModes.db(m, SoundModes.BANDS10, 12f);
        float[] g = new float[SoundModes.BANDS10.length];
        for (int i = 0; i < g.length; i++) g[i] = prefs.getFloat("c" + i, 0);
        return g;
    }

    /** EQ curve + auto attenuation + loudness + balance, all as input gain in front of the limiter. */
    void applyEq() {
        float[] g = gains();
        float loudDb = prefs.getInt("loudDb", 0), bal = prefs.getInt("bal", 10) - 10;
        try {
            if (dp != null) {
                for (int i = 0; i < g.length; i++) {
                    float cutoff = i + 1 < g.length ? (float) Math.sqrt(SoundModes.BANDS10[i] * SoundModes.BANDS10[i + 1]) : 20000f;
                    dp.setPreEqBandAllChannelsTo(i, new DynamicsProcessing.EqBand(true, cutoff, g[i]));
                }
                // half attenuation: full made bass feel weaker (user, 2026-11-08); the limiter catches the remaining peaks
                float base = 0.5f * SoundModes.autoAttenuation(g) + loudDb;
                dp.setInputGainbyChannel(0, base - Math.max(0, bal));  // right-heavy balance lowers left
                dp.setInputGainbyChannel(1, base + Math.min(0, bal));
            } else if (eq != null) {
                short n = eq.getNumberOfBands();
                int[] f = new int[n];
                for (short i = 0; i < n; i++) f[i] = eq.getCenterFreq(i) / 1000; // mHz -> Hz
                short top = eq.getBandLevelRange()[1];
                float[] lg = SoundModes.db(prefs.getString("mode", "Normal").equals("Custom") ? "Normal" : prefs.getString("mode", "Normal"), f, top / 100f);
                for (short i = 0; i < n; i++) eq.setBandLevel(i, (short) Math.round(lg[i] * 100));
                if (loud != null) loud.setTargetGain(Math.round(Math.min(10, loudDb) * 100));
            }
        } catch (RuntimeException e) { fxInfo.setText("Equalizer error: " + e.getMessage()); }
    }

    SeekBar slider(int max, int value, java.util.function.IntConsumer onChange) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(value);
        sb.setPadding(40, 30, 40, 30);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) { if (user) onChange.accept(v); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        return sb;
    }

    void setLoud(int db) {
        prefs.edit().putInt("loudDb", db).apply();
        applyEq();
    }

    void setVirt(int v) {
        prefs.edit().putInt("virt", v).apply();
        try { if (virt != null && virt.getStrengthSupported()) virt.setStrength((short) v); }
        catch (RuntimeException e) { fxInfo.setText("Surround error: " + e.getMessage()); }
    }

    void setBass(int v) {
        prefs.edit().putInt("bass", v).apply();
        try { if (bass != null && bass.getStrengthSupported()) bass.setStrength((short) v); }
        catch (RuntimeException e) { fxInfo.setText("Bass boost error: " + e.getMessage()); }
    }

    Button big(String label, Runnable r) {
        Button b = btn(label, r);
        b.setTextSize(20);
        b.setMinHeight(220);
        return b;
    }

    /** Settings (0x43) → ▼ → OK. shortcut: assumes Sound is the 2nd item under Picture on the user's Hisense; re-check if the menu changes. */
    void tvSoundMenu() {
        String[] seq = {"Menu", "Down", "Ok"};
        for (int i = 0; i < seq.length; i++) { String k = seq[i]; holdHandler.postDelayed(() -> tvKey(k), i * 1200L); }
    }

    void tvKey(String k) { status.setText(TvActivity.send(this, k) ? "Sent TV " + k + " ✓" : "No IR emitter"); }

    void go(Class<?> c) { startActivity(new Intent(this, c)); finish(); }

    static void media(Context c, int code) {
        AudioManager am = (AudioManager) c.getSystemService(AUDIO_SERVICE);
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    void media(int code) { media(this, code); refresh(); }

    void vol(int dir) {
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI);
        refresh();
    }

    void setTimer(int minutes) {
        AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 2,
                new Intent(this, OffReceiver.class).putExtra("speaker", true), PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(pi);
        long at = minutes == 0 ? 0 : System.currentTimeMillis() + minutes * 60_000L;
        if (at > 0) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); }
            catch (SecurityException e) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); }
        }
        prefs.edit().putLong("offAt", at).apply();
        refresh();
    }

    void refresh() {
        String bt = null;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
            if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) bt = String.valueOf(d.getProductName()).replace("dontapplycevolume", ""); // MIUI prefixes this flag to the name
        int v = am.getStreamVolume(AudioManager.STREAM_MUSIC), max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volBar.setProgress(v);
        status.setText((bt != null ? "🔵 Connected: " + bt : "⚪ Bluetooth speaker not connected")
                + "   •   Volume " + (max > 0 ? v * 100 / max : 0) + "%" + (am.isMusicActive() ? "   •   ▶ playing" : ""));
        long at = prefs.getLong("offAt", 0);
        timerInfo.setText(at > System.currentTimeMillis()
                ? "⏰ Music pauses at " + android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(at)) : "");
    }
}
