package dev.nyx.util;

import com.mojang.blaze3d.platform.InputConstants;

public final class Keys {
    public static final int NONE = -1;

    private Keys() {}

    public static boolean down(int key) {
        return key > 0 && InputConstants.isKeyDown(key);
    }

    public static String name(int key) {
        if (key <= 0) return "None";
        try {
            String n = InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
            return n.isEmpty() ? "#" + key : n;
        } catch (Throwable t) {
            return "#" + key;
        }
    }
}
