package net.glowcube.client.module.optik;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;

/**
 * Niedriges Feuer - aus AxolotlClient ("Low Fire"). Das Feuer vor der
 * Kamera wird ein Stueck nach unten geschoben ({@code FeuerMixin}).
 */
public final class NiedrigesFeuer extends Module {
    private static NiedrigesFeuer instanz;

    public NiedrigesFeuer() {
        super("Niedriges Feuer", "Das Feuer am Bildschirmrand verdeckt weniger Sicht", Category.OPTIK);
        instanz = this;
    }

    public static boolean aktiv() {
        return instanz != null && instanz.isEnabled();
    }
}
