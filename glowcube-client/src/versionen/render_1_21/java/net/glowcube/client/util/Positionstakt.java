package net.glowcube.client.util;

import net.minecraft.network.protocol.Packet;

/**
 * 1.21.11 nimmt beliebig viele Positionspakete je Tick an - hier ist nichts
 * zu tun. Das Gegenstueck fuer 26.x liegt in versionen/render_26.
 */
public final class Positionstakt {
    private Positionstakt() {
    }

    public static void vorSenden(Packet<?> packet) {
    }
}
