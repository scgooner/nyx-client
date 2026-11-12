package dev.nyx.modules.player;

import dev.nyx.util.Mc;
import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.BoolSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class AutoTool extends Module {
    private final BoolSetting switchBack = add(new BoolSetting("Switch back", "Go back to your previous slot when you stop mining", true));
    private int previousSlot = -1;

    public AutoTool() {
        super("AutoTool", Category.PLAYER, "Switches to the fastest tool for the block you're mining");
    }

    @Override
    public void onTick() {
        LocalPlayer p = mc().player;
        Inventory inv = p.getInventory();
        boolean mining = Mc.screen() == null && mc().options.keyAttack.isDown()
                && mc().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;

        if (mining) {
            BlockState state = mc().level.getBlockState(((BlockHitResult) mc().hitResult).getBlockPos());
            if (state.isAir()) return;
            int selected = inv.getSelectedSlot();
            int best = selected;
            float bestSpeed = inv.getItem(selected).getDestroySpeed(state);
            for (int i = 0; i < 9; i++) {
                float s = inv.getItem(i).getDestroySpeed(state);
                if (s > bestSpeed + 0.001f) {
                    bestSpeed = s;
                    best = i;
                }
            }
            if (best != selected) {
                if (previousSlot < 0) previousSlot = selected;
                inv.setSelectedSlot(best);
            }
        } else if (previousSlot >= 0) {
            if (switchBack.on()) inv.setSelectedSlot(previousSlot);
            previousSlot = -1;
        }
    }

    @Override
    protected void onDisable() { previousSlot = -1; }
}
