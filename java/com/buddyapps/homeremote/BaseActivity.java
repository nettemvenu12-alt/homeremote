package com.buddyapps.homeremote;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared look for the AC and TV screens. */
public class BaseActivity extends Activity {
    void style(Button b, boolean active, String label) {
        if (label != null) b.setText(label);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(36);
        g.setColor(active ? Color.rgb(30, 110, 220) : Color.rgb(40, 46, 58));
        b.setBackground(g);
        b.setTextColor(Color.WHITE);
    }

    TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    TextView label(String s) {
        TextView t = text(s, 14, Color.GRAY);
        t.setPadding(12, 28, 0, 4);
        return t;
    }

    Button btn(String label, Runnable r) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); r.run(); });
        style(b, false, null);
        b.setMinHeight(170);
        b.setPadding(8, 24, 8, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(10, 10, 10, 10);
        b.setLayoutParams(lp);
        return b;
    }

    LinearLayout row(View... vs) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER);
        for (View v : vs) l.addView(v);
        return l;
    }

    final android.os.Handler holdHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Button that fires on touch-down and repeats while held, like a real remote (single clicks were missed by the TV). */
    Button hold(String label, Runnable action) {
        Button b = btn(label, null);
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> { action.run(); holdHandler.postDelayed(loop[0], 150); };
        b.setOnClickListener(null);
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                v.setPressed(true);
                action.run();
                holdHandler.postDelayed(loop[0], 450);
            } else if (e.getAction() == android.view.MotionEvent.ACTION_UP || e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.setPressed(false);
                holdHandler.removeCallbacks(loop[0]);
            }
            return true;
        });
        return b;
    }
}
