package net.glowcube.client.core;

/**
 * Die Kategorien der linken Leiste im ClickGUI.
 *
 * Die Einteilung folgt der von BleachHack, damit sich jemand, der von dort
 * kommt, sofort zurechtfindet: Combat, Movement, Render, Player, World,
 * Exploits, Misc.
 */
public enum Category {
    COMBAT("Combat", "⚔"),
    MOVEMENT("Movement", "➤"),
    RENDER("Render", "◆"),
    PLAYER("Player", "☗"),
    WORLD("World", "⛰"),
    EXPLOIT("Exploits", "⚡"),
    MISC("Misc", "⚙");

    private final String label;
    private final String icon;

    Category(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String label() {
        return label;
    }

    public String icon() {
        return icon;
    }
}
