package dev.nyx.hud;

import dev.nyx.module.Module;
import dev.nyx.module.ModuleManager;
import dev.nyx.modules.misc.Notifications;
import dev.nyx.util.Anim;
import dev.nyx.util.Colors;
import dev.nyx.util.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Small slide-in notifications in the bottom-right corner. */
public final class Toasts {
    private static final class Toast {
        final String title, text;
        final boolean positive;
        final long start = System.currentTimeMillis();
        float y = Float.NaN;

        Toast(String title, String text, boolean positive) {
            this.title = title;
            this.text = text;
            this.positive = positive;
        }
    }

    private static final List<Toast> TOASTS = new ArrayList<>();
    private static long lastFrame = System.nanoTime();

    private Toasts() {}

    public static void moduleToggled(Module m) {
        push(m.getName(), m.isEnabled() ? "Enabled" : "Disabled", m.isEnabled());
    }

    public static void push(String title, String text, boolean positive) {
        Notifications n = ModuleManager.get(Notifications.class);
        if (!n.isEnabled()) return;
        TOASTS.removeIf(t -> t.title.equals(title));
        TOASTS.add(new Toast(title, text, positive));
        while (TOASTS.size() > 5) TOASTS.remove(0);
    }

    public static void render(GuiGraphicsExtractor g) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (TOASTS.isEmpty()) return;

        long life = (long) (ModuleManager.get(Notifications.class).duration() * 1000);
        long ms = System.currentTimeMillis();
        TOASTS.removeIf(t -> ms - t.start > life + 300);

        float w = 130, h = 26, gap = 4;
        float right = g.guiWidth() - 6;
        float baseY = g.guiHeight() - 6;
        int accent = Colors.accent();

        for (int i = TOASTS.size() - 1, slot = 0; i >= 0; i--, slot++) {
            Toast t = TOASTS.get(i);
            long age = ms - t.start;
            float in = Anim.easeOutCubic(age / 250f);
            float out = age > life ? Anim.easeInCubic((age - life) / 300f) : 0f;
            float show = in * (1 - out);
            float targetY = baseY - (slot + 1) * (h + gap);
            t.y = Float.isNaN(t.y) ? targetY : Anim.approach(t.y, targetY, 14, dt);
            float x = right - w * show;

            float prev = Render2D.alpha;
            Render2D.alpha = show;
            Render2D.shadow(g, x, t.y, w, h, 5, 4, 70);
            Render2D.roundRect(g, x, t.y, w, h, 5, 0xEE121318);
            Render2D.roundRect(g, x + 4, t.y + 6, 2, h - 12, 1, t.positive ? accent : Colors.MUTED);
            Render2D.text(g, Render2D.ellipsize(t.title, (int) w - 20), x + 11, t.y + 5, Colors.TEXT);
            Render2D.text(g, t.text, x + 11, t.y + 15, t.positive ? Colors.TEXT_2 : Colors.MUTED);
            float progress = Anim.clamp01(1 - age / (float) life);
            Render2D.roundRect(g, x + 11, t.y + h - 3, (w - 17) * progress, 1, 0.5f, Colors.alpha(accent, 0.6f));
            Render2D.alpha = prev;
        }
    }
}
