package net.glowcube.client.core;

/**
 * Die Kategorien der linken Leiste im ClickGUI.
 *
 * Die Einteilung folgt der von BleachHack, damit sich jemand, der von dort
 * kommt, sofort zurechtfindet: Combat, Movement, Render, Player, World,
 * Exploits, Misc.
 */
public enum Category {
    COMBAT("Combat", "\u2694", 0xFFFF5F6D),
    MOVEMENT("Movement", "\u27A4", 0xFF3BF0D4),
    RENDER("Render", "\u25C6", 0xFF9B6BFF),
    PLAYER("Player", "\u2617", 0xFF5FE3A1),
    WORLD("World", "\u26F0", 0xFFFFC53D),
    EXPLOIT("Exploits", "\u26A1", 0xFFFF7AF5),
    MISC("Misc", "\u2699", 0xFF7FC4FF);

    private final String label;
    private final String icon;
    private final int color;

    Category(String label, String icon, int color) {
        this.label = label;
        this.icon = icon;
        this.color = color;
    }

    /** Die Hausfarbe dieser Kategorie. */
    public int color() {
        return color;
    }

    public String label() {
        return label;
    }

    public String icon() {
        return icon;
    }
}
