package dev.nyx.module;

import dev.nyx.NyxClient;
import dev.nyx.hud.Toasts;
import dev.nyx.modules.combat.AutoTotem;
import dev.nyx.modules.combat.KillAura;
import dev.nyx.modules.combat.TriggerBot;
import dev.nyx.modules.misc.AntiAFK;
import dev.nyx.modules.misc.ClickGui;
import dev.nyx.modules.misc.Notifications;
import dev.nyx.modules.movement.Flight;
import dev.nyx.modules.movement.Speed;
import dev.nyx.modules.movement.Spider;
import dev.nyx.modules.movement.Sprint;
import dev.nyx.modules.movement.Step;
import dev.nyx.modules.player.AutoRespawn;
import dev.nyx.modules.player.AutoTool;
import dev.nyx.modules.player.FastPlace;
import dev.nyx.modules.render.ESP;
import dev.nyx.modules.render.Fullbright;
import dev.nyx.modules.render.HudModule;
import dev.nyx.modules.render.Zoom;
import dev.nyx.modules.world.AutoFish;
import dev.nyx.modules.world.FastBreak;
import dev.nyx.modules.world.Nuker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();

    private ModuleManager() {}

    public static void init() {
        register(new KillAura(), new TriggerBot(), new AutoTotem(),
                new Sprint(), new Flight(), new Speed(), new Step(), new Spider(),
                new AutoRespawn(), new AutoTool(), new FastPlace(),
                new Fullbright(), new ESP(), new HudModule(), new Zoom(),
                new Nuker(), new AutoFish(), new FastBreak(),
                new ClickGui(), new Notifications(), new AntiAFK());
    }

    private static void register(Module... modules) { Collections.addAll(MODULES, modules); }

    public static List<Module> all() { return Collections.unmodifiableList(MODULES); }

    public static List<Module> of(Category category) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) if (m.getCategory() == category) out.add(m);
        return out;
    }

    public static List<Module> search(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Module> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        for (Module m : MODULES) {
            if (m.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        }
        for (Module m : MODULES) {
            if (!out.contains(m) && (m.getDescription().toLowerCase(Locale.ROOT).contains(q)
                    || m.getCategory().title.toLowerCase(Locale.ROOT).contains(q))) out.add(m);
        }
        return out;
    }

    public static <T extends Module> T get(Class<T> type) {
        for (Module m : MODULES) if (type.isInstance(m)) return type.cast(m);
        throw new IllegalArgumentException("No module " + type.getSimpleName());
    }

    public static Module byName(String name) {
        for (Module m : MODULES) if (m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }

    public static void tick() {
        if (!Module.inGame()) return;
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.onTick();
            } catch (Throwable t) {
                fail(m, t);
            }
        }
    }

    public static void frame() {
        if (!Module.inGame()) return;
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.onFrame();
            } catch (Throwable t) {
                fail(m, t);
            }
        }
    }

    public static void shutdown() {
        for (Module m : MODULES) {
            try {
                m.onShutdown();
            } catch (Throwable t) {
                NyxClient.LOG.error("Module {} failed during shutdown", m.getName(), t);
            }
        }
    }

    /** Number of module errors this session (read by the in-game tests). */
    public static int failures;

    /** A module that throws is switched off instead of crashing the game. */
    private static void fail(Module m, Throwable t) {
        failures++;
        NyxClient.LOG.error("Module {} crashed and was disabled", m.getName(), t);
        m.setEnabledSilently(false);
        Toasts.push(m.getName(), "Disabled after an error", false);
    }
}
