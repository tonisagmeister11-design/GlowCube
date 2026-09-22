package net.glowcube.client.module.optik;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;

/**
 * Beacon-Strahlen aus - nach AxolotlClients {@code BeaconBeam}. Der
 * Strahl wird gar nicht erst gezeichnet ({@code BeaconMixin}).
 */
public final class KeinBeaconStrahl extends Module {
    private static KeinBeaconStrahl instanz;

    public KeinBeaconStrahl() {
        super("Kein Beacon-Strahl", "Blendet die Strahlen von Leuchtfeuern aus", Category.OPTIK);
        instanz = this;
    }

    public static boolean aktiv() {
        return instanz != null && instanz.isEnabled();
    }
}
