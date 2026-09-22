package net.glowcube.client.module.optik;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;

/**
 * Das eigene Namensschild - aus AxolotlClient ("Show own nametag").
 * Sichtbar in der dritten Person ({@code EigenerNameMixin}).
 */
public final class EigenerName extends Module {
    private static EigenerName instanz;

    public EigenerName() {
        super("Eigener Name", "Zeigt dein eigenes Namensschild in der dritten Person", Category.OPTIK);
        instanz = this;
    }

    public static boolean aktiv() {
        return instanz != null && instanz.isEnabled();
    }
}
