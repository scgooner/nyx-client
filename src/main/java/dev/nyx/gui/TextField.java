package dev.nyx.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nyx.util.Colors;
import dev.nyx.util.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** Minimal single-line text input drawn with the client's own style. */
final class TextField {
    final String placeholder;
    final int maxLength;
    String text = "";
    boolean focused;
    private long focusedAt;

    TextField(String placeholder, int maxLength) {
        this.placeholder = placeholder;
        this.maxLength = maxLength;
    }

    void setFocused(boolean f) {
        if (f && !focused) focusedAt = System.currentTimeMillis();
        focused = f;
    }

    /** Returns true if the key was used for editing. */
    boolean key(KeyEvent e) {
        if (e.key() == InputConstants.KEY_BACKSPACE) {
            if (text.isEmpty()) return true;
            if ((e.modifiers() & InputConstants.MOD_CONTROL) != 0) {
                String t = text.stripTrailing();
                int cut = t.lastIndexOf(' ');
                text = cut < 0 ? "" : t.substring(0, cut + 1);
            } else {
                text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
            }
            return true;
        }
        return false;
    }

    boolean chr(CharacterEvent e) {
        if (!e.isAllowedChatCharacter()) return false;
        String s = e.codepointAsString();
        if (text.length() + s.length() <= maxLength) text += s;
        return true;
    }

    void clear() { text = ""; }

    void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float hover, float focus, int accent, boolean searchIcon) {
        int bg = Colors.lerp(Colors.FIELD, 0xFF22252E, Math.max(hover * 0.6f, focus));
        Render2D.roundRect(g, x, y, w, h, h / 2.6f, Colors.lerp(Colors.alpha(accent, 0f), Colors.alpha(accent, 0.55f), focus));
        Render2D.roundRect(g, x + 1, y + 1, w - 2, h - 2, h / 2.6f - 1, bg);

        float tx = x + 8;
        if (searchIcon) {
            float cx = x + 10, cy = y + h / 2 - 0.5f;
            int ic = Colors.lerp(Colors.MUTED, Colors.TEXT_2, Math.max(hover, focus));
            Render2D.circle(g, cx, cy, 3.2f, ic);
            Render2D.circle(g, cx, cy, 2.0f, bg);
            Render2D.roundRect(g, cx + 2.0f, cy + 2.0f, 2.4f, 1.1f, 0.5f, ic);
            tx = x + 18;
        }
        float ty = y + (h - 8) / 2f;
        int maxW = (int) (x + w - 6 - tx);
        if (text.isEmpty()) {
            Render2D.text(g, Render2D.ellipsize(placeholder, maxW), tx, ty, Colors.MUTED);
        } else {
            String shown = text;
            while (Render2D.width(shown) > maxW && shown.length() > 1) shown = shown.substring(1);
            Render2D.text(g, shown, tx, ty, Colors.TEXT);
            tx += Render2D.width(shown);
        }
        if (focused && ((System.currentTimeMillis() - focusedAt) / 530) % 2 == 0) {
            Render2D.rect(g, tx + (text.isEmpty() ? 0 : 1), ty - 1, 1, 10, accent);
        }
    }
}
