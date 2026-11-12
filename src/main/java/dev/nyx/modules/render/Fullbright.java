package dev.nyx.modules.render;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.ModeSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class Fullbright extends Module {
    private static final int INFINITE = -1;

    private final ModeSetting mode = add(new ModeSetting("Mode", "Night Vision lights up everything; Gamma raises brightness to the maximum the game allows", "Night Vision", "Night Vision", "Gamma"));
    private boolean addedEffect;
    private Double savedGamma;

    public Fullbright() {
        super("Fullbright", Category.RENDER, "See clearly in the dark");
    }

    @Override
    public void onTick() {
        LocalPlayer p = mc().player;
        if (mode.is("Night Vision")) {
            restoreGamma();
            if (!p.hasEffect(MobEffects.NIGHT_VISION)) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, INFINITE, 0, false, false));
                addedEffect = true;
            }
        } else {
            removeEffect();
            if (savedGamma == null) savedGamma = mc().options.gamma().get();
            if (mc().options.gamma().get() < 1.0) mc().options.gamma().set(1.0);
        }
    }

    @Override
    protected void onDisable() {
        removeEffect();
        restoreGamma();
    }

    @Override
    public void onShutdown() { restoreGamma(); }

    private void removeEffect() {
        if (addedEffect && inGame()) mc().player.removeEffect(MobEffects.NIGHT_VISION);
        addedEffect = false;
    }

    private void restoreGamma() {
        if (savedGamma != null) {
            mc().options.gamma().set(savedGamma);
            savedGamma = null;
        }
    }

    @Override
    public String suffix() { return mode.get(); }
}
