package dev.nyx.modules.combat;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.BoolSetting;
import dev.nyx.setting.ModeSetting;
import dev.nyx.setting.NumberSetting;
import dev.nyx.util.Targets;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class KillAura extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum attack distance in blocks", 3.5, 1, 6, 0.1));
    private final NumberSetting charge = add(new NumberSetting("Charge", "Attack charge needed before swinging (1 = full hit)", 0.95, 0, 1, 0.05));
    private final ModeSetting priority = add(new ModeSetting("Priority", "Which target is attacked first", "Distance", "Distance", "Health", "Angle"));
    private final BoolSetting players = add(new BoolSetting("Players", "Attack other players", true));
    private final BoolSetting hostiles = add(new BoolSetting("Hostiles", "Attack hostile mobs", true));
    private final BoolSetting passives = add(new BoolSetting("Passives", "Attack animals and other passive mobs", false));
    private final BoolSetting rotate = add(new BoolSetting("Rotate", "Turn your view toward the target before hitting", true));
    private final BoolSetting walls = add(new BoolSetting("Through walls", "Also hit targets you can't see", false));
    private final BoolSetting pauseOnUse = add(new BoolSetting("Pause while using", "Don't attack while eating, blocking or drawing a bow", true));

    public KillAura() {
        super("KillAura", Category.COMBAT, "Automatically attacks nearby targets");
    }

    @Override
    public void onTick() {
        LocalPlayer p = mc().player;
        if (mc().screen != null || p.isSpectator()) return;
        if (pauseOnUse.on() && p.isUsingItem()) return;
        if (p.getAttackStrengthScale(0f) < charge.f()) return;

        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        double maxDist = range.value();
        for (Entity e : mc().level.entitiesForRendering()) {
            if (!Targets.matches(e, players.on(), hostiles.on(), passives.on())) continue;
            if (p.distanceTo(e) > maxDist) continue;
            LivingEntity le = (LivingEntity) e;
            if (!walls.on() && !p.hasLineOfSight(le)) continue;
            double score = switch (priority.get()) {
                case "Health" -> le.getHealth();
                case "Angle" -> Targets.angleTo(p, le);
                default -> p.distanceToSqr(le);
            };
            if (score < best) {
                best = score;
                target = le;
            }
        }
        if (target == null) return;

        if (rotate.on()) Targets.face(p, target);
        mc().gameMode.attack(p, target);
        p.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public String suffix() { return priority.get(); }
}
