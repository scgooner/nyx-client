package dev.nyx.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class Targets {
    private Targets() {}

    public static boolean matches(Entity e, boolean players, boolean hostiles, boolean passives) {
        Minecraft mc = Minecraft.getInstance();
        if (!(e instanceof LivingEntity living) || e == mc.player) return false;
        if (!living.isAlive() || living.isDeadOrDying()) return false;
        if (e instanceof Player p) return players && !p.isSpectator();
        if (e instanceof ArmorStand) return false;
        if (e instanceof Enemy) return hostiles;
        return passives;
    }

    /** Turns the player's view toward the middle of an entity's hitbox. */
    public static void face(LocalPlayer p, Entity target) {
        Vec3 eye = p.getEyePosition();
        Vec3 aim = target.getBoundingBox().getCenter();
        double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        p.setYRot(yaw);
        p.setXRot(Math.max(-90f, Math.min(90f, pitch)));
        p.setYHeadRot(yaw);
    }

    /** Smallest angle in degrees between where the player looks and the target. */
    public static float angleTo(LocalPlayer p, Entity target) {
        Vec3 eye = p.getEyePosition();
        Vec3 aim = target.getBoundingBox().getCenter().subtract(eye).normalize();
        Vec3 look = p.getViewVector(1f);
        double dot = Math.max(-1, Math.min(1, look.dot(aim)));
        return (float) Math.toDegrees(Math.acos(dot));
    }
}
