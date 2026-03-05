package com.mystichorizons.mysticnametags.nameplate.glyph;

import java.awt.Color;

public final class TintPaletteCompat {

    private TintPaletteCompat() {}

    /**
     * Quantize to 9 steps per channel (0..255).
     * That yields 729 total tint effects.
     */
    public static int quantizeRgb(Color c) {
        int r = quantizeChannel(c.getRed());
        int g = quantizeChannel(c.getGreen());
        int b = quantizeChannel(c.getBlue());
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static int quantizeChannel(int v) {
        // Map 0..255 -> step 0..8 -> back to 0..255
        int step = (int) Math.round((v / 255.0) * 8.0);
        return (int) Math.round((step / 8.0) * 255.0);
    }
}