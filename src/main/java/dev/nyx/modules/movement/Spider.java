package dev.nyx.modules.movement;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.NumberSetting;
import dev.nyx.util.Motion;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class Spider extends Module {
    private final NumberSetting climb = add(new NumberSetting("Speed", "Climbing speed in blocks per tick", 0.2, 0.05, 1.0, 0.05));

    public Spider() {
        super("Spider", Category.MOVEMENT, "Climb up walls like a spider");
    }

    @Override
    public void onTick() {
        LocalPlayer p = mc().player;
        if (!p.horizontalCollision || !Motion.isMoving(p)) return;
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, climb.value(), v.z);
        p.resetFallDistance();
    }
}
