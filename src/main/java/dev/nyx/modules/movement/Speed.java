package dev.nyx.modules.movement;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.ModeSetting;
import dev.nyx.setting.NumberSetting;
import dev.nyx.util.Motion;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class Speed extends Module {
    /** Roughly vanilla sprint speed in blocks per tick. */
    private static final double BASE = 0.2873;

    private final ModeSetting mode = add(new ModeSetting("Mode", "Ground only boosts while walking; Strafe also keeps speed in the air", "Ground", "Ground", "Strafe"));
    private final NumberSetting factor = add(new NumberSetting("Speed", "Movement speed compared to sprinting", 1.4, 1.0, 3.0, 0.05));

    public Speed() {
        super("Speed", Category.MOVEMENT, "Moves you faster than normal");
    }

    @Override
    public void onTick() {
        LocalPlayer p = mc().player;
        if (p.getAbilities().flying || p.isInWater() || p.isPassenger() || p.isShiftKeyDown()) return;
        if (!Motion.isMoving(p)) return;
        if (mode.is("Ground") && !p.onGround()) return;
        double[] dir = Motion.direction(p, BASE * factor.value());
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(dir[0], v.y, dir[1]);
    }

    @Override
    public String suffix() { return mode.get(); }
}
