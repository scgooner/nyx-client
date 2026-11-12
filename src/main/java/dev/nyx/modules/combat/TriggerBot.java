package dev.nyx.modules.combat;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.BoolSetting;
import dev.nyx.setting.NumberSetting;
import dev.nyx.util.Targets;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

public final class TriggerBot extends Module {
    private final NumberSetting charge = add(new NumberSetting("Charge", "Attack charge needed before swinging (1 = full hit)", 1.0, 0, 1, 0.05));
    private final BoolSetting players = add(new BoolSetting("Players", "Hit players under your crosshair", true));
    private final BoolSetting hostiles = add(new BoolSetting("Hostiles", "Hit hostile mobs under your crosshair", true));
    private final BoolSetting passives = add(new BoolSetting("Passives", "Hit passive mobs under your crosshair", false));

    public TriggerBot() {
        super("TriggerBot", Category.COMBAT, "Attacks whatever entity your crosshair is on");
    }

    @Override
    public void onTick() {
        LocalPlayer p = mc().player;
        if (mc().screen != null || p.isUsingItem()) return;
        Entity target = mc().crosshairPickEntity;
        if (target == null || !Targets.matches(target, players.on(), hostiles.on(), passives.on())) return;
        if (p.getAttackStrengthScale(0f) < charge.f()) return;
        mc().gameMode.attack(p, target);
        p.swing(InteractionHand.MAIN_HAND);
    }
}
