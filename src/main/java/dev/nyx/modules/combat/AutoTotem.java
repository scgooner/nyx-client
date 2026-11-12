package dev.nyx.modules.combat;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.NumberSetting;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

public final class AutoTotem extends Module {
    private static final int OFFHAND_BUTTON = 40;

    private final NumberSetting health = add(new NumberSetting("Health", "Only swap at or below this health (0 = always keep one ready)", 0, 0, 20, 1));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks to wait between swaps", 2, 0, 20, 1));
    private int cooldown;

    public AutoTotem() {
        super("AutoTotem", Category.COMBAT, "Keeps a Totem of Undying in your offhand");
    }

    @Override
    public void onTick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        // Inventory clicks only line up with the player inventory menu, so skip while another container is open.
        if (mc().screen != null && !(mc().screen instanceof InventoryScreen)) return;
        LocalPlayer p = mc().player;
        if (p.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) return;
        if (health.value() > 0 && p.getHealth() > health.value()) return;

        Inventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).getItem() != Items.TOTEM_OF_UNDYING) continue;
            int menuSlot = i < 9 ? i + 36 : i;
            mc().gameMode.handleContainerInput(p.inventoryMenu.containerId, menuSlot, OFFHAND_BUTTON, ContainerInput.SWAP, p);
            cooldown = delay.i();
            return;
        }
    }

    @Override
    public String suffix() {
        if (!inGame()) return null;
        int count = 0;
        Inventory inv = mc().player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() == Items.TOTEM_OF_UNDYING) count += inv.getItem(i).getCount();
        }
        return String.valueOf(count);
    }
}
