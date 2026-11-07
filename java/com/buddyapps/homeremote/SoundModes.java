package com.buddyapps.homeremote;

/** Equalizer band levels (millibels) for each sound mode, from the band centre frequencies. */
public class SoundModes {
    public static final String[] NAMES = {"Normal", "Bass", "Super Bass", "Movie", "Music", "Vocal"};

    /** 10-band graphic EQ centres (Hz) used with DynamicsProcessing. */
    public static final int[] BANDS10 = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};

    /** Same curves in dB for any band layout. */
    public static float[] db(String mode, int[] freqsHz, float maxDb) {
        short[] mB = levels(mode, freqsHz, (short) 1000);
        float[] out = new float[mB.length];
        for (int i = 0; i < mB.length; i++) out[i] = mB[i] / 1000f * maxDb;
        return out;
    }

    /** Wavelet-style auto attenuation: lower the input by the biggest boost so the EQ can't push past 0 dBFS. */
    public static float autoAttenuation(float[] gainsDb) {
        float max = 0;
        for (float g : gainsDb) max = Math.max(max, g);
        return -max;
    }

    /** freqsHz = centre of each band; max = equalizer's top level in mB. */
    public static short[] levels(String mode, int[] freqsHz, short max) {
        short[] out = new short[freqsHz.length];
        for (int i = 0; i < freqsHz.length; i++) {
            int f = freqsHz[i];
            double k;
            switch (mode) {
                case "Bass":  k = f < 250 ? 0.8 : f < 1000 ? 0.3 : 0; break;
                case "Super Bass": k = f < 250 ? 0.7 : f < 1000 ? 0.2 : -0.4; break; // lows +1.1*max over highs, shifted down for headroom (avoids cracking)
                case "Movie": k = f < 250 ? 0.6 : f >= 2000 && f < 8000 ? 0.4 : 0; break; // rumble + clear dialogue
                case "Music": k = f < 250 ? 0.4 : f >= 4000 ? 0.4 : f >= 1000 ? -0.1 : 0; break;
                case "Vocal": k = f < 250 ? -0.2 : f >= 1000 && f < 4000 ? 0.6 : 0; break;
                default:      k = 0;
            }
            out[i] = (short) Math.round(k * max);
        }
        return out;
    }

    // Self-check: java SoundModes.java
    public static void main(String[] a) {
        int[] f = {60, 230, 910, 3600, 14000}; // typical 5-band Android equalizer
        short max = 1500;
        check(levels("Normal", f, max), new short[]{0, 0, 0, 0, 0});
        check(levels("Bass", f, max), new short[]{1200, 1200, 450, 0, 0});
        check(levels("Super Bass", f, max), new short[]{1050, 1050, 300, -600, -600});
        check(levels("Vocal", f, max), new short[]{-300, -300, 0, 900, 0});
        short[] m = levels("Movie", f, max);
        if (m[0] <= 0 || m[3] <= 0 || m[2] != 0) throw new AssertionError("movie shape");
        for (String n : NAMES) for (short v : levels(n, f, max)) if (Math.abs(v) > max) throw new AssertionError("range " + n);
        float[] sb = db("Super Bass", BANDS10, 12f);
        if (Math.abs(sb[0] - 8.4f) > 0.01 || sb[9] >= 0) throw new AssertionError("db " + java.util.Arrays.toString(sb));
        if (autoAttenuation(sb) != -sb[0]) throw new AssertionError("atten");
        if (autoAttenuation(db("Normal", BANDS10, 12f)) != 0) throw new AssertionError("atten normal");
        System.out.println("OK");
    }

    static void check(short[] got, short[] want) {
        if (!java.util.Arrays.equals(got, want)) throw new AssertionError(java.util.Arrays.toString(got));
    }
}
