package dev.nyx.module;

import dev.nyx.NyxClient;
import dev.nyx.config.ConfigManager;
import dev.nyx.hud.Toasts;
import dev.nyx.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final boolean defaultEnabled;
    private final int defaultKey;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private int key;

    protected Module(String name, Category category, String description) {
        this(name, category, description, false, -1);
    }

    protected Module(String name, Category category, String description, boolean defaultEnabled, int defaultKey) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
        this.defaultKey = defaultKey;
        this.enabled = defaultEnabled;
        this.key = defaultKey;
    }

    protected static Minecraft mc() { return Minecraft.getInstance(); }

    /** True while a world is loaded and the local player exists. */
    public static boolean inGame() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && mc.gameMode != null;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<Setting<?>> getSettings() { return Collections.unmodifiableList(settings); }
    public boolean isEnabled() { return enabled; }
    public int getKey() { return key; }
    public boolean isDefaultEnabled() { return defaultEnabled; }
    public int getDefaultKey() { return defaultKey; }

    public void setKey(int key) {
        if (this.key != key) {
            this.key = key;
            ConfigManager.markDirty();
        }
    }

    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean on) {
        if (applyEnabled(on)) {
            ConfigManager.markDirty();
            if (showInArrayList()) Toasts.moduleToggled(this);
        }
    }

    /** Used by config loading: no toast, no dirty flag. */
    public void setEnabledSilently(boolean on) { applyEnabled(on); }

    private boolean applyEnabled(boolean on) {
        if (enabled == on) return false;
        enabled = on;
        try {
            if (on) onEnable(); else onDisable();
        } catch (Throwable t) {
            NyxClient.LOG.error("Module {} failed to {}", name, on ? "enable" : "disable", t);
        }
        return true;
    }

    protected void onEnable() {}
    protected void onDisable() {}

    /** Called every client tick while enabled and in a world. */
    public void onTick() {}

    /** Called every rendered frame while enabled and in a world. */
    public void onFrame() {}

    /** Called once when the game closes, to restore anything the module changed (e.g. options). */
    public void onShutdown() {}

    /** Short extra text shown next to the name in the module list (e.g. the active mode). */
    public String suffix() { return null; }

    /** Whether this module appears in the on-screen module list and gets toggle notifications. */
    public boolean showInArrayList() { return true; }

    /** Action modules (like the Click GUI) do something when triggered instead of staying on. */
    public boolean isAction() { return false; }

    public void resetToDefaults() {
        setEnabledSilently(defaultEnabled);
        key = defaultKey;
        for (Setting<?> s : settings) s.reset();
    }
}
