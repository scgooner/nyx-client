package dev.nyx.module;

public enum Category {
    COMBAT("Combat", "⚔"),
    MOVEMENT("Movement", "➤"),
    PLAYER("Player", "☺"),
    RENDER("Render", "◉"),
    WORLD("World", "⛏"),
    MISC("Misc", "⚙");

    public final String title;
    public final String icon;

    Category(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }
}
