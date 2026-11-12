package dev.nyx.modules.render;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.BoolSetting;

public final class HudModule extends Module {
    private final BoolSetting watermark = add(new BoolSetting("Watermark", "Show the client name and FPS in the top-left corner", true));
    private final BoolSetting arrayList = add(new BoolSetting("Module list", "List enabled modules in the top-right corner", true));
    private final BoolSetting background = add(new BoolSetting("List background", "Dark background behind the module list", true))
            .when(arrayList::on);
    private final BoolSetting rainbow = add(new BoolSetting("Rainbow", "Shift the accent hue down the module list", false))
            .when(arrayList::on);
    private final BoolSetting coords = add(new BoolSetting("Coordinates", "Show your position under the watermark", true));

    public HudModule() {
        super("HUD", Category.RENDER, "On-screen watermark, module list and coordinates", true, -1);
    }

    public boolean watermark() { return watermark.on(); }
    public boolean arrayList() { return arrayList.on(); }
    public boolean background() { return background.on(); }
    public boolean rainbow() { return rainbow.on(); }
    public boolean coords() { return coords.on(); }

    @Override
    public boolean showInArrayList() { return false; }
}
