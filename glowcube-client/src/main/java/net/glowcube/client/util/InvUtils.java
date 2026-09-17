package net.glowcube.client.util;

import net.glowcube.client.mixin.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), {@code InvUtils}.
 *
 * <p>Alles, was Module mit dem Rucksack anstellen, laeuft hier zusammen:
 * suchen, den Hotbar-Platz wechseln (und wieder zurueck) und Sachen
 * umlegen. Die Grenzfaelle sind die des Originals - besonders der
 * Ruecktausch, der sich einen einzigen vorherigen Platz merkt, und das
 * Aufraeumen am Ende einer Umlege-Aktion, wenn der Mauszeiger danach noch
 * etwas haelt.
 */
public final class InvUtils {
    private static final Aktion AKTION = new Aktion();
    /** -1 heisst: es liegt nichts zum Zuruecktauschen bereit. */
    public static int vorherigerPlatz = -1;

    private InvUtils() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    // ------------------------------------------------------------------ Suchen

    private static Predicate<ItemStack> einesVon(Item... items) {
        return stack -> {
            for (Item item : items) {
                if (stack.is(item)) {
                    return true;
                }
            }
            return false;
        };
    }

    public static boolean inHaupthand(Predicate<ItemStack> pruefung) {
        return mc().player != null && pruefung.test(mc().player.getMainHandItem());
    }

    public static boolean inNebenhand(Predicate<ItemStack> pruefung) {
        return mc().player != null && pruefung.test(mc().player.getOffhandItem());
    }

    public static FindItemResult findeInHotbar(Item... items) {
        return findeInHotbar(einesVon(items));
    }

    /**
     * Zuerst die Haende, dann die Hotbar. Die Reihenfolge ist wichtig: liegt
     * das Gesuchte schon in der Hand, soll kein Tausch stattfinden.
     */
    public static FindItemResult findeInHotbar(Predicate<ItemStack> passt) {
        if (mc().player == null) {
            return new FindItemResult(-1, 0);
        }
        if (inNebenhand(passt)) {
            return new FindItemResult(SlotUtils.OFFHAND, mc().player.getOffhandItem().getCount());
        }
        if (inHaupthand(passt)) {
            return new FindItemResult(mc().player.getInventory().getSelectedSlot(),
                    mc().player.getMainHandItem().getCount());
        }
        return finde(passt, 0, 8);
    }

    public static FindItemResult finde(Item... items) {
        return finde(einesVon(items));
    }

    public static FindItemResult finde(Predicate<ItemStack> passt) {
        if (mc().player == null) {
            return new FindItemResult(-1, 0);
        }
        return finde(passt, 0, mc().player.getInventory().getContainerSize() - 1);
    }

    public static FindItemResult finde(Predicate<ItemStack> passt, int von, int bis) {
        if (mc().player == null) {
            return new FindItemResult(-1, 0);
        }
        int platz = -1;
        int anzahl = 0;
        for (int i = von; i <= bis; i++) {
            ItemStack stack = mc().player.getInventory().getItem(i);
            if (passt.test(stack)) {
                if (platz == -1) {
                    platz = i;
                }
                anzahl += stack.getCount();
            }
        }
        return new FindItemResult(platz, anzahl);
    }

