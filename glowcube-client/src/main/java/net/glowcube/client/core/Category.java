package net.glowcube.client.core;

/** Die Kategorien der linken Leiste im ClickGUI. */
public enum Category {
    COMBAT("Combat", "⚔"),
    MOVEMENT("Movement", "➤"),
    RENDER("Render", "◆"),
    PLAYER("Player", "☗"),
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
