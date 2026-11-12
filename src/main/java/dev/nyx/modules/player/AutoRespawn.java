package dev.nyx.modules.player;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.NumberSetting;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;

public final class AutoRespawn extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks to wait on the death screen before respawning", 10, 0, 100, 1));
    private int waited;
    private boolean sent;

    public AutoRespawn() {
        super("AutoRespawn", Category.PLAYER, "Respawns you automatically when you die");
    }

    @Override
    public void onTick() {
        LocalPlayer p = mc().player;
        boolean dead = p.isDeadOrDying() || mc().screen instanceof DeathScreen;
        if (!dead) {
            waited = 0;
            sent = false;
            return;
        }
        if (sent) return;
        if (waited++ < delay.i()) return;
        p.respawn();
        sent = true;
    }
}
