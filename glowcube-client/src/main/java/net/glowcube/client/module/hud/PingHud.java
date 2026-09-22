package net.glowcube.client.module.hud;

import net.glowcube.client.hud.TextHudModul;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Die Latenz zum Server - nach AxolotlClient ({@code PingHud}). Der Wert
 * kommt aus der Spielerliste, genau wie AxolotlClient ihn dort liest; in der
 * Einzelspielerwelt ist er naturgemaess 0.
 */
public final class PingHud extends TextHudModul {
    public PingHud() {
        super("Ping", "Zeigt die Latenz zum Server");
    }

    @Override
    protected String text() {
        if (mc.player == null || mc.player.connection == null) {
            return "0 ms";
        }
        PlayerInfo info = mc.player.connection.getPlayerInfo(mc.player.getUUID());
        return (info == null ? 0 : info.getLatency()) + " ms";
    }
}
