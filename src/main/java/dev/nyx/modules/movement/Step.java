package dev.nyx.modules.movement;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.NumberSetting;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class Step extends Module {
    private static final double VANILLA_STEP = 0.6;

    private final NumberSetting height = add(new NumberSetting("Height", "How many blocks you can step up", 1.0, 0.6, 3.0, 0.1));

    public Step() {
        super("Step", Category.MOVEMENT, "Walk up full blocks without jumping");
    }

    @Override
    public void onTick() {
        AttributeInstance attr = mc().player.getAttribute(Attributes.STEP_HEIGHT);
        if (attr != null && attr.getBaseValue() != height.value()) attr.setBaseValue(height.value());
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        AttributeInstance attr = mc().player.getAttribute(Attributes.STEP_HEIGHT);
        if (attr != null) attr.setBaseValue(VANILLA_STEP);
    }

    @Override
    public String suffix() { return height.format(); }
}
