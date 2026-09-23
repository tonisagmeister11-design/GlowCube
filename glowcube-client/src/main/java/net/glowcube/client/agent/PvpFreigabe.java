package net.glowcube.client.agent;

import net.minecraft.client.Minecraft;

/**
 * Wo PvP Pro laufen darf: in der eigenen Welt (Einzelspieler, LAN-Host) und
 * auf Servern, deren Betreiber es ausdruecklich erlaubt - das GlowCube-Plugin
 * meldet dann "pvppro;an" (config.yml: pvp-pro-erlaubt, Berechtigung
 * glowcube.pvppro). Auf jedem anderen Server bleibt es aus.
 */
public final class PvpFreigabe {
    private static volatile boolean vomServer;

    private PvpFreigabe() {
    }

    public static boolean erlaubt() {
        return Minecraft.getInstance().getSingleplayerServer() != null || vomServer;
    }

    /** Das Plugin hat PvP Pro fuer diese Verbindung freigegeben. */
    public static void vomServerErlaubt() {
        vomServer = true;
    }

    /** Beim Trennen: die Freigabe gilt nur fuer den Server, der sie gegeben hat. */
    public static void zuruecksetzen() {
        vomServer = false;
    }
}
