package dev.nyx.hud;

import dev.nyx.NyxClient;
import dev.nyx.module.Module;
import dev.nyx.module.ModuleManager;
import dev.nyx.modules.render.HudModule;
import dev.nyx.util.Anim;
import dev.nyx.util.Colors;
import dev.nyx.util.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Hud {
    private static final Map<Module, Float> LIST_ANIM = new LinkedHashMap<>();
    private static final Map<Module, Float> LIST_Y = new LinkedHashMap<>();
    private static long lastFrame = System.nanoTime();

    private Hud() {}

    public static void render(GuiGraphicsExtractor g) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        Minecraft mc = Minecraft.getInstance();
        Render2D.alpha = 1f;
        HudModule hud = ModuleManager.get(HudModule.class);
        if (hud.isEnabled() && !mc.options.hideGui) {
            float y = 5;
            if (hud.watermark()) y = watermark(g, mc) + 4;
            if (hud.coords() && mc.player != null) info(g, mc, y);
            if (hud.arrayList()) arrayList(g, hud, dt);
        }
        Toasts.render(g);
    }

    private static float watermark(GuiGraphicsExtractor g, Minecraft mc) {
        int accent = Colors.accent();
        String brand = "nyx";
        String rest = "  " + mc.getFps() + " fps";
        float w = 18 + Render2D.width(brand) + Render2D.width(rest) + 6;
        float x = 5, y = 5, h = 15;
        Render2D.shadow(g, x, y, w, h, 4, 3, 60);
        Render2D.roundRect(g, x, y, w, h, 4, 0xDD111217);
        Render2D.circle(g, x + 8, y + h / 2, 2.5f, accent);
        Render2D.text(g, brand, x + 14, y + 4, Colors.TEXT);
        Render2D.text(g, rest, x + 14 + Render2D.width(brand), y + 4, Colors.MUTED);
        return y + h;
    }

    private static void info(GuiGraphicsExtractor g, Minecraft mc, float y) {
        String xyz = String.format(Locale.ROOT, "%.1f  %.1f  %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Render2D.textShadow(g, "XYZ", 6, y, Colors.accent());
        Render2D.textShadow(g, xyz, 6 + Render2D.width("XYZ "), y, Colors.TEXT);
    }

    private static void arrayList(GuiGraphicsExtractor g, HudModule hud, float dt) {
        for (Module m : ModuleManager.all()) {
            if (!m.showInArrayList()) continue;
            boolean on = m.isEnabled();
            float cur = LIST_ANIM.getOrDefault(m, 0f);
            if (cur == 0f && !on) continue;
            LIST_ANIM.put(m, Anim.approach(cur, on ? 1f : 0f, 12, dt));
        }
        List<Module> shown = new ArrayList<>();
        for (Map.Entry<Module, Float> e : LIST_ANIM.entrySet()) if (e.getValue() > 0.001f) shown.add(e.getKey());
        shown.sort(Comparator.comparingInt(m -> -Render2D.width(label(m))));

        float right = g.guiWidth() - 4;
        float rowH = 11, y = 4;
        int index = 0;
        for (Module m : shown) {
            float t = Anim.easeOutCubic(LIST_ANIM.get(m));
            String name = m.getName();
            String suffix = m.suffix();
            float w = Render2D.width(label(m)) + 8;
            float targetY = y;
            float cy = LIST_Y.containsKey(m) ? Anim.approach(LIST_Y.get(m), targetY, 16, dt) : targetY;
            LIST_Y.put(m, cy);
            float x = right - w * t;
            int color = hud.rainbow() ? Colors.accent(index * 180) : Colors.accent();

            float prev = Render2D.alpha;
            Render2D.alpha = t;
            if (hud.background()) Render2D.rect(g, x, cy, w, rowH, 0xB0101116);
            Render2D.rect(g, right, cy, 1.5f, rowH, color);
            Render2D.text(g, name, x + 4, cy + 2, color);
            if (suffix != null) Render2D.text(g, " " + suffix, x + 4 + Render2D.width(name), cy + 2, Colors.TEXT_2);
            Render2D.alpha = prev;

            y += rowH * t;
            index++;
        }
    }

    private static String label(Module m) {
        String s = m.suffix();
        return s == null ? m.getName() : m.getName() + " " + s;
    }

    static {
        NyxClient.LOG.debug("HUD ready");
    }
}
