package dev.nyx.modules.misc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nyx.gui.ClickGuiScreen;
import dev.nyx.module.Category;
import dev.nyx.module.Module;
import dev.nyx.setting.BoolSetting;
import dev.nyx.setting.ModeSetting;
import dev.nyx.setting.NumberSetting;
import dev.nyx.util.Colors;

/** The menu itself: its keybind opens the GUI, and its settings control the theme. */
public final class ClickGui extends Module {
    private final ModeSetting accent = add(new ModeSetting("Accent", "Accent color used across the client",
            "Violet", "Violet", "Blue", "Cyan", "Mint", "Rose", "Amber", "Custom"));
    private final NumberSetting hue = add(new NumberSetting("Hue", "Custom accent hue", 265, 0, 360, 1))
            .when(() -> accent.is("Custom"));
    private final BoolSetting rainbow = add(new BoolSetting("Rainbow", "Slowly cycle the accent through every hue", false));
    private final BoolSetting blur = add(new BoolSetting("Blur", "Blur the game behind the menu (strength follows Options > Accessibility > Menu Background Blur)", true));
    private final BoolSetting dim = add(new BoolSetting("Dim", "Darken the game behind the menu", true));
    private final NumberSetting animation = add(new NumberSetting("Animation speed", "Speed of the open, close and hover animations", 1.0, 0.5, 2.0, 0.1));

    public ClickGui() {
        super("ClickGUI", Category.MISC, "Opens this menu and controls its look", false, InputConstants.KEY_RSHIFT);
    }

    @Override
    public void toggle() {
        if (mc().screen == null) mc().setScreen(new ClickGuiScreen());
    }

    @Override
    public boolean isAction() { return true; }

    @Override
    public boolean showInArrayList() { return false; }

    public boolean blur() { return blur.on(); }
    public boolean dim() { return dim.on(); }
    public float animationSpeed() { return animation.f(); }

    public int accentColor(int offsetMs) {
        if (rainbow.on()) {
            float h = ((System.currentTimeMillis() + offsetMs) % 6000L) / 6000f;
            return Colors.hsv(h, 0.62f, 0.98f);
        }
        return switch (accent.get()) {
            case "Blue" -> 0xFF3B82F6;
            case "Cyan" -> 0xFF06B6D4;
            case "Mint" -> 0xFF10B981;
            case "Rose" -> 0xFFF43F5E;
            case "Amber" -> 0xFFF59E0B;
            case "Custom" -> Colors.hsv(hue.f() / 360f, 0.62f, 0.98f);
            default -> 0xFF8B5CF6;
        };
    }
}
