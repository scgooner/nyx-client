package dev.nyx.modules.world;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.util.Reflect;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;

import java.lang.reflect.Field;

/** Removes the short cooldown the game adds between breaking one block and starting the next. */
public final class FastBreak extends Module {
    private static final Field DESTROY_DELAY = Reflect.field(MultiPlayerGameMode.class, "destroyDelay");

    public FastBreak() {
        super("FastBreak", Category.WORLD, "No delay between breaking blocks");
    }

    @Override
    public void onTick() {
        if (Reflect.getInt(DESTROY_DELAY, mc().gameMode, 0) > 0) Reflect.setInt(DESTROY_DELAY, mc().gameMode, 0);
    }
}
