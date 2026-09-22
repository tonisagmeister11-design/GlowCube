package net.glowcube.client.module.hud;

import net.glowcube.client.hud.TextHudModul;
import net.minecraft.client.multiplayer.ServerData;

/** Die Adresse des Servers - aus AxolotlClient ({@code IPHud}). */
public final class ServerIpHud extends TextHudModul {
    public ServerIpHud() {
        super("Server-IP", "Zeigt die Adresse des Servers");
    }

    @Override
    protected String text() {
        ServerData server = mc.getCurrentServer();
        return server == null ? "Einzelspieler" : server.ip;
    }
}
