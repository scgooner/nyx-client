package dev.nyx.util;

import dev.nyx.module.ModuleManager;
import dev.nyx.modules.misc.ClickGui;

public final class Colors {
    // Dark theme palette (ARGB)
    public static final int WINDOW = 0xF2111217;
    public static final int SIDEBAR = 0xFF0B0C10;
    public static final int PANEL = 0xFF131419;
    public static final int CARD = 0xFF181A21;
    public static final int CARD_HOVER = 0xFF20232C;
    public static final int FIELD = 0xFF1B1D25;
    public static final int TRACK = 0xFF2A2D38;
    public static final int DIVIDER = 0x14FFFFFF;
    public static final int TEXT = 0xFFECEEF3;
    public static final int TEXT_2 = 0xFFA3A8B5;
    public static final int MUTED = 0xFF686D7C;
    public static final int DANGER = 0xFFF0506E;

    private Colors() {}

    public static int accent() {
        ClickGui gui = ModuleManager.get(ClickGui.class);
        return gui.accentColor(0);
    }

    /** Accent with a hue offset, used for rainbow gradients in the module list. */
    public static int accent(int offsetMs) {
        return ModuleManager.get(ClickGui.class).accentColor(offsetMs);
    }

    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        int i = (int) (h * 6);
        float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    public static float hue(int argb) {
        float r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        if (d == 0) return 0;
        float h;
        if (max == r) h = ((g - b) / d) % 6;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        h /= 6;
        return h < 0 ? h + 1 : h;
    }

    public static int lerp(int a, int b, float t) {
        t = Anim.clamp01(t);
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static int alpha(int argb, float alpha) {
        int a = (int) ((argb >>> 24) * Anim.clamp01(alpha));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
