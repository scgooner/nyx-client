package dev.nyx.modules.render;

import dev.nyx.util.Mc;
import com.mojang.blaze3d.platform.InputConstants;
import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.BoolSetting;
import dev.nyx.setting.KeySetting;
import dev.nyx.setting.NumberSetting;
import dev.nyx.util.Anim;
import dev.nyx.util.Keys;

/** Hold the zoom key to narrow your field of view; the original FOV is restored on release. */
public final class Zoom extends Module {
    private final KeySetting key = add(new KeySetting("Zoom key", "Hold this key to zoom in", InputConstants.KEY_C));
    private final NumberSetting fov = add(new NumberSetting("Zoom FOV", "Field of view while zoomed (lower = closer)", 30, 30, 70, 1));
    private final BoolSetting smooth = add(new BoolSetting("Smooth", "Ease in and out instead of snapping", true));

    private Integer savedFov;
    private float current = -1;
    private long lastFrame = System.nanoTime();

    public Zoom() {
        super("Zoom", Category.RENDER, "Hold a key to zoom in");
    }

    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        update(dt);
    }

    @Override
    public void onTick() {
        // Fallback when the HUD isn't being drawn (F1): frame updates stop, so step at tick rate.
        if (System.nanoTime() - lastFrame > 100_000_000L) update(0.05f);
    }

    private void update(float dt) {
        boolean held = Mc.screen() == null && Keys.down(key.key());
        if (held && savedFov == null) {
            savedFov = mc().options.fov().get();
            current = savedFov;
        }
        if (savedFov == null) return;

        float target = held ? fov.f() : savedFov;
        current = smooth.on() ? Anim.approach(current, target, 14, dt) : target;
        int value = Math.round(current);
        if (!held && Math.abs(current - savedFov) < 0.5f) {
            restore();
            return;
        }
        if (mc().options.fov().get() != value) mc().options.fov().set(value);
    }

    private void restore() {
        if (savedFov != null) {
            mc().options.fov().set(savedFov);
            savedFov = null;
        }
    }

    @Override
    protected void onDisable() { restore(); }

    @Override
    public void onShutdown() { restore(); }

    @Override
    public boolean showInArrayList() { return false; }
}
