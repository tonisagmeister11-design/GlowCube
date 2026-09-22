package net.glowcube.client.module.optik;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;

/**
 * Kein Regen - aus AxolotlClient ("No Rain"). Die eigene Welt meldet
 * clientseitig Regen- und Gewitterstaerke 0 ({@code WetterMixin}); der
 * Server und das Wetter selbst bleiben unberuehrt.
 */
public final class KeinRegen extends Module {
    private static KeinRegen instanz;

    public KeinRegen() {
        super("Kein Regen", "Kein Regen und kein Gewitter auf deinem Bildschirm", Category.OPTIK);
        instanz = this;
    }

    public static boolean aktiv() {
        return instanz != null && instanz.isEnabled();
    }
}
