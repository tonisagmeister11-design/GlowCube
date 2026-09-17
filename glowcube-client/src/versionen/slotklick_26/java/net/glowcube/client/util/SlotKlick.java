package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;

/**
 * Ein einzelner Klick in einem Behaeltermenue - die Stelle, an der sich die
 * Fassungen unterscheiden, gebuendelt an einem Ort.
 *
 * <p>Diese Datei ist die Fassung <b>ab 26.x</b>: dort ist der alte
 * {@code ClickType} zu {@link ContainerInput} geworden und
 * {@code handleInventoryMouseClick} heisst jetzt
 * {@code handleContainerInput}. Die Werte sind dieselben geblieben
 * (PICKUP, QUICK_MOVE ...), nur der Name des Typs und der Methode hat sich
 * geaendert.
 *
 * <p>Fuer 1.21.x liegt daneben eine eigene Fassung
 * ({@code src/versionen/slotklick_1_21}), die build.gradle statt dieser
 * einsetzt. {@link InvUtils} und {@link Crafting} kennen nur diese Klasse
 * und deren {@link Art} und bleiben so fassungsneutral.
 */
public final class SlotKlick {
    /** Was der Klick tun soll - unabhaengig vom Namen der Spielfassung. */
    public enum Art {
        AUFNEHMEN,       // frueher ClickType.PICKUP
        SCHNELL_UMLEGEN  // frueher ClickType.QUICK_MOVE
    }

    private SlotKlick() {
    }

    public static void klick(int containerId, int slot, int knopf, Art art) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        ContainerInput eingabe = art == Art.SCHNELL_UMLEGEN
                ? ContainerInput.QUICK_MOVE
                : ContainerInput.PICKUP;
        mc.gameMode.handleContainerInput(containerId, slot, knopf, eingabe, mc.player);
    }
}
