package net.glowcube.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;

/**
 * Fassung fuer <b>1.21.x</b>: versionsabhaengige Netz-Kleinigkeiten. Das
 * Schwung-Paket {@code ServerboundSwingPacket} heisst ab 26.x anders - deshalb
 * gebuendelt hier, damit BlockUtils/Nuker/Criticals fassungsneutral bleiben.
 */
public final class Netz {
    private Netz() {
    }

    /** Schwung nur an den Server melden, ohne die Arm-Animation im Bild. */
    public static void schwungSenden(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.send(new ServerboundSwingPacket(hand));
        }
    }

    /** Ist das ein Schwung-Paket auf dem Weg zum Server? */
    public static boolean istSchwungPaket(Packet<?> paket) {
        return paket instanceof ServerboundSwingPacket;
    }
}
