package dev.nyx.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;

/** Single place for game calls whose names moved in 26.3, so API differences are fixed in one spot. */
public final class Mc {
    private Mc() {}

    public static Screen screen() { return Minecraft.getInstance().gui.screen(); }

    public static void setScreen(Screen screen) { Minecraft.getInstance().gui.setScreen(screen); }

    public static boolean hudHidden() { return Minecraft.getInstance().gui.hud.isHidden(); }

    /** Plays the hand swing animation and tells the server, like a normal attack swing. */
    public static void swing(LivingEntity entity, InteractionHand hand) {
        entity.swing(hand, SwingAnimation.DEFAULT, true);
    }
}
