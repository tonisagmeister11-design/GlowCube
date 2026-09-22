package net.glowcube.client.module.optik;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;

/**
 * Keine Vignette - aus AxolotlClient ("Remove Vignette"). Der dunkle
 * Bildrand faellt weg ({@code VignetteMixin}).
 */
public final class KeineVignette extends Module {
    private static KeineVignette instanz;

    public KeineVignette() {
        super("Keine Vignette", "Kein dunkler Rand am Bildschirm", Category.OPTIK);
        instanz = this;
    }

    public static boolean aktiv() {
        return instanz != null && instanz.isEnabled();
    }
}
