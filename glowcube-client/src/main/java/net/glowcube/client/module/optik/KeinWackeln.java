package net.glowcube.client.module.optik;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;

/**
 * Kein Kamerawackeln bei Treffern - aus AxolotlClient ("No Hurt Cam").
 * Der Mixin {@code KeinWackelnMixin} ueberspringt {@code GameRenderer.bobHurt}.
 */
public final class KeinWackeln extends Module {
    private static KeinWackeln instanz;

    public KeinWackeln() {
        super("Kein Wackeln", "Die Kamera wackelt nicht, wenn du getroffen wirst", Category.OPTIK);
        instanz = this;
    }

    public static boolean aktiv() {
        return instanz != null && instanz.isEnabled();
    }
}
