package dev.nyx;

import dev.nyx.config.ConfigManager;
import dev.nyx.hud.Hud;
import dev.nyx.module.ModuleManager;
import dev.nyx.util.Keybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NyxClient implements ClientModInitializer {
    public static final String ID = "nyx";
    public static final String VERSION = "1.0.0";
    public static final Logger LOG = LoggerFactory.getLogger("Nyx");

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        ConfigManager.init();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Keybinds.poll();
            ModuleManager.tick();
            ConfigManager.tick();
        });

        // Rendered every frame: HUD overlays, plus frame-rate key polling and per-frame module hooks.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "hud"), (g, delta) -> {
            Keybinds.poll();
            ModuleManager.frame();
            Hud.render(g);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            ModuleManager.shutdown();
            ConfigManager.saveNow();
        });

        LOG.info("Nyx Client {} initialised with {} modules", VERSION, ModuleManager.all().size());
    }
}
