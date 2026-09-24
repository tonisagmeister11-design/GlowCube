package net.glowcube.client.module.misc;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.render.Netz;
import net.minecraft.network.chat.Component;

/**
 * Panic: schaltet auf einen Schlag alles aus, was im Client an ist - Hacks,
 * HUD-Anzeigen, Karte, Optik, Werkzeuge. Laufende Agenten werden
 * zurueckgerufen. Dasselbe macht der Chatbefehl {@code /panic}.
 *
 * <p>Der Zustand wird gleich gespeichert, damit nach einem Neustart nichts
 * von selbst wieder anspringt.
 */
public final class Panic extends Module {
    public Panic() {
        super("Panic", "Schaltet sofort alle aktivierten Features aus (auch /panic)", Category.MISC);
    }

    @Override
    public void onEnable() {
        setEnabledSilently(false);
        int aus = ausfuehren();
        Netz.nachricht(Component.literal("[GlowCube] Panic: " + aus + " Features ausgeschaltet."), false);
    }

    /** Alles aus. Liefert, wie viele Module an waren. */
    public static int ausfuehren() {
        int aus = 0;
        for (Module modul : GlowCubeClient.modules().all()) {
            if (modul instanceof Panic || !modul.isEnabled()) {
                continue;
            }
            try {
                modul.setEnabled(false);
            } catch (Throwable fehler) {
                // Auch ein Modul, das beim Ausschalten stolpert, gilt als aus.
                modul.setEnabledSilently(false);
            }
            aus++;
        }
        try {
            GlowCubeClient.config().save();
        } catch (Throwable ignoriert) {
            // Spaetestens beim Beenden wird gespeichert.
        }
        return aus;
    }

    @Override
    public boolean bleibtNachWeltwechsel() {
        return false;
    }
}
