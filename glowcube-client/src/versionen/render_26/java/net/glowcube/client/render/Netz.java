package net.glowcube.client.render;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;

/**
 * Fassung fuer <b>26.3 und neuer</b>: dieselben Kleinigkeiten mit den neuen
 * 26.x-Namen. Die Arm-Animation ({@code swing}) verlangt ab 26.x einen neuen
 * {@code SwingAnimation}-Parameter und bleibt vorerst aus - der Angriff selbst
 * laeuft ohnehin ueber {@code gameMode.attack}. Das Chunk-Neuzeichnen fuer
 * X-Ray folgt mit der uebrigen Render-Anbindung.
 */
public final class Netz {
    private Netz() {
    }

    public static void schwungSenden(InteractionHand hand) {
        // Auf 26.3 vorerst ohne eigenes Schwung-Paket.
    }

    public static boolean istSchwungPaket(Packet<?> paket) {
        return paket.getClass().getSimpleName().contains("Swing");
    }

    public static void schwingen(InteractionHand hand) {
        // Arm-Animation auf 26.3 vorerst aus (neuer SwingAnimation-Parameter).
    }

    public static int chunkX(ChunkPos pos) {
        return pos.x();
    }

    public static int chunkZ(ChunkPos pos) {
        return pos.z();
    }

    public static void chunksNeuZeichnen() {
        // Chunk-Neuzeichnen auf 26.3 noch offen.
    }

    public static int interaktZielId(ServerboundInteractPacket paket) {
        return paket.entityId();
    }
}
