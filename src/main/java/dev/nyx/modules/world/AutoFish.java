package dev.nyx.modules.world;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.NumberSetting;
import dev.nyx.util.Reflect;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;

public final class AutoFish extends Module {
    private static final Field BITING = Reflect.field(FishingHook.class, "biting");

    private final NumberSetting recast = add(new NumberSetting("Recast delay", "Ticks to wait after reeling in before casting again", 15, 0, 60, 1));
    private int cooldown;
    private int castTicks;
    private int caught;

    public AutoFish() {
        super("AutoFish", Category.WORLD, "Reels in and recasts your rod automatically");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
        castTicks = 0;
    }

    @Override
    public void onTick() {
        LocalPlayer p = mc().player;
        InteractionHand hand = rodHand(p);
        if (hand == null) return;

        FishingHook hook = p.fishing;
        if (hook == null) {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            if (mc().screen != null) return;
            mc().gameMode.useItem(p, hand);
            p.swing(hand);
            castTicks = 0;
            cooldown = 10;
            return;
        }

        castTicks++;
        boolean bite = Reflect.getBool(BITING, hook, false);
        // Fallback if the field isn't readable: the bobber is yanked down when a fish bites.
        if (BITING == null) bite = castTicks > 20 && hook.isInWater() && hook.getDeltaMovement().y < -0.08;
        if (bite) {
            mc().gameMode.useItem(p, hand);
            p.swing(hand);
            cooldown = recast.i();
            caught++;
        }
    }

    private static InteractionHand rodHand(LocalPlayer p) {
        if (p.getMainHandItem().getItem() == Items.FISHING_ROD) return InteractionHand.MAIN_HAND;
        if (p.getOffhandItem().getItem() == Items.FISHING_ROD) return InteractionHand.OFF_HAND;
        return null;
    }

    @Override
    public String suffix() { return String.valueOf(caught); }
}
