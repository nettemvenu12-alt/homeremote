package com.buddyapps.homeremote;

/** NEC IR protocol: 9ms/4.5ms leader, 32 bits LSB first, stop mark. */
public class Nec {
    public static final int FREQ = 38000;

    /** b0,b1 = address bytes as sent; cmd byte is followed by its inverse. */
    public static int[] pattern(int b0, int b1, int cmd) {
        int[] bytes = {b0, b1, cmd, ~cmd & 0xFF};
        // frame + one NEC repeat burst: a single short frame was missed by the TV from the speaker screen
        int[] p = new int[2 + 64 + 1 + 4];
        p[0] = 9000; p[1] = 4500;
        int i = 2;
        for (int b : bytes)
            for (int k = 0; k < 8; k++) { p[i++] = 560; p[i++] = ((b >> k) & 1) == 1 ? 1690 : 560; }
        p[i++] = 560;
        p[i++] = 40000; p[i++] = 9000; p[i++] = 2250; p[i] = 560; // repeat code ~108 ms after frame start
        return p;
    }

    // Self-check: java Nec.java
    public static void main(String[] a) {
        int[] p = pattern(0x04, 0xFB, 0x08);
        if (p.length != 71 || p[0] != 9000 || p[p.length - 1] != 560 || p[68] != 9000) throw new AssertionError("shape");
        // 0x04 LSB first -> bits 0,0,1,... : third bit is a 1
        if (p[3] != 560 || p[5] != 560 || p[7] != 1690) throw new AssertionError("bit order");
        // inverse of 0x08 is 0xF7 -> first bit of 4th byte is 1
        if (p[2 + 48 + 1] != 1690) throw new AssertionError("inverse");
        System.out.println("OK");
    }
}
