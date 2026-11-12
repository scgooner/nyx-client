package dev.nyx.modules.movement;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.ModeSetting;
import dev.nyx.setting.NumberSetting;
import dev.nyx.util.Motion;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;

public final class Flight extends Module {
    private static final float VANILLA_FLY_SPEED = 0.05f;

    private final ModeSetting mode = add(new ModeSetting("Mode", "Creative uses creative-style flying; Velocity moves you directly", "Creative", "Creative", "Velocity"));
    private final NumberSetting speed = add(new NumberSetting("Speed", "Horizontal flight speed multiplier", 1.0, 0.1, 5, 0.1));
    private final NumberSetting vertical = add(new NumberSetting("Vertical", "Up/down speed in blocks per tick (Velocity mode)", 0.5, 0.1, 3, 0.1))
            .when(() -> mode.is("Velocity"));

    public Flight() {
        super("Flight", Category.MOVEMENT, "Lets you fly in survival");
    }

    @Override
    public void onTick() {
        LocalPlayer p = mc().player;
        Abilities a = p.getAbilities();
        if (mode.is("Creative")) {
            a.mayfly = true;
            a.flying = true;
            a.setFlyingSpeed(VANILLA_FLY_SPEED * speed.f());
        } else {
            if (!p.isCreative() && !p.isSpectator()) a.flying = false;
            double[] dir = Motion.direction(p, speed.value() * 0.5);
            double y = 0;
            if (p.input.keyPresses.jump()) y += vertical.value();
            if (p.input.keyPresses.shift()) y -= vertical.value();
            p.setDeltaMovement(dir[0], y, dir[1]);
        }
        p.resetFallDistance();
    }

    @Override
    protected void onDisable() {
        if (!inGame()) return;
        LocalPlayer p = mc().player;
        Abilities a = p.getAbilities();
        if (!p.isCreative() && !p.isSpectator()) {
            a.mayfly = false;
            a.flying = false;
        }
        a.setFlyingSpeed(VANILLA_FLY_SPEED);
    }

    @Override
    public String suffix() { return mode.get(); }
}
