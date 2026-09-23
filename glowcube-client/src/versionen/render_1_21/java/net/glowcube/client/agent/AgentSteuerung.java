package net.glowcube.client.agent;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.glowcube.client.render.Netz;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Fassung fuer <b>1.21.x</b>: die Bruecke vom Menue (Client-Thread) zum
 * Server der eigenen Welt. Ein Agent ist ein echtes Wesen in der Welt - das
 * kann nur der Server steuern. In der Einzelspielerwelt (und beim Hosten
 * einer LAN-Welt) laeuft dieser Server im selben Spiel; dorthin wird jeder
 * Befehl per {@code server.execute} uebergeben. Auf fremden Servern geht es
 * nicht - dafuer braeuchte es ein Server-Plugin.
 */
public final class AgentSteuerung {
    private AgentSteuerung() {
    }

    /** Einmal beim Start: Server-Ticks und das Herunterfahren abonnieren. */
    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(AgentWelt::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(AgentWelt::herunterfahren);
    }

    /** @return false, wenn es hier nicht geht - das Modul schaltet sich dann wieder aus */
    public static boolean starten(Auftrag auftrag, String art, AgentWerte werte) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            Netz.nachricht(Component.literal(
                    "§c[Agent] Geht nur in deiner eigenen Welt (Einzelspieler oder LAN-Host)."), false);
            return false;
        }
        UUID spieler = mc.player.getUUID();
        server.execute(() -> AgentWelt.starten(server, spieler, auftrag, art, werte));
        return true;
    }

    public static void zurueck(Auftrag auftrag) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        UUID spieler = mc.player.getUUID();
        server.execute(() -> AgentWelt.zurueck(server, spieler, auftrag));
    }

    public static void einstellen(Auftrag auftrag, AgentWerte werte) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        UUID spieler = mc.player.getUUID();
        server.execute(() -> AgentWelt.einstellen(spieler, auftrag, werte));
    }

    public static void alleZurueck() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        UUID spieler = mc.player.getUUID();
        server.execute(() -> {
            for (Auftrag auftrag : Auftrag.values()) {
                AgentWelt.zurueck(server, spieler, auftrag);
            }
        });
    }
}
