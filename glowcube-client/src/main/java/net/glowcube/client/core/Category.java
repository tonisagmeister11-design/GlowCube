package net.glowcube.client.core;

/**
 * Die Kategorien der linken Leiste im ClickGUI.
 *
 * Die Einteilung folgt der von BleachHack, damit sich jemand, der von dort
 * kommt, sofort zurechtfindet: Combat, Movement, Render, Player, World,
 * Exploits, Misc.
 */
public enum Category {
    COMBAT("Combat", "\u2694", 0xFFFF5F6D, Bereich.HACKS),
    MOVEMENT("Movement", "\u27A4", 0xFF3BF0D4, Bereich.HACKS),
    RENDER("Render", "\u25C6", 0xFF9B6BFF, Bereich.HACKS),
    PLAYER("Player", "\u2617", 0xFF5FE3A1, Bereich.HACKS),
    WORLD("World", "\u26F0", 0xFFFFC53D, Bereich.HACKS),
    EXPLOIT("Exploits", "\u26A1", 0xFFFF7AF5, Bereich.HACKS),
    MISC("Misc", "\u2699", 0xFF7FC4FF, Bereich.HACKS),
    // Kein Hack: legitime Werkzeuge - die Leistung und die HUD-Anzeigen
    // (CPS, Keystrokes, FPS ...), wie sie PvP-Clients mitbringen.
    PERFORMANCE("Performance", "\u2726", 0xFFB6FF3B, Bereich.KEIN_HACK),
    PVP_HUD("PvP-HUD", "\u2694", 0xFFFF7A7A, Bereich.KEIN_HACK),
    HUD("Info-HUD", "\u25A3", 0xFF7FC4FF, Bereich.KEIN_HACK),
    OPTIK("Optik", "\u25C9", 0xFFFF9F5F, Bereich.KEIN_HACK);

    /**
     * Die zwei Bereiche, in die das Menue oben aufteilt: was ein Hack ist und
     * was keiner ist. Jede Kategorie gehoert genau zu einem davon.
     */
    public enum Bereich {
        HACKS("Hacks", 0xFFFF5F6D),
        KEIN_HACK("Kein Hack", 0xFF5FE3A1);

        private final String label;
        private final int color;

        Bereich(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public int color() {
            return color;
        }
    }

    private final String label;
    private final String icon;
    private final int color;
    private final Bereich bereich;

    Category(String label, String icon, int color, Bereich bereich) {
        this.label = label;
        this.icon = icon;
        this.color = color;
        this.bereich = bereich;
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

    /** In welchen der zwei Menuebereiche diese Kategorie faellt. */
    public Bereich bereich() {
        return bereich;
    }
}
