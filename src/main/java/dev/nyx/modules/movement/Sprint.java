package dev.nyx.modules.movement;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.ModeSetting;
import dev.nyx.util.Motion;
import net.minecraft.client.player.LocalPlayer;

public final class Sprint extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Legit only sprints forward like vanilla; Omni sprints in every direction", "Legit", "Legit", "Omni"));

    public Sprint() {
        super("Sprint", Category.MOVEMENT, "Sprints automatically while you move");
    }

    @Override
    public void onTick() {
        LocalPlayer p = mc().player;
        boolean moving = mode.is("Omni") ? Motion.isMoving(p) : p.input.hasForwardImpulse();
        boolean canSprint = p.getFoodData().getFoodLevel() > 6 || p.getAbilities().mayfly;
        if (moving && canSprint && !p.isShiftKeyDown() && !p.isUsingItem()) p.setSprinting(true);
    }

    @Override
    public String suffix() { return mode.get(); }
}
