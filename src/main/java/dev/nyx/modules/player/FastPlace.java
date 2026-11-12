package dev.nyx.modules.player;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.NumberSetting;
import dev.nyx.util.Reflect;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

public final class FastPlace extends Module {
    private static final Field RIGHT_CLICK_DELAY = Reflect.field(Minecraft.class, "rightClickDelay");

    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between placements (vanilla is 4)", 0, 0, 3, 1));

    public FastPlace() {
        super("FastPlace", Category.PLAYER, "Places blocks and uses items faster");
    }

    @Override
    public void onTick() {
        int current = Reflect.getInt(RIGHT_CLICK_DELAY, mc(), 0);
        if (current > delay.i()) Reflect.setInt(RIGHT_CLICK_DELAY, mc(), delay.i());
    }
}
