package net.glowcube.client.hud;

import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;

/**
 * Zaehlt Klicks der letzten Sekunde - uebertragen aus AxolotlClient
 * ({@code ClickInputTracker}, dort wiederum nach KronHUD): jeder Klick ist ein
 * Zeitstempel, und was aelter als 1000 ms ist, faellt heraus.
 *
 * <p>Gezaehlt wird an den Tasten "Angreifen" und "Benutzen" - wie bei
 * AxolotlClient mit "CPS von Tastenbelegung". Statt eines Eingabe-Mixins
 * (dessen Signatur sich zwischen 1.21.11 und 26.x aendert) wird jeden Frame
 * nachgesehen, ob die Taste neu gedrueckt ist.
 */
public final class KlickZaehler {
    private static final Liste LINKS = new Liste();
    private static final Liste RECHTS = new Liste();

    private static boolean linksVorher;
    private static boolean rechtsVorher;

    private KlickZaehler() {
    }

    /** Jeden Frame aus dem HUD - auch wenn es gerade nichts zeichnet. */
    public static void aktualisieren() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }
        boolean links = mc.options.keyAttack.isDown();
        boolean rechts = mc.options.keyUse.isDown();
        if (links && !linksVorher) {
            LINKS.klick();
        }
        if (rechts && !rechtsVorher) {
            RECHTS.klick();
        }
        linksVorher = links;
        rechtsVorher = rechts;
        LINKS.aufraeumen();
        RECHTS.aufraeumen();
    }

    public static int links() {
        return LINKS.anzahl();
    }

    public static int rechts() {
        return RECHTS.anzahl();
    }

    private static final class Liste {
        private final ArrayDeque<Long> klicks = new ArrayDeque<>();

        void klick() {
            klicks.addLast(System.currentTimeMillis());
        }

        void aufraeumen() {
            long jetzt = System.currentTimeMillis();
            while (!klicks.isEmpty() && jetzt - klicks.peekFirst() > 1000) {
                klicks.removeFirst();
            }
        }

        int anzahl() {
            return klicks.size();
        }
    }
}
