package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * Ab 26.x wirft der Server jeden hinaus, der zwischen zwei "Tick-Ende"-Paketen
 * mehr als eine Position meldet ({@code receivedPositionThisTick}). Criticals,
 * MaceAura, PacketFly und FakeLag melden aber gewollt mehrere Stellen in einem
 * Tick. Damit das weiter geht, schiebt dieser Takt vor jede weitere Position
 * ein eigenes Tick-Ende ein - fuer den Server sind es dann einfach mehrere
 * kurze Ticks, genau wie bei einem Client, der nach einem Ruckler aufholt.
 */
public final class Positionstakt {
    private static boolean schonPosition;
    private static boolean drin;

    private Positionstakt() {
    }

    /** Laeuft unmittelbar bevor ein Paket wirklich hinausgeht. */
    public static void vorSenden(Packet<?> packet) {
        if (packet instanceof ServerboundClientTickEndPacket) {
            schonPosition = false;
            return;
        }
        if (!(packet instanceof ServerboundMovePlayerPacket bewegung) || !bewegung.hasPosition()) {
            return;
        }
        if (schonPosition && !drin) {
            var mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                drin = true;
                try {
                    mc.getConnection().send(ServerboundClientTickEndPacket.INSTANCE);
                } finally {
                    drin = false;
                }
            }
        }
        schonPosition = true;
    }
}
