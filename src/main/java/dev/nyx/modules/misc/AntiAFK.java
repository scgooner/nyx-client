package dev.nyx.modules.misc;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.BoolSetting;
import dev.nyx.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import java.util.concurrent.ThreadLocalRandom;

public final class AntiAFK extends Module {
    private final NumberSetting interval = add(new NumberSetting("Interval", "Seconds between actions", 30, 5, 300, 5));
    private final BoolSetting jump = add(new BoolSetting("Jump", "Jump each interval", true));
    private final BoolSetting rotate = add(new BoolSetting("Rotate", "Turn a little each interval", true));
    private final BoolSetting swing = add(new BoolSetting("Swing", "Swing your hand each interval", false));
    private int ticks;

    public AntiAFK() {
        super("AntiAFK", Category.MISC, "Moves a little now and then so you aren't marked as away");
    }

    @Override
    protected void onEnable() { ticks = 0; }

    @Override
    public void onTick() {
        if (++ticks < interval.i() * 20) return;
        ticks = 0;
        LocalPlayer p = mc().player;
        if (jump.on() && p.onGround()) p.jumpFromGround();
        if (rotate.on()) p.setYRot(p.getYRot() + ThreadLocalRandom.current().nextFloat(-45f, 45f));
        if (swing.on()) p.swing(InteractionHand.MAIN_HAND);
    }
}