    /** Das schnellste Werkzeug in der Hotbar fuer diesen Block. */
    public static FindItemResult schnellstesWerkzeug(BlockState zustand) {
        if (mc().player == null) {
            return new FindItemResult(-1, 0);
        }
        float bestes = 1.0f;
        int platz = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc().player.getInventory().getItem(i);
            if (!stack.isCorrectToolForDrops(zustand)) {
                continue;
            }
            float wert = stack.getDestroySpeed(zustand);
            if (wert > bestes) {
                bestes = wert;
                platz = i;
            }
        }
        return new FindItemResult(platz, 1);
    }

    // ------------------------------------------------------------- Umschalten

    /**
     * Auf einen Hotbar-Platz wechseln. Mit {@code zurueck} wird der alte
     * Platz gemerkt, damit {@link #tauscheZurueck()} ihn wiederherstellt.
     */
    public static boolean tausche(int platz, boolean zurueck) {
        if (platz == SlotUtils.OFFHAND) {
            return true;
        }
        if (platz < 0 || platz > 8 || mc().player == null) {
            return false;
        }
        if (zurueck && vorherigerPlatz == -1) {
            vorherigerPlatz = mc().player.getInventory().getSelectedSlot();
        } else if (!zurueck) {
            vorherigerPlatz = -1;
        }
        mc().player.getInventory().setSelectedSlot(platz);
        // Sofort melden statt erst im naechsten Tick - sonst baut der Server
        // mit dem alten Gegenstand.
        ((MultiPlayerGameModeAccessor) mc().gameMode).glowcube$auswahlMelden();
        return true;
    }

    public static boolean tauscheZurueck() {
        if (vorherigerPlatz == -1) {
            return false;
        }
        boolean ergebnis = tausche(vorherigerPlatz, false);
        vorherigerPlatz = -1;
        return ergebnis;
    }

    // ------------------------------------------------------------- Verschieben

    public static Aktion verschieben() {
        AKTION.art = SlotKlick.Art.AUFNEHMEN;
        AKTION.zweiKlicks = true;
        return AKTION;
    }

    public static Aktion klicken() {
        AKTION.art = SlotKlick.Art.AUFNEHMEN;
        return AKTION;
    }

    /**
     * Eine angefangene Umlege-Aktion. Bewusst eine einzige, wiederverwendete
     * Instanz wie im Original - so kann nie eine halbfertige Aktion
     * irgendwo liegenbleiben.
     */
    public static final class Aktion {
        private SlotKlick.Art art;
        private boolean zweiKlicks;
        private int von = -1;
        private int nach = -1;
        private int daten;
        private boolean inSichSelbst;

        private Aktion() {
        }

        public Aktion von(int index) {
            von = SlotUtils.indexToId(index);
            return this;
        }

        public void nachNebenhand() {
            nach = SlotUtils.indexToId(SlotUtils.OFFHAND);
            ausfuehren();
        }

        public void nachHotbar(int i) {
            nach = SlotUtils.indexToId(SlotUtils.HOTBAR_START + i);
            ausfuehren();
        }

        private void ausfuehren() {
            if (mc().player == null) {
                zuruecksetzen();
                return;
            }
            boolean zeigerWarLeer = mc().player.containerMenu.getCarried().isEmpty();

            SlotKlick.Art vorherigeArt = art;
            boolean vorherigeZweiKlicks = zweiKlicks;
            int vorherVon = von;
            int vorherNach = nach;

            if (art != null && von != -1 && nach != -1) {
                klick(von);
                if (zweiKlicks) {
                    klick(nach);
                }
            }
            zuruecksetzen();

            // Aufraeumen: war der Zeiger vorher leer und haelt nach dem
            // Tausch etwas, dann lag am Zielplatz schon etwas. Das gehoert
            // zurueck, sonst faellt es beim Schliessen auf den Boden.
            if (!inSichSelbst && zeigerWarLeer && vorherigeArt == SlotKlick.Art.AUFNEHMEN && vorherigeZweiKlicks
                    && vorherVon != -1 && vorherNach != -1
                    && !mc().player.containerMenu.getCarried().isEmpty()) {
                inSichSelbst = true;
                InvUtils.klicken().vonId(vorherVon).nachId(vorherVon);
                inSichSelbst = false;
            }
        }

        private Aktion vonId(int id) {
            von = id;
            return this;
        }

        private void nachId(int id) {
            nach = id;
            ausfuehren();
        }

        private void zuruecksetzen() {
            art = null;
            zweiKlicks = false;
            von = -1;
            nach = -1;
            daten = 0;
        }

        private void klick(int id) {
            SlotKlick.klick(mc().player.containerMenu.containerId, id, daten, art);
        }
    }

    /** Was der Mauszeiger haelt, zurueck ins Fenster werfen. */
    public static void handLeeren() {
        if (mc().player != null && !mc().player.containerMenu.getCarried().isEmpty()) {
            SlotKlick.klick(mc().player.containerMenu.containerId,
                    AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, 0, SlotKlick.Art.AUFNEHMEN);
        }
    }
}
