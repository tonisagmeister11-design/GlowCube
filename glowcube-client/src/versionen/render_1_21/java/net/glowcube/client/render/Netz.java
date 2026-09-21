package net.glowcube.client.render;

import net.glowcube.client.mixin.ServerboundInteractPacketAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;

/**
 * Fassung fuer <b>1.21.x</b>: gebuendelte versionsabhaengige Kleinigkeiten aus
 * Netzwerk, ChunkPos und Rendern, die sich ab 26.x geaendert haben. So bleiben
 * die Module (BlockUtils, KillAura, Criticals, Search ...) fassungsneutral.
 */
public final class Netz {
    private Netz() {
    }

    /** Schwung nur an den Server melden, ohne Arm-Animation im Bild. */
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

    /** Den Arm schwingen (mit Animation). */
    public static void schwingen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.swing(hand);
        }
    }

    public static int chunkX(ChunkPos pos) {
        return pos.x;
    }

    public static int chunkZ(ChunkPos pos) {
        return pos.z;
    }

    /** Alle Chunks neu zeichnen lassen (fuer X-Ray beim Umschalten). */
    public static void chunksNeuZeichnen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    /** Die Wesen-Nummer aus einem Interaktionspaket. */
    public static int interaktZielId(ServerboundInteractPacket paket) {
        return ((ServerboundInteractPacketAccessor) paket).glowcube$zielNummer();
    }
}
