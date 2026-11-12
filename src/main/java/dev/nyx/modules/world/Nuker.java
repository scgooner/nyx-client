package dev.nyx.modules.world;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.BoolSetting;
import dev.nyx.setting.ModeSetting;
import dev.nyx.setting.NumberSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Nuker extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "Break blocks within this many blocks of your eyes", 4.0, 1, 6, 0.5));
    private final ModeSetting mode = add(new ModeSetting("Mode", "All breaks everything; Same type only breaks the type of block you last hit", "All", "All", "Same type"));
    private final NumberSetting perTick = add(new NumberSetting("Per tick", "Blocks broken per tick in creative mode", 4, 1, 20, 1));
    private final BoolSetting keepFloor = add(new BoolSetting("Keep floor", "Never break blocks below your feet level", true));

    private Block filter;

    public Nuker() {
        super("Nuker", Category.WORLD, "Breaks blocks all around you");
    }

    @Override
    public void onTick() {
        LocalPlayer p = mc().player;
        ClientLevel level = mc().level;
        if (mc().screen != null) return;

        if (mc().options.keyAttack.isDown() && mc().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            filter = level.getBlockState(hit.getBlockPos()).getBlock();
        }
        if (mode.is("Same type") && filter == null) return;

        Vec3 eye = p.getEyePosition();
        double r = range.value(), rSq = r * r;
        int ir = (int) Math.ceil(r);
        BlockPos origin = BlockPos.containing(eye);
        int floorY = p.blockPosition().getY();
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-ir, -ir, -ir), origin.offset(ir, ir, ir))) {
            if (keepFloor.on() && pos.getY() < floorY) continue;
            if (Vec3.atCenterOf(pos).distanceToSqr(eye) > rSq) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (state.getDestroySpeed(level, pos) < 0) continue;
            if (mode.is("Same type") && !state.is(filter)) continue;
            targets.add(pos.immutable());
        }
        if (targets.isEmpty()) return;
        targets.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));

        if (p.isCreative()) {
            for (int i = 0; i < Math.min(perTick.i(), targets.size()); i++) {
                mc().gameMode.startDestroyBlock(targets.get(i), Direction.UP);
            }
        } else {
            mc().gameMode.continueDestroyBlock(targets.get(0), Direction.UP);
        }
        p.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    protected void onDisable() {
        filter = null;
        if (inGame() && mc().gameMode.isDestroying()) mc().gameMode.stopDestroyBlock();
    }

    @Override
    public String suffix() { return mode.get(); }
}
