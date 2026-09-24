package net.glowcube.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Sammelstelle fuer Benachrichtigungen. Jedes Modul darf hier etwas
 * melden; angezeigt wird es vom Modul "Benachrichtigungen" - ist das aus,
 * verfallen die Meldungen still.
 */
public final class Meldungen {
    /** Eine Meldung samt Farbe und Zeitpunkt (Millisekunden). */
    public record Meldung(String text, int farbe, long seit) {
    }

    private static final List<Meldung> AKTIV = new ArrayList<>();
    /** Wird von "Benachrichtigungen" gesetzt: soll bei neuen Meldungen ein Ton kommen? */
    public static Runnable ton;

    private Meldungen() {
    }

    public static synchronized void melden(String text, int farbe) {
        // Dieselbe Meldung nicht doppelt stapeln.
        AKTIV.removeIf(m -> m.text().equals(text));
        AKTIV.add(new Meldung(text, farbe, System.currentTimeMillis()));
        while (AKTIV.size() > 6) {
            AKTIV.remove(0);
        }
        if (ton != null) {
            try {
                ton.run();
            } catch (Throwable ignoriert) {
                // Ohne Ton geht es auch.
            }
        }
    }

    /** Die noch sichtbaren Meldungen; aeltere als {@code dauerMs} fliegen raus. */
    public static synchronized List<Meldung> aktuelle(long dauerMs) {
        long jetzt = System.currentTimeMillis();
        AKTIV.removeIf(m -> jetzt - m.seit() > dauerMs);
        return new ArrayList<>(AKTIV);
    }
}
