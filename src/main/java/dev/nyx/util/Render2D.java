package dev.nyx.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 2D drawing in GUI coordinates with shapes rasterised at real screen-pixel resolution, so rounded
 * corners stay smooth at every GUI scale. Edge pixels get partial alpha for anti-aliasing.
 * {@link #alpha} is a global multiplier used for fade animations.
 */
public final class Render2D {
    public static float alpha = 1f;

    private Render2D() {}

    public static float scale() {
        return (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static Font font() { return Minecraft.getInstance().font; }

    private static int col(int argb) { return Colors.alpha(argb, alpha); }

    /** Snap a GUI coordinate to the nearest device pixel. */
    public static float snap(float v) {
        float s = scale();
        return Math.round(v * s) / s;
    }

    public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
        roundRect(g, x, y, w, h, 0, color);
    }

    public static void roundRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
        int c = col(color);
        if ((c >>> 24) == 0 || w <= 0 || h <= 0) return;
        float s = scale();
        int X = Math.round(x * s), Y = Math.round(y * s);
        int W = Math.round((x + w) * s) - X, H = Math.round((y + h) * s) - Y;
        if (W <= 0 || H <= 0) return;
        int R = Math.min(Math.round(radius * s), Math.min(W, H) / 2);

        g.pose().pushMatrix();
        g.pose().scale(1f / s, 1f / s);
        if (R <= 0) {
            g.fill(X, Y, X + W, Y + H, c);
        } else {
            g.fill(X, Y + R, X + W, Y + H - R, c);
            for (int i = 0; i < R; i++) {
                double dy = R - i - 0.5;
                double inset = R - Math.sqrt(Math.max(0, (double) R * R - dy * dy));
                int full = (int) Math.ceil(inset);
                float coverage = (float) (full - inset);
                int top = Y + i, bottom = Y + H - 1 - i;
                g.fill(X + full, top, X + W - full, top + 1, c);
                g.fill(X + full, bottom, X + W - full, bottom + 1, c);
                if (full > 0 && coverage > 0.03f) {
                    int edge = Colors.alpha(c, coverage);
                    g.fill(X + full - 1, top, X + full, top + 1, edge);
                    g.fill(X + W - full, top, X + W - full + 1, top + 1, edge);
                    g.fill(X + full - 1, bottom, X + full, bottom + 1, edge);
                    g.fill(X + W - full, bottom, X + W - full + 1, bottom + 1, edge);
                }
            }
        }
        g.pose().popMatrix();
    }

    /** Rounded rect with only the left corners rounded (used for the sidebar). */
    public static void roundRectLeft(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
        roundRect(g, x, y, Math.min(w, radius * 2 + 1), h, radius, color);
        rect(g, x + radius, y, w - radius, h, color);
    }

    public static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
        roundRect(g, cx - r, cy - r, r * 2, r * 2, r, color);
    }

    /** Soft drop shadow made of stacked translucent rounded rects. */
    public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float size, int strength) {
        int layers = 7;
        for (int i = layers; i >= 1; i--) {
            float t = i / (float) layers;
            float grow = size * t;
            int a = (int) (strength * (1 - t) * (1 - t) / 2.2f) + 1;
            roundRect(g, x - grow, y - grow + size * 0.35f, w + grow * 2, h + grow * 2, radius + grow, Colors.withAlpha(0, a));
        }
    }

    /** 1px rounded outline (drawn as four thin strips plus corner arcs approximated by a ring). */
    public static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
        float px = 1f / scale();
        rect(g, x + radius, y, w - radius * 2, px, color);
        rect(g, x + radius, y + h - px, w - radius * 2, px, color);
        rect(g, x, y + radius, px, h - radius * 2, color);
        rect(g, x + w - px, y + radius, px, h - radius * 2, color);
    }

    public static void text(GuiGraphicsExtractor g, String s, float x, float y, int color) {
        int c = col(color);
        if ((c >>> 24) < 8 || s.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().translate(snap(x), snap(y));
        g.text(font(), s, 0, 0, c, false);
        g.pose().popMatrix();
    }

    public static void textShadow(GuiGraphicsExtractor g, String s, float x, float y, int color) {
        int c = col(color);
        if ((c >>> 24) < 8 || s.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().translate(snap(x), snap(y));
        g.text(font(), s, 0, 0, c, true);
        g.pose().popMatrix();
    }

    public static void textCentered(GuiGraphicsExtractor g, String s, float cx, float y, int color) {
        text(g, s, cx - width(s) / 2f, y, color);
    }

    public static void textRight(GuiGraphicsExtractor g, String s, float right, float y, int color) {
        text(g, s, right - width(s), y, color);
    }

    public static int width(String s) { return font().width(s); }

    public static String ellipsize(String s, int maxWidth) {
        if (width(s) <= maxWidth) return s;
        return font().plainSubstrByWidth(s, Math.max(0, maxWidth - width("…"))) + "…";
    }

    /** Animated pill switch. t = 0 (off) .. 1 (on). */
    public static void toggle(GuiGraphicsExtractor g, float x, float y, float t, int accent) {
        float w = 20, h = 11;
        int track = Colors.lerp(Colors.TRACK, accent, t);
        roundRect(g, x, y, w, h, h / 2, track);
        float knob = h - 3;
        float kx = x + 1.5f + (w - knob - 3) * t;
        circle(g, kx + knob / 2, y + h / 2, knob / 2, Colors.lerp(0xFFB9BDC9, 0xFFFFFFFF, t));
    }
}
