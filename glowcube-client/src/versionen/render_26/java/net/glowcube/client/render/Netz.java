package net.glowcube.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;

/**
 * Fassung fuer <b>26.3 und neuer</b>: das eigene {@code ServerboundSwingPacket}
 * gibt es unter diesem Namen nicht mehr. Bis der neue Name eingebaut ist, wird
 * der Schwung ueber {@code player.swing} gemeldet (mit Armanimation), und die
 * Paketpruefung laeuft ueber den Klassennamen.
 */
public final class Netz {
    private Netz() {
    }

    public static void schwungSenden(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.swing(hand, false);
        }
    }

    public static boolean istSchwungPaket(Packet<?> paket) {
        return paket.getClass().getSimpleName().contains("Swing");
    }
}
