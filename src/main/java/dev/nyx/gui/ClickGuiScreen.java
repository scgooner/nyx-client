package dev.nyx.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nyx.NyxClient;
import dev.nyx.config.ConfigManager;
import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.module.ModuleManager;
import dev.nyx.modules.misc.ClickGui;
import dev.nyx.setting.BoolSetting;
import dev.nyx.setting.KeySetting;
import dev.nyx.setting.ModeSetting;
import dev.nyx.setting.NumberSetting;
import dev.nyx.setting.Setting;
import dev.nyx.util.Anim;
import dev.nyx.util.Colors;
import dev.nyx.util.Keybinds;
import dev.nyx.util.Keys;
import dev.nyx.util.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The click GUI. Drawn immediate-mode: every frame lays out and draws all widgets and records
 * clickable regions; input events are matched against the regions from the last frame.
 */
public final class ClickGuiScreen extends Screen {
    private static final float MAX_W = 452, MAX_H = 292, SIDEBAR = 112, RADIUS = 9, HEADER = 36;
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private static final int MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE;

    private static Category lastCategory = Category.COMBAT;
    private static boolean lastConfigs;

    @FunctionalInterface
    private interface Click { void click(int button, double mx, double my); }

    private record Region(float x, float y, float w, float h, float cx1, float cy1, float cx2, float cy2, Click click) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h && mx >= cx1 && mx < cx2 && my >= cy1 && my < cy2;
        }
    }

    private record ScrollArea(float x, float y, float w, float h, boolean settings) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    // Navigation state
    private Category category = lastCategory;
    private boolean configsPage = lastConfigs;
    private Module settingsFor, settingsShown;
    private float settingsT;

    // Widgets
    private final TextField search = new TextField("Search modules", 32);
    private final TextField profileName = new TextField("New profile name", 24);
    private TextField focusedField;
    private boolean focusClaimed;
    private Object listening; // Module (its keybind) or KeySetting
    private ModeSetting dropdown;
    private float dropX, dropY, dropW, dropT;
    private NumberSetting dragging;
    private float dragX, dragW;

    // Scrolling
    private float scroll, scrollTarget, maxScroll;
    private float sScroll, sScrollTarget, sMaxScroll;

    // Animation
    private final Anim.Map01 anim = new Anim.Map01();
    private final Map<Object, Float> sliderShown = new HashMap<>();
    private float pillY = Float.NaN, pillAlpha;
    private float switchT = 1f;
    private final long openedAt = System.nanoTime();
    private long closingAt = -1;
    private boolean finished;
    private long lastFrame;
    private float dt = 0.016f, open;
    private boolean backgroundDone;

    // Hit testing
    private final List<Region> regions = new ArrayList<>();
    private final List<Region> pending = new ArrayList<>();
    private final List<ScrollArea> scrollAreas = new ArrayList<>();
    private final List<ScrollArea> pendingScroll = new ArrayList<>();
    private float clipX1 = -1e6f, clipY1 = -1e6f, clipX2 = 1e6f, clipY2 = 1e6f;
    private int mouseX, mouseY;

    // Config page feedback
    private String status;
    private boolean statusGood;
    private long statusAt;

    public ClickGuiScreen() {
        super(Component.literal("Nyx Client"));
    }

    private static ClickGui gui() { return ModuleManager.get(ClickGui.class); }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ open / close animation

    private float progress() {
        long now = System.nanoTime();
        float speed = gui().animationSpeed();
        if (closingAt < 0) return Anim.clamp01((now - openedAt) / 1e6f / (260f / speed));
        return 1f - Anim.clamp01((now - closingAt) / 1e6f / (180f / speed));
    }

    private boolean interactive() { return closingAt < 0 && open > 0.85f; }

    @Override
    public void onClose() {
        if (closingAt >= 0) return;
        closingAt = System.nanoTime();
        setFocus(null);
        dropdown = null;
        listening = null;
        dragging = null;
    }

    private void finishClose() {
        if (finished) return;
        finished = true;
        lastCategory = category;
        lastConfigs = configsPage;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen == this) {
                mc.setScreen(null);
                Keybinds.swallowHeldKeys();
            }
        });
    }

    @Override
    public void removed() {
        if (focusedField != null) {
            focusedField.setFocused(false);
            focusedField = null;
            Minecraft.getInstance().onTextInputFocusChange(this, false);
        }
        lastCategory = category;
        lastConfigs = configsPage;
    }

    // ------------------------------------------------------------------ background

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        drawBackground(g);
        backgroundDone = true;
    }

    private void drawBackground(GuiGraphicsExtractor g) {
        float a = Anim.easeOutCubic(progress());
        ClickGui gui = gui();
        if (gui.blur()) g.blurBeforeThisStratum();
        if (gui.dim()) {
            g.fillGradient(0, 0, width, height, Colors.withAlpha(0x05060A, (int) (110 * a)), Colors.withAlpha(0x05060A, (int) (165 * a)));
        }
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        long now = System.nanoTime();
        dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        mouseX = mx;
        mouseY = my;

        if (!backgroundDone) drawBackground(g);
        backgroundDone = false;

        float t = progress();
        if (closingAt >= 0 && t <= 0f) {
            finishClose();
            return;
        }
        open = t;
        boolean closing = closingAt >= 0;
        float alpha = closing ? Anim.easeOutCubic(t) : Anim.easeOutCubic(t);
        float scale = closing ? 0.94f + 0.06f * Anim.easeOutCubic(t) : 0.9f + 0.1f * Anim.easeOutBack(t);
        float sp = gui().animationSpeed();

        float W = Math.min(MAX_W, width - 16), H = Math.min(MAX_H, height - 16);
        float x0 = Render2D.snap((width - W) / 2f), y0 = Render2D.snap((height - H) / 2f);
        float cx = x0 + W / 2f, cy = y0 + H / 2f;

        pending.clear();
        pendingScroll.clear();
        resetClip();

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -cy);
        Render2D.alpha = alpha;

        int accent = Colors.accent();
        Render2D.shadow(g, x0, y0, W, H, RADIUS, 16, 170);
        Render2D.roundRect(g, x0, y0, W, H, RADIUS, Colors.WINDOW);
        Render2D.roundRectLeft(g, x0, y0, SIDEBAR, H, RADIUS, Colors.alpha(Colors.SIDEBAR, 0.8f));
        Render2D.rect(g, x0 + SIDEBAR, y0, 1f / Render2D.scale(), H, Colors.DIVIDER);

        drawSidebar(g, x0, y0, H, accent, sp);

        switchT = Anim.approach(switchT, 1f, 9f * sp, dt);
        float contentAlpha = alpha * Anim.easeOutCubic(switchT);
        float slide = (1 - Anim.easeOutCubic(switchT)) * 8;
        Render2D.alpha = contentAlpha;
        float contentX = x0 + SIDEBAR + 14, contentW = W - SIDEBAR - 28;
        drawHeader(g, contentX, y0 + slide, contentW, accent, sp);
        float bodyY = y0 + HEADER + 10, bodyH = H - HEADER - 18;
        if (configsPage && !searching()) drawConfigs(g, contentX, bodyY + slide, contentW, bodyH, accent, sp);
        else drawModules(g, contentX, bodyY + slide, contentW, bodyH, accent, sp);
        Render2D.alpha = alpha;

        settingsT = Anim.approach(settingsT, settingsFor != null ? 1f : 0f, 13f * sp, dt);
        if (settingsT > 0.002f && settingsShown != null) {
            drawSettings(g, x0, y0, W, H, accent, sp, alpha);
        } else if (settingsFor == null) {
            settingsShown = null;
        }

        if (dropdown != null) drawDropdown(g, accent, sp);

        g.pose().popMatrix();
        Render2D.alpha = 1f;

        regions.clear();
        regions.addAll(pending);
        scrollAreas.clear();
        scrollAreas.addAll(pendingScroll);
    }

    private boolean searching() { return !search.text.isBlank(); }

    // ------------------------------------------------------------------ sidebar

    private void drawSidebar(GuiGraphicsExtractor g, float x0, float y0, float H, int accent, float sp) {
        float sx = x0 + 12;
        // Logo
        Render2D.circle(g, sx + 5, y0 + 18, 7.5f, Colors.alpha(accent, 0.16f));
        Render2D.circle(g, sx + 5, y0 + 18, 3.6f, accent);
        Render2D.text(g, "nyx", sx + 16, y0 + 14, Colors.TEXT);
        Render2D.text(g, "client", sx + 18 + Render2D.width("nyx"), y0 + 14, Colors.MUTED);

        float rowsY = y0 + 44;
        Render2D.text(g, "MODULES", sx, rowsY - 1, Colors.alpha(Colors.MUTED, 0.8f));
        rowsY += 12;
        float rowH = 19, rowGap = 2;
        Category[] cats = Category.values();
        float configsY = rowsY + cats.length * (rowH + rowGap) + 18;

        // Selection pill (animated between rows)
        Float target = null;
        if (!searching()) {
            if (configsPage) target = configsY;
            else for (int i = 0; i < cats.length; i++) if (cats[i] == category) target = rowsY + i * (rowH + rowGap);
        }
        pillAlpha = Anim.approach(pillAlpha, target != null ? 1f : 0f, 14f * sp, dt);
        if (target != null) pillY = Float.isNaN(pillY) ? target : Anim.approach(pillY, target, 16f * sp, dt);
        if (!Float.isNaN(pillY) && pillAlpha > 0.01f) {
            Render2D.roundRect(g, x0 + 7, pillY, SIDEBAR - 14, rowH, 5, Colors.alpha(accent, 0.15f * pillAlpha));
            Render2D.roundRect(g, x0 + 7, pillY + 5, 2, rowH - 10, 1, Colors.alpha(accent, pillAlpha));
        }

        for (int i = 0; i < cats.length; i++) {
            final Category c = cats[i];
            float ry = rowsY + i * (rowH + rowGap);
            boolean selected = !configsPage && !searching() && c == category;
            int enabled = 0;
            for (Module m : ModuleManager.of(c)) if (m.isEnabled() && !m.isAction() && m.showInArrayList()) enabled++;
            sidebarRow(g, x0, sx, ry, rowH, c.icon, c.title, enabled, selected, accent, sp, "cat" + c, (b, mx, my) -> {
                if (b != LEFT) return;
                if (configsPage || category != c || searching()) switchT = 0f;
                category = c;
                configsPage = false;
                search.clear();
                setFocus(null);
                closeSettings();
                scroll = scrollTarget = 0;
            });
        }

        Render2D.text(g, "CLIENT", sx, configsY - 13, Colors.alpha(Colors.MUTED, 0.8f));
        sidebarRow(g, x0, sx, configsY, rowH, "☰", "Configs", 0, configsPage && !searching(), accent, sp, "configs", (b, mx, my) -> {
            if (b != LEFT) return;
            if (!configsPage || searching()) switchT = 0f;
            configsPage = true;
            search.clear();
            setFocus(null);
            closeSettings();
            scroll = scrollTarget = 0;
        });

        float hintY = y0 + H - 38;
        Render2D.text(g, "Right-click  settings", sx, hintY, Colors.alpha(Colors.MUTED, 0.75f));
        Render2D.text(g, "Middle-click  keybind", sx, hintY + 10, Colors.alpha(Colors.MUTED, 0.75f));
        Render2D.text(g, "v" + NyxClient.VERSION + "  ·  26.3", sx, y0 + H - 14, Colors.alpha(Colors.MUTED, 0.55f));
    }

    private void sidebarRow(GuiGraphicsExtractor g, float x0, float sx, float y, float h, String icon, String title, int badge,
                            boolean selected, int accent, float sp, Object key, Click click) {
        boolean hovered = hovered(x0 + 7, y, SIDEBAR - 14, h);
        float hv = anim.get(key, hovered, dt, 16f * sp);
        float sel = anim.get(key + "#sel", selected, dt, 14f * sp);
        if (hv > 0.01f) Render2D.roundRect(g, x0 + 7, y, SIDEBAR - 14, h, 5, Colors.alpha(0xFFFFFFFF, 0.035f * hv * (1 - sel)));
        int iconColor = Colors.lerp(Colors.lerp(Colors.MUTED, Colors.TEXT_2, hv), accent, sel);
        Render2D.text(g, icon, sx + 2, y + 5.5f, iconColor);
        Render2D.text(g, title, sx + 16 + hv * 1.5f, y + 5.5f, Colors.lerp(Colors.lerp(Colors.TEXT_2, Colors.TEXT, hv), Colors.TEXT, sel));
        if (badge > 0) {
            String b = String.valueOf(badge);
            float bw = Render2D.width(b) + 7;
            float bx = x0 + SIDEBAR - 12 - bw;
            Render2D.roundRect(g, bx, y + 4, bw, 11, 5.5f, Colors.alpha(accent, 0.22f));
            Render2D.text(g, b, bx + 3.5f, y + 5.5f, Colors.lerp(Colors.TEXT_2, Colors.TEXT, sel));
        }
        hit(x0 + 7, y, SIDEBAR - 14, h, click);
    }

    // ------------------------------------------------------------------ header + search

    private void drawHeader(GuiGraphicsExtractor g, float x, float y0, float w, int accent, float sp) {
        String title;
        String subtitle;
        if (searching()) {
            int n = ModuleManager.search(search.text).size();
            title = "Search";
            subtitle = n + (n == 1 ? " result" : " results");
        } else if (configsPage) {
            title = "Configs";
            subtitle = "Active profile: " + ConfigManager.active();
        } else {
            List<Module> mods = ModuleManager.of(category);
            int on = 0;
            for (Module m : mods) if (m.isEnabled() && !m.isAction()) on++;
            title = category.title;
            subtitle = mods.size() + " modules  ·  " + on + " enabled";
        }
        if (status != null && System.currentTimeMillis() - statusAt < 3500) {
            subtitle = status;
        }
        Render2D.text(g, title, x, y0 + 13, Colors.TEXT);
        boolean showStatus = status != null && System.currentTimeMillis() - statusAt < 3500;
        Render2D.text(g, subtitle, x, y0 + 24, showStatus ? (statusGood ? accent : Colors.DANGER) : Colors.MUTED);

        float fw = 132, fh = 18, fx = x + w - fw, fy = y0 + 12;
        boolean hv = hovered(fx, fy, fw, fh);
        search.draw(g, fx, fy, fw, fh, anim.get("search", hv, dt, 16f * sp), anim.get("search#f", search.focused, dt, 14f * sp), accent, true);
        hit(fx, fy, fw, fh, (b, mx, my) -> {
            if (b == RIGHT) {
                search.clear();
                scroll = scrollTarget = 0;
            }
            setFocus(search);
        });
    }

    // ------------------------------------------------------------------ module grid

    private void drawModules(GuiGraphicsExtractor g, float x, float y, float w, float h, int accent, float sp) {
        List<Module> mods = searching() ? ModuleManager.search(search.text) : ModuleManager.of(category);
        float gap = 7, cardW = (w - gap) / 2f, cardH = 38;
        int rows = (mods.size() + 1) / 2;
        maxScroll = Math.max(0, rows * (cardH + gap) - gap - h + 4);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll = Anim.approach(scroll, scrollTarget, 18f, dt);
        pendingScroll.add(new ScrollArea(x, y, w, h, false));

        setClip(g, x - 3, y, w + 6, h);
        if (mods.isEmpty()) {
            Render2D.textCentered(g, "No modules match “" + search.text.trim() + "”", x + w / 2, y + h / 2 - 10, Colors.MUTED);
            Render2D.textCentered(g, "Try a name like “fly” or a category", x + w / 2, y + h / 2 + 2, Colors.alpha(Colors.MUTED, 0.6f));
        }
        for (int i = 0; i < mods.size(); i++) {
            float cxp = x + (i % 2) * (cardW + gap);
            float cyp = y + 2 + (i / 2) * (cardH + gap) - scroll;
            if (cyp + cardH < y - 2 || cyp > y + h + 2) continue;
            drawCard(g, mods.get(i), cxp, cyp, cardW, cardH, accent, sp);
        }
        clearClip(g);
        drawScrollbar(g, x + w + 4, y, h, scroll, maxScroll, accent);
    }

    private void drawCard(GuiGraphicsExtractor g, Module m, float x, float y, float w, float h, int accent, float sp) {
        boolean hovered = hovered(x, y, w, h);
        float hv = anim.get(m, hovered, dt, 18f * sp);
        float on = anim.get(m.getName() + "#on", m.isEnabled(), dt, 13f * sp);
        boolean binding = listening == m;

        Render2D.roundRect(g, x, y, w, h, 6, Colors.lerp(Colors.CARD, Colors.CARD_HOVER, hv));
        if (on > 0.01f) {
            Render2D.roundRect(g, x, y, w, h, 6, Colors.alpha(accent, 0.075f * on));
            Render2D.roundRect(g, x, y + 10, 2, h - 20, 1, Colors.alpha(accent, on));
        }

        String name = m.getName();
        Render2D.text(g, name, x + 10, y + 9, Colors.lerp(Colors.TEXT_2, Colors.TEXT, Math.max(on, hv)));
        float nameEnd = x + 10 + Render2D.width(name);
        if (m.getKey() > 0 && !binding) {
            String kn = Keys.name(m.getKey());
            float bw = Render2D.width(kn) + 7;
            Render2D.roundRect(g, nameEnd + 5, y + 7.5f, bw, 11, 3, Colors.TRACK);
            Render2D.text(g, kn, nameEnd + 8.5f, y + 9, Colors.TEXT_2);
        }
        if (searching()) {
            Render2D.textRight(g, m.getCategory().title, x + w - 38, y + 9, Colors.alpha(Colors.MUTED, 0.8f));
        }

        String sub = binding ? "Press a key…  (Esc to clear)" : m.getDescription();
        Render2D.text(g, Render2D.ellipsize(sub, (int) w - 50), x + 10, y + 22, binding ? accent : Colors.MUTED);

        if (m.isAction()) {
            Render2D.textRight(g, "›", x + w - 12, y + h / 2 - 4, Colors.lerp(Colors.MUTED, Colors.TEXT, hv));
        } else {
            Render2D.toggle(g, x + w - 31, y + h / 2 - 5.5f, on, accent);
            if (!m.getSettings().isEmpty() && hv > 0.01f) {
                for (int d = 0; d < 3; d++) Render2D.circle(g, x + w - 42 + d * 3.2f - 6, y + h / 2, 0.9f, Colors.alpha(Colors.TEXT_2, hv));
            }
        }

        hit(x, y, w, h, (b, mx, my) -> {
            if (b == LEFT) {
                if (m.isAction()) openSettings(m);
                else m.toggle();
            } else if (b == RIGHT) {
                openSettings(m);
            } else if (b == MIDDLE) {
                listening = m;
            }
        });
    }

    private void drawScrollbar(GuiGraphicsExtractor g, float x, float y, float h, float pos, float max, int accent) {
        if (max <= 0.5f) return;
        float thumbH = Math.max(18, h * h / (h + max));
        float ty = y + (h - thumbH) * (pos / max);
        Render2D.roundRect(g, x, ty, 2, thumbH, 1, Colors.alpha(accent, 0.45f));
    }

    // ------------------------------------------------------------------ settings panel

    private void openSettings(Module m) {
        settingsFor = m;
        settingsShown = m;
        sScroll = sScrollTarget = 0;
        dropdown = null;
        listening = null;
    }

    private void closeSettings() {
        settingsFor = null;
        dropdown = null;
        if (listening instanceof KeySetting || listening == settingsShown) listening = null;
    }

    private void drawSettings(GuiGraphicsExtractor g, float x0, float y0, float W, float H, int accent, float sp, float baseAlpha) {
        Module m = settingsShown;
        float st = Anim.easeOutCubic(settingsT);
        float px = x0 + SIDEBAR + 1 + (1 - st) * 36, pw = W - SIDEBAR - 1;
        Render2D.alpha = baseAlpha * st;

        Render2D.roundRect(g, px, y0, pw, H, RADIUS, Colors.PANEL);
        Render2D.rect(g, px, y0, RADIUS, H, Colors.PANEL);
        Render2D.rect(g, px, y0, 1f / Render2D.scale(), H, Colors.DIVIDER);
        if (settingsFor != null) hit(px, y0, pw, H, (b, mx, my) -> {}); // block clicks to the grid underneath

        // Header
        float bx = px + 12, by = y0 + 11;
        boolean backHv = hovered(bx, by, 18, 18);
        float bh = anim.get("back", backHv, dt, 18f * sp);
        Render2D.roundRect(g, bx, by, 18, 18, 5, Colors.lerp(Colors.FIELD, Colors.CARD_HOVER, bh));
        Render2D.textCentered(g, "‹", bx + 9, by + 5, Colors.lerp(Colors.TEXT_2, Colors.TEXT, bh));
        hit(bx, by, 18, 18, (b, mx, my) -> closeSettings());

        Render2D.text(g, m.getName(), px + 38, y0 + 12, Colors.TEXT);
        Render2D.text(g, m.getCategory().title, px + 38, y0 + 23, Colors.MUTED);
        if (!m.isAction()) {
            float on = anim.get(m.getName() + "#on", m.isEnabled(), dt, 13f * sp);
            float tx = px + pw - 34, ty = y0 + 15;
            Render2D.toggle(g, tx, ty, on, accent);
            hit(tx - 4, ty - 4, 28, 19, (b, mx, my) -> m.toggle());
        }

        // Body
        float x = px + 14, w = pw - 30, top = y0 + HEADER + 8, h = H - HEADER - 16;
        pendingScroll.add(new ScrollArea(px, top, pw, h, true));
        setClip(g, px, top, pw, h);
        float y = top - sScroll;

        // Shows the hovered setting's description (from the previous frame), else the module's.
        String desc = hoverDescription != null ? hoverDescription : m.getDescription();
        hoverDescription = null;
        Render2D.text(g, Render2D.ellipsize(desc, (int) w), x, y + 2, hoverDescription == null && desc.equals(m.getDescription()) ? Colors.TEXT_2 : accent);
        y += 18;

        y = keyRow(g, x, y, w, "Keybind", Keys.name(m.getKey()), listening == m, accent, sp, m, () -> listening = m);
        for (Setting<?> s : m.getSettings()) {
            if (!s.isVisible()) continue;
            Render2D.rect(g, x, y, w, 1f / Render2D.scale(), Colors.DIVIDER);
            if (s instanceof BoolSetting b) y = boolRow(g, x, y, w, b, accent, sp);
            else if (s instanceof NumberSetting n) y = numberRow(g, x, y, w, n, accent, sp);
            else if (s instanceof ModeSetting md) y = modeRow(g, x, y, w, md, accent, sp);
            else if (s instanceof KeySetting k) y = keyRow(g, x, y, w, k.getName(), Keys.name(k.key()), listening == k, accent, sp, k, () -> listening = k);
        }
        y += 6;
        // Reset button
        float rw = Render2D.width("Reset to defaults") + 16;
        button(g, "Reset to defaults", x, y, rw, 16, 2, "reset" + m.getName(), sp, accent, () -> {
            boolean wasOn = m.isEnabled();
            int key = m.getKey();
            for (Setting<?> s : m.getSettings()) s.reset();
            m.setKey(key);
            if (m.isEnabled() != wasOn) m.setEnabledSilently(wasOn);
            ConfigManager.markDirty();
        });
        y += 22;

        float contentH = (y + sScroll) - top;
        sMaxScroll = Math.max(0, contentH - h);
        sScrollTarget = Math.max(0, Math.min(sMaxScroll, sScrollTarget));
        sScroll = Anim.approach(sScroll, sScrollTarget, 18f, dt);
        clearClip(g);
        drawScrollbar(g, px + pw - 7, top, h, sScroll, sMaxScroll, accent);
    }

    private float boolRow(GuiGraphicsExtractor g, float x, float y, float w, BoolSetting s, int accent, float sp) {
        float h = 24;
        boolean hv = hovered(x, y, w, h);
        float hvT = anim.get(s, hv, dt, 18f * sp);
        float on = anim.get(s.getName() + "#" + System.identityHashCode(s), s.on(), dt, 13f * sp);
        Render2D.text(g, s.getName(), x, y + 8, Colors.lerp(Colors.TEXT_2, Colors.TEXT, hvT));
        Render2D.toggle(g, x + w - 20, y + 6.5f, on, accent);
        if (hv) tooltip(s.getDescription());
        hit(x, y, w, h, (b, mx, my) -> { if (b == LEFT) s.toggle(); });
        return y + h;
    }

    private float numberRow(GuiGraphicsExtractor g, float x, float y, float w, NumberSetting s, int accent, float sp) {
        float h = 32;
        boolean hv = hovered(x, y, w, h) || dragging == s;
        float hvT = anim.get(s, hv, dt, 18f * sp);
        Render2D.text(g, s.getName(), x, y + 7, Colors.lerp(Colors.TEXT_2, Colors.TEXT, hvT));
        Render2D.textRight(g, s.format(), x + w, y + 7, dragging == s ? accent : Colors.TEXT);

        float target = (float) s.fraction();
        float shown = sliderShown.getOrDefault(s, target);
        shown = dragging == s ? target : Anim.approach(shown, target, 20f, dt);
        sliderShown.put(s, shown);

        float ty = y + 21, th = 3;
        Render2D.roundRect(g, x, ty, w, th, 1.5f, Colors.TRACK);
        Render2D.roundRect(g, x, ty, Math.max(th, w * shown), th, 1.5f, accent);
        float kx = x + w * shown, ky = ty + th / 2;
        if (hvT > 0.01f) Render2D.circle(g, kx, ky, 4f + 3f * hvT, Colors.alpha(accent, 0.22f * hvT));
        Render2D.circle(g, kx, ky, 3.6f + 0.6f * hvT, 0xFFFFFFFF);
        if (hv) tooltip(s.getDescription());

        final float trackX = x, trackW = w;
        hit(x - 4, y + 14, w + 8, 16, (b, mx, my) -> {
            if (b != LEFT) return;
            dragging = s;
            dragX = trackX;
            dragW = trackW;
            s.setFraction((mx - trackX) / trackW);
        });
        return y + h;
    }

    private float modeRow(GuiGraphicsExtractor g, float x, float y, float w, ModeSetting s, int accent, float sp) {
        float h = 24;
        int widest = 0;
        for (String mode : s.modes()) widest = Math.max(widest, Render2D.width(mode));
        float boxW = Math.max(72, widest + 24), boxX = x + w - boxW, boxY = y + 4;
        boolean rowHv = hovered(x, y, w, h);
        boolean boxHv = hovered(boxX, boxY, boxW, 16);
        float hvT = anim.get(s, rowHv, dt, 18f * sp);
        float bxT = anim.get(s.getName() + "#box" + System.identityHashCode(s), boxHv || dropdown == s, dt, 18f * sp);
        Render2D.text(g, s.getName(), x, y + 8, Colors.lerp(Colors.TEXT_2, Colors.TEXT, hvT));
        Render2D.roundRect(g, boxX, boxY, boxW, 16, 4, Colors.lerp(Colors.FIELD, Colors.CARD_HOVER, bxT));
        Render2D.text(g, s.get(), boxX + 7, boxY + 4, Colors.TEXT);
        Render2D.text(g, dropdown == s ? "▴" : "▾", boxX + boxW - 11, boxY + 4, Colors.lerp(Colors.MUTED, accent, bxT));
        if (rowHv) tooltip(s.getDescription());
        hit(boxX, boxY, boxW, 16, (b, mx, my) -> {
            if (dropdown == s) {
                dropdown = null;
                return;
            }
            dropdown = s;
            dropX = boxX;
            dropY = boxY + 18;
            dropW = boxW;
            dropT = 0;
        });
        return y + h;
    }

    private float keyRow(GuiGraphicsExtractor g, float x, float y, float w, String label, String keyName, boolean waiting,
                         int accent, float sp, Object animKey, Runnable start) {
        float h = 24;
        String shown = waiting ? "Press a key…" : keyName;
        float bw = Math.max(46, Render2D.width(shown) + 14), bx = x + w - bw, by = y + 4;
        boolean hv = hovered(bx, by, bw, 16);
        float hvT = anim.get(animKey + "#key", hv || waiting, dt, 18f * sp);
        Render2D.text(g, label, x, y + 8, Colors.TEXT_2);
        Render2D.roundRect(g, bx, by, bw, 16, 4, Colors.lerp(Colors.FIELD, Colors.alpha(accent, 0.25f), waiting ? 1 : hvT * 0.4f));
        Render2D.textCentered(g, shown, bx + bw / 2, by + 4, waiting ? accent : Colors.TEXT);
        hit(bx, by, bw, 16, (b, mx, my) -> start.run());
        return y + h;
    }

    private void drawDropdown(GuiGraphicsExtractor g, int accent, float sp) {
        ModeSetting s = dropdown;
        dropT = Anim.approach(dropT, 1f, 16f * sp, dt);
        float itemH = 15, full = s.modes().size() * itemH + 4;
        float h = full * Anim.easeOutCubic(dropT);

        // Clicking anywhere outside the list closes it.
        hitScreen((b, mx, my) -> dropdown = null);

        Render2D.shadow(g, dropX, dropY, dropW, h, 5, 6, 110);
        Render2D.roundRect(g, dropX, dropY, dropW, h, 5, 0xFF1D1F27);
        setClip(g, dropX, dropY, dropW, h);
        for (int i = 0; i < s.modes().size(); i++) {
            final String mode = s.modes().get(i);
            float iy = dropY + 2 + i * itemH;
            boolean hv = hovered(dropX + 2, iy, dropW - 4, itemH);
            float hvT = anim.get(s.getName() + "#opt" + mode, hv, dt, 20f * sp);
            if (hvT > 0.01f) Render2D.roundRect(g, dropX + 2, iy, dropW - 4, itemH, 3, Colors.alpha(0xFFFFFFFF, 0.06f * hvT));
            boolean sel = s.is(mode);
            if (sel) Render2D.circle(g, dropX + 8, iy + itemH / 2, 1.6f, accent);
            Render2D.text(g, mode, dropX + 14, iy + 4, sel ? Colors.TEXT : Colors.lerp(Colors.TEXT_2, Colors.TEXT, hvT));
            hit(dropX + 2, iy, dropW - 4, itemH, (b, mx, my) -> {
                s.set(mode);
                dropdown = null;
            });
        }
        clearClip(g);
    }

    // ------------------------------------------------------------------ configs page

    private void drawConfigs(GuiGraphicsExtractor g, float x, float y, float w, float h, int accent, float sp) {
        List<String> profiles = ConfigManager.list();
        pendingScroll.add(new ScrollArea(x, y, w, h, false));
        setClip(g, x - 3, y, w + 6, h);
        float yy = y + 2 - scroll;

        // Active profile card
        Render2D.roundRect(g, x, yy, w, 40, 6, Colors.CARD);
        Render2D.text(g, "Active profile", x + 10, yy + 9, Colors.MUTED);
        Render2D.text(g, ConfigManager.active(), x + 10, yy + 22, Colors.TEXT);
        Render2D.text(g, "Changes save automatically", x + 10 + Render2D.width(ConfigManager.active()) + 8, yy + 22, Colors.alpha(Colors.MUTED, 0.7f));
        button(g, "Save now", x + w - 72, yy + 12, 62, 16, 0, "saveNow", sp, accent, () -> {
            boolean ok = ConfigManager.save(ConfigManager.active());
            setStatus(ok ? "Saved “" + ConfigManager.active() + "”" : "Could not save the profile", ok);
        });
        yy += 48;

        // New profile
        float fw = w - 78;
        boolean fh = hovered(x, yy, fw, 20);
        profileName.draw(g, x, yy, fw, 20, anim.get("pname", fh, dt, 16f * sp), anim.get("pname#f", profileName.focused, dt, 14f * sp), accent, false);
        hit(x, yy, fw, 20, (b, mx, my) -> setFocus(profileName));
        button(g, "Create", x + w - 70, yy + 2, 70, 16, 0, "create", sp, accent, this::createProfile);
        yy += 30;

        Render2D.text(g, "PROFILES", x, yy, Colors.alpha(Colors.MUTED, 0.8f));
        yy += 12;
        for (String name : profiles) {
            final String profile = name;
            boolean active = name.equals(ConfigManager.active());
            boolean hv = hovered(x, yy, w, 26);
            float hvT = anim.get("profile#" + name, hv, dt, 18f * sp);
            Render2D.roundRect(g, x, yy, w, 26, 6, Colors.lerp(Colors.CARD, Colors.CARD_HOVER, hvT));
            if (active) Render2D.circle(g, x + 11, yy + 13, 2.2f, accent);
            Render2D.text(g, name, x + (active ? 19 : 10), yy + 9, active ? Colors.TEXT : Colors.TEXT_2);
            if (active) Render2D.text(g, "active", x + 23 + Render2D.width(name), yy + 9, Colors.alpha(accent, 0.85f));
            float bxp = x + w - 8;
            if (!active) {
                bxp -= 50;
                button(g, "Delete", bxp, yy + 5, 50, 16, 2, "del#" + name, sp, accent, () -> {
                    boolean ok = ConfigManager.delete(profile);
                    setStatus(ok ? "Deleted “" + profile + "”" : "Could not delete the profile", ok);
                });
                bxp -= 54;
                button(g, "Load", bxp, yy + 5, 50, 16, 1, "load#" + name, sp, accent, () -> {
                    ConfigManager.saveNow();
                    boolean ok = ConfigManager.load(profile);
                    setStatus(ok ? "Loaded “" + profile + "”" : "Could not load the profile", ok);
                });
            }
            yy += 30;
        }
        Render2D.text(g, "Profiles are stored in .minecraft/config/nyx/profiles", x, yy + 4, Colors.alpha(Colors.MUTED, 0.6f));
        yy += 18;

        float contentH = yy + scroll - y;
        maxScroll = Math.max(0, contentH - h);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll = Anim.approach(scroll, scrollTarget, 18f, dt);
        clearClip(g);
        drawScrollbar(g, x + w + 4, y, h, scroll, maxScroll, accent);
    }

    private void createProfile() {
        String name = ConfigManager.sanitize(profileName.text);
        if (name.isEmpty()) {
            setStatus("Type a name for the new profile first", false);
            setFocus(profileName);
            return;
        }
        boolean ok = ConfigManager.saveAs(name);
        setStatus(ok ? "Created “" + name + "”" : "Could not create the profile", ok);
        if (ok) {
            profileName.clear();
            setFocus(null);
        }
    }

    private void setStatus(String s, boolean good) {
        status = s;
        statusGood = good;
        statusAt = System.currentTimeMillis();
    }

    /** style: 0 accent, 1 subtle, 2 danger. */
    private void button(GuiGraphicsExtractor g, String label, float x, float y, float w, float h, int style, Object key, float sp, int accent, Runnable action) {
        boolean hv = hovered(x, y, w, h);
        float hvT = anim.get(key, hv, dt, 18f * sp);
        int bg, fg;
        switch (style) {
            case 0 -> {
                bg = Colors.lerp(Colors.alpha(accent, 0.85f), accent, hvT);
                fg = 0xFFFFFFFF;
            }
            case 2 -> {
                bg = Colors.lerp(Colors.FIELD, Colors.alpha(Colors.DANGER, 0.3f), hvT);
                fg = Colors.lerp(Colors.TEXT_2, Colors.DANGER, hvT);
            }
            default -> {
                bg = Colors.lerp(Colors.FIELD, Colors.CARD_HOVER, hvT);
                fg = Colors.lerp(Colors.TEXT_2, Colors.TEXT, hvT);
            }
        }
        Render2D.roundRect(g, x, y, w, h, 4, bg);
        Render2D.textCentered(g, label, x + w / 2, y + (h - 8) / 2f, fg);
        hit(x, y, w, h, (b, mx, my) -> { if (b == LEFT) action.run(); });
    }

    // ------------------------------------------------------------------ hit testing helpers

    private boolean hovered(float x, float y, float w, float h) {
        if (!interactive() && closingAt >= 0) return false;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
                && mouseX >= clipX1 && mouseX < clipX2 && mouseY >= clipY1 && mouseY < clipY2
                && !coveredByDropdown();
    }

    private boolean coveredByDropdown() {
        if (dropdown == null) return false;
        float h = (dropdown.modes().size() * 15 + 4);
        return mouseX >= dropX && mouseX < dropX + dropW && mouseY >= dropY && mouseY < dropY + h
                && !(clipX1 == dropX && clipY1 == dropY);
    }

    private void hit(float x, float y, float w, float h, Click click) {
        pending.add(new Region(x, y, w, h, clipX1, clipY1, clipX2, clipY2, click));
    }

    private void hitScreen(Click click) {
        pending.add(new Region(-1e6f, -1e6f, 2e6f, 2e6f, -1e6f, -1e6f, 1e6f, 1e6f, click));
    }

    private void setClip(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        clipX1 = x;
        clipY1 = y;
        clipX2 = x + w;
        clipY2 = y + h;
        g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
    }

    private void clearClip(GuiGraphicsExtractor g) {
        g.disableScissor();
        resetClip();
    }

    private void resetClip() {
        clipX1 = clipY1 = -1e6f;
        clipX2 = clipY2 = 1e6f;
    }

    private void tooltip(String text) {
        // Descriptions are shown inline for modules; settings show theirs in the status line.
        if (text == null || text.isEmpty() || (status != null && System.currentTimeMillis() - statusAt < 3500)) return;
        hoverDescription = text;
    }

    private String hoverDescription;

    // ------------------------------------------------------------------ input

    private void setFocus(TextField field) {
        focusClaimed = true;
        if (focusedField == field) return;
        if (focusedField != null) focusedField.setFocused(false);
        focusedField = field;
        if (field != null) field.setFocused(true);
        Minecraft.getInstance().onTextInputFocusChange(this, field != null);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!interactive()) return true;
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (listening != null) {
            listening = null;
            return true;
        }
        focusClaimed = false;
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.contains(mx, my)) {
                r.click.click(button, mx, my);
                break;
            }
        }
        if (!focusClaimed) setFocus(null);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null && dragW > 0) {
            dragging.setFraction((event.x() - dragX) / dragW);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!interactive()) return true;
        dropdown = null;
        for (int i = scrollAreas.size() - 1; i >= 0; i--) {
            ScrollArea a = scrollAreas.get(i);
            if (!a.contains(mx, my)) continue;
            if (a.settings()) sScrollTarget = Math.max(0, Math.min(sMaxScroll, sScrollTarget - (float) sy * 26));
            else scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) sy * 26));
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (closingAt >= 0) return true;

        if (listening != null) {
            int value = (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) ? Keys.NONE : key;
            if (listening instanceof Module m) m.setKey(value);
            else if (listening instanceof KeySetting k) k.set(value);
            listening = null;
            return true;
        }
        if (focusedField != null) {
            if (key == InputConstants.KEY_ESCAPE) {
                setFocus(null);
                return true;
            }
            if (key == InputConstants.KEY_RETURN && focusedField == profileName) {
                createProfile();
                return true;
            }
            if (focusedField.key(event) && focusedField == search) scroll = scrollTarget = 0;
            return true;
        }
        if (dropdown != null && key == InputConstants.KEY_ESCAPE) {
            dropdown = null;
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            if (searching()) {
                search.clear();
                return true;
            }
            if (settingsFor != null) {
                closeSettings();
                return true;
            }
            onClose();
            return true;
        }
        if (key == gui().getKey()) {
            onClose();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (closingAt >= 0 || listening != null) return true;
        if (focusedField == null) {
            // Type anywhere to search (except on the configs page, where typing would be ambiguous).
            if (configsPage || !event.isAllowedChatCharacter() || Character.isWhitespace(event.codepoint())) return true;
            setFocus(search);
        }
        focusedField.chr(event);
        if (focusedField == search) {
            closeSettings();
            scroll = scrollTarget = 0;
            switchT = Math.min(switchT, 0.6f);
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
