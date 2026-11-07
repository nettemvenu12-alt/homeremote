package com.buddyapps.homeremote;

/** Samsung AC IR protocol (14-byte state, 21-byte for power on/off). Timings and layout from IRremoteESP8266 ir_Samsung. */
public class SamsungAc {
    public static final int FREQ = 38000;
    public boolean on = false;
    public int temp = 24;      // 16..30
    public int mode = 1;       // 0 Auto, 1 Cool, 2 Dry, 3 Fan, 4 Heat
    public int fan = 0;        // 0 Auto, 2 Low, 4 Med, 5 High
    public int swing = 0b111;  // 0b111 Off, 0b010 Up-down, 0b011 Left-right, 0b100 Both
    public int special = 0;    // byte10 FanSpecial: 0 none, 0b011 Turbo, 0b101 WindFree, 0b111 Eco
    public boolean quiet = false, display = true, ion = false;

    static final int[] RESET = {0x02, 0x92, 0x0F, 0, 0, 0, 0xF0, 0x01, 0xE2, 0xFE, 0x71, 0x40, 0x11, 0xF0};
    static final int[] MIDDLE = {0x01, 0xD2, 0x0F, 0, 0, 0, 0};

    public int[] state() {
        int[] s = RESET.clone();
        int p = on ? 0x30 : 0;
        s[6] = (s[6] & ~0x30) | p;
        s[13] = (s[13] & ~0x30) | p;
        s[11] = (s[11] & 0x0F) | ((temp - 16) << 4);
        s[12] = (s[12] & ~0x70) | (mode << 4);
        s[12] = (s[12] & ~0x0E) | (fan << 1);
        s[9] = (s[9] & ~0x70) | (swing << 4);
        // User's AC ignores Turbo when the Powerful FanSpecial bits are set; Turbo is fan 7 alone (tested 2026-11-07).
        int fs = special == 0b011 ? 0 : special;
        s[10] = (s[10] & ~0x1E) | (fs << 1) | (display ? 0x10 : 0);
        s[5] = (s[5] & ~0x20) | (quiet ? 0x20 : 0);
        s[11] = (s[11] & ~0x01) | (ion ? 1 : 0);
        checksum(s, 0);
        checksum(s, 7);
        return s;
    }

    static void checksum(int[] s, int o) {
        int sum = Integer.bitCount(s[o]) + Integer.bitCount(s[o + 1] & 0x0F) + Integer.bitCount((s[o + 2] >> 4) & 0x0F);
        for (int i = 3; i < 7; i++) sum += Integer.bitCount(s[o + i]);
        int c = ~sum & 0xFF;
        s[o + 1] = (s[o + 1] & 0x0F) | ((c & 0x0F) << 4);
        s[o + 2] = (s[o + 2] & 0xF0) | (c >> 4);
    }

    /** Power changes go out as the 21-byte form, like the real remote. */
    public int[] bytes(boolean powerChange) {
        int[] s = state();
        if (!powerChange) return s;
        int[] e = new int[21];
        System.arraycopy(s, 0, e, 0, 7);
        System.arraycopy(MIDDLE, 0, e, 7, 7);
        System.arraycopy(s, 7, e, 14, 7);
        return e;
    }

    public static int[] pattern(int[] data) {
        java.util.ArrayList<Integer> p = new java.util.ArrayList<>();
        p.add(690); p.add(17844);
        for (int sec = 0; sec < data.length; sec += 7) {
            p.add(3086); p.add(8864);
            for (int i = sec; i < sec + 7; i++)
                for (int b = 0; b < 8; b++) { p.add(586); p.add(((data[i] >> b) & 1) == 1 ? 1432 : 436); }
            p.add(586);
            if (sec + 7 < data.length) p.add(2886);
        }
        int[] r = new int[p.size()];
        for (int i = 0; i < r.length; i++) r[i] = p.get(i);
        return r;
    }

    // Self-check against known-good vectors: java SamsungAc.java
    public static void main(String[] a) {
        SamsungAc ac = new SamsungAc();
        ac.on = true; ac.temp = 20; ac.mode = 1; ac.fan = 0;
        check(ac.state(), RESET);
        ac.on = false; ac.temp = 24;
        check(ac.bytes(true), new int[]{0x02, 0xB2, 0x0F, 0, 0, 0, 0xC0, 0x01, 0xD2, 0x0F, 0, 0, 0, 0, 0x01, 0x02, 0xFF, 0x71, 0x80, 0x11, 0xC0});
        if (pattern(ac.bytes(true)).length % 2 != 1) throw new AssertionError("pattern must end on a mark");
        ac.on = true; ac.special = 0b011; ac.fan = 7; ac.quiet = true; ac.display = false; ac.ion = true; ac.swing = 0b100;
        int[] s = ac.state();
        if (((s[10] >> 1) & 7) != 0 || (s[10] & 0x10) != 0 || (s[5] & 0x20) == 0 || (s[11] & 1) != 1
                || ((s[9] >> 4) & 7) != 0b100 || ((s[12] >> 1) & 7) != 7) throw new AssertionError("feature bits");
        int[] c = s.clone(); checksum(c, 0); checksum(c, 7);
        check(c, s);
        System.out.println("OK");
    }

    static void check(int[] got, int[] want) {
        if (!java.util.Arrays.equals(got, want)) throw new AssertionError(java.util.Arrays.toString(got));
    }
}
