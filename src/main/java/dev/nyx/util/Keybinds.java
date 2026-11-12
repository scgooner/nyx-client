package dev.nyx.util;

import dev.nyx.module.Module;
import dev.nyx.module.ModuleManager;
import net.minecraft.client.Minecraft;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Edge-triggered module keybinds. Polled every tick and every frame, so short taps are not missed.
 * Key state is always tracked, but a toggle only fires when no screen is open, so a key held while
 * closing a menu does not immediately re-trigger anything.
 */
public final class Keybinds {
    private static final Map<Module, Boolean> WAS_DOWN = new IdentityHashMap<>();

    private Keybinds() {}

    public static void poll() {
        Minecraft mc = Minecraft.getInstance();
        boolean allowed = Mc.screen() == null && mc.player != null;
        for (Module m : ModuleManager.all()) {
            int key = m.getKey();
            boolean down = Keys.down(key);
            boolean before = WAS_DOWN.getOrDefault(m, false);
            WAS_DOWN.put(m, down);
            if (allowed && down && !before) m.toggle();
        }
    }

    /** Marks every currently held key as already handled (used when the GUI closes). */
    public static void swallowHeldKeys() {
        for (Module m : ModuleManager.all()) WAS_DOWN.put(m, Keys.down(m.getKey()));
    }
}
