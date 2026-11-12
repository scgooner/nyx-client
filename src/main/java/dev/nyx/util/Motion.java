package dev.nyx.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;

public final class Motion {
    private Motion() {}

    public static boolean isMoving(LocalPlayer p) {
        Vec2 v = p.input.getMoveVector();
        return v.x != 0 || v.y != 0;
    }

    /**
     * Horizontal velocity in world space for the player's current movement input, scaled to
     * {@code speed} blocks per tick. Returns {x, z}.
     */
    public static double[] direction(LocalPlayer p, double speed) {
        Vec2 v = p.input.getMoveVector();
        double forward = v.y, strafe = v.x;
        double len = Math.sqrt(forward * forward + strafe * strafe);
        if (len < 1e-4) return new double[]{0, 0};
        forward /= len;
        strafe /= len;
        double yaw = Math.toRadians(p.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double x = (strafe * cos - forward * sin) * speed;
        double z = (forward * cos + strafe * sin) * speed;
        return new double[]{x, z};
    }
}
