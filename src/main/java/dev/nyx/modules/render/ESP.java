package dev.nyx.modules.render;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.BoolSetting;
import dev.nyx.setting.NumberSetting;
import dev.nyx.util.Reflect;
import dev.nyx.util.Targets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Outlines entities through walls using the game's own glowing outline. On the client the
 * renderer reads the entity's shared "glowing" flag, so that flag is set directly (and re-applied
 * every tick, because server updates overwrite it).
 */
public final class ESP extends Module {
    private static final Method SET_SHARED_FLAG = Reflect.method(Entity.class, "setSharedFlag", int.class, boolean.class);
    private static final int FLAG_GLOWING = Reflect.getStaticInt(Reflect.field(Entity.class, "FLAG_GLOWING"), 6);

    private final NumberSetting range = add(new NumberSetting("Range", "Only outline entities within this many blocks", 64, 8, 256, 8));
    private final BoolSetting players = add(new BoolSetting("Players", "Outline players", true));
    private final BoolSetting hostiles = add(new BoolSetting("Hostiles", "Outline hostile mobs", true));
    private final BoolSetting passives = add(new BoolSetting("Passives", "Outline animals and passive mobs", false));
    private final BoolSetting items = add(new BoolSetting("Items", "Outline dropped items", false));

    private final Set<Entity> glowing = Collections.newSetFromMap(new IdentityHashMap<>());

    public ESP() {
        super("ESP", Category.RENDER, "Highlights entities through walls");
    }

    @Override
    public void onTick() {
        double maxSq = range.value() * range.value();
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entity e : mc().level.entitiesForRendering()) {
            boolean want = mc().player.distanceToSqr(e) <= maxSq
                    && (Targets.matches(e, players.on(), hostiles.on(), passives.on()) || (items.on() && e instanceof ItemEntity));
            if (want) {
                if (!e.isCurrentlyGlowing()) setGlowing(e, true);
                seen.add(e);
            }
        }
        for (Entity e : glowing) if (!seen.contains(e) && !e.isRemoved()) setGlowing(e, false);
        glowing.clear();
        glowing.addAll(seen);
    }

    @Override
    protected void onDisable() {
        for (Entity e : glowing) if (!e.isRemoved()) setGlowing(e, false);
        glowing.clear();
    }

    private static void setGlowing(Entity e, boolean on) {
        if (SET_SHARED_FLAG == null) return;
        try {
            SET_SHARED_FLAG.invoke(e, FLAG_GLOWING, on);
        } catch (Throwable ignored) {
        }
    }
}
