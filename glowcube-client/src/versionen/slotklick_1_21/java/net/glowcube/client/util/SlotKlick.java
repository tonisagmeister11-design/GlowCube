package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;

/**
 * Fassung fuer <b>1.21.x</b> von {@link SlotKlick}.
 *
 * <p>build.gradle schliesst die 26.x-Standardfassung aus src/main aus und
 * setzt diese hier ein, sobald {@code minecraft_version} mit "1." beginnt.
 * Hier heisst der Typ noch {@code ClickType} und die Methode noch
 * {@code handleInventoryMouseClick}. Die {@link Art}-Werte sind Wort fuer
 * Wort dieselben wie in der 26.x-Fassung, damit {@link InvUtils} und
 * {@link Crafting} nichts davon merken.
 */
public final class SlotKlick {
    /** Was der Klick tun soll - unabhaengig vom Namen der Spielfassung. */
    public enum Art {
        AUFNEHMEN,       // ClickType.PICKUP
        SCHNELL_UMLEGEN  // ClickType.QUICK_MOVE
    }

    private SlotKlick() {
    }

    public static void klick(int containerId, int slot, int knopf, Art art) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        ClickType typ = art == Art.SCHNELL_UMLEGEN
                ? ClickType.QUICK_MOVE
                : ClickType.PICKUP;
        mc.gameMode.handleInventoryMouseClick(containerId, slot, knopf, typ, mc.player);
    }
}
