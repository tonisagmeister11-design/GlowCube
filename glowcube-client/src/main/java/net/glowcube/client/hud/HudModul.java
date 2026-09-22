package net.glowcube.client.hud;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.ModeSetting;

/**
 * Basis einer HUD-Anzeige (CPS, FPS, Keystrokes ...). Die Anzeige kennt ihre
 * Groesse und zeichnet sich an eine vorgegebene Stelle; wohin, legt
 * {@link HudAnzeigen} fest - links unter dem Wasserzeichen oder rechts unter
 * der Modulliste, je nach Einstellung "Seite".
 */
public abstract class HudModul extends Module {
    private final ModeSetting seite;

    protected HudModul(String name, String beschreibung, boolean rechts) {
        this(name, beschreibung, rechts, Category.HUD);
    }

    /** Mit eigener Kategorie - die PvP-Anzeigen stehen im Fenster "PvP-HUD". */
    protected HudModul(String name, String beschreibung, boolean rechts, Category kategorie) {
        super(name, beschreibung, kategorie);
        seite = register(new ModeSetting("Seite", "An welchem Bildschirmrand die Anzeige steht",
                rechts ? "Rechts" : "Links", "Links", "Rechts"));
    }

    public boolean rechts() {
        return seite.is("Rechts");
    }

    /** Einmal je Frame vor dem Messen - hier werden Werte eingesammelt. */
    public void vorbereiten() {
    }

    public abstract float breite(HudZeichner z);

    public abstract float hoehe();

    public abstract void zeichnen(HudZeichner z, float x, float y);
}
