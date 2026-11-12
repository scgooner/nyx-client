package dev.nyx.modules.misc;

import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.NumberSetting;

public final class Notifications extends Module {
    private final NumberSetting duration = add(new NumberSetting("Duration", "Seconds each notification stays on screen", 2.0, 0.5, 8, 0.5));

    public Notifications() {
        super("Notifications", Category.MISC, "Pop-ups when a module is switched on or off", true, -1);
    }

    public double duration() { return duration.value(); }

    @Override
    public boolean showInArrayList() { return false; }
}
