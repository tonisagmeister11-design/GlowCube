package net.glowcube.client.agent;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Module;
import net.glowcube.client.module.agent.AgentModul;
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
 * Befehl per {@code server.execute} uebergeben. Auf einem fremden Server
 * geht der Befehl als {@link AgentPaket} an das GlowCube-Agent-Plugin - hat
 * der Server das nicht, sagt GlowCube Bescheid.
 */
public final class AgentSteuerung {
    private AgentSteuerung() {
    }

    /** Einmal beim Start: Server-Ticks und das Herunterfahren abonnieren. */
    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(AgentWelt::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(AgentWelt::herunterfahren);
        OrbitalStrike.registrieren();
        // Kanal zum Server-Plugin, in beide Richtungen.
        PayloadTypeRegistry.playC2S().register(AgentPaket.TYP, AgentPaket.CODEC);
        PayloadTypeRegistry.playS2C().register(AgentPaket.TYP, AgentPaket.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(AgentPaket.TYP, (paket, kontext) -> vomServer(paket.text()));
    }

    /** Das Plugin meldet: dieser Agent ist fertig - das Modul im Menue nachziehen. */
    private static void vomServer(String text) {
        String[] teile = text.split(";");
        if (teile.length == 2 && teile[0].equals("pvppro") && teile[1].equals("an")) {
            PvpFreigabe.vomServerErlaubt();
            return;
        }
        if (teile.length == 2 && teile[0].equals("aus")) {
            Minecraft.getInstance().execute(() -> {
                for (Module modul : GlowCubeClient.modules().all()) {
                    if (modul instanceof AgentModul agent && agent.auftrag().name().equals(teile[1])) {
                        agent.setEnabledSilently(false);
                    }
                }
            });
        }
    }

    /** Auf einem fremden Server: ueber das Plugin. false, wenn es keins gibt. */
    private static boolean anPlugin(String text, boolean melden) {
        if (ClientPlayNetworking.canSend(AgentPaket.TYP)) {
            ClientPlayNetworking.send(new AgentPaket(text));
            return true;
        }
        if (melden) {
            Netz.nachricht(Component.literal(
                    "\u00A7c[Agent] Dieser Server hat das GlowCube-Agent-Plugin nicht."), false);
        }
        return false;
    }

    /** @return false, wenn es hier nicht geht - das Modul schaltet sich dann wieder aus */
    public static boolean starten(Auftrag auftrag, String art, AgentWerte werte) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.player == null) {
            return false;
        }
        if (server == null) {
            return anPlugin("start;" + auftrag.name() + ";" + art + ";" + werte.alsText(), true);
        }
        UUID spieler = mc.player.getUUID();
        server.execute(() -> AgentWelt.starten(server, spieler, auftrag, art, werte));
        return true;
    }

    public static void zurueck(Auftrag auftrag) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.player == null) {
            return;
        }
        if (server == null) {
            anPlugin("zurueck;" + auftrag.name(), false);
            return;
        }
        UUID spieler = mc.player.getUUID();
        server.execute(() -> AgentWelt.zurueck(server, spieler, auftrag));
    }

    public static void einstellen(Auftrag auftrag, AgentWerte werte) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.player == null) {
            return;
        }
        if (server == null) {
            anPlugin("werte;" + auftrag.name() + ";" + werte.alsText(), false);
            return;
        }
        UUID spieler = mc.player.getUUID();
        server.execute(() -> AgentWelt.einstellen(spieler, auftrag, werte));
    }

    /**
     * Markiert, worauf der Spieler gerade schaut (bis 500 Bloecke), als Ziel
     * fuer den Orbital Strike. Der Hebel oben auf der Kanone feuert dorthin.
     *
     * @return die Meldung fuer den Chat
     */
    public static String zielMarkieren() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return "Nicht in einer Welt.";
        }
        net.minecraft.world.phys.HitResult treffer = mc.player.pick(500, 1.0f, false);
        if (!(treffer instanceof net.minecraft.world.phys.BlockHitResult block)
                || treffer.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return "Kein Block in Sicht - schau auf die Stelle, die getroffen werden soll.";
        }
        net.minecraft.core.BlockPos pos = block.getBlockPos();
        IntegratedServer server = mc.getSingleplayerServer();
        UUID spieler = mc.player.getUUID();
        if (server != null) {
            server.execute(() -> OrbitalStrike.zielSetzen(spieler, pos));
        } else {
            return "Den Orbital Strike gibt es nur in deiner eigenen Welt.";
        }
        return "Orbital-Strike-Ziel: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " - jetzt den Hebel auf der Kanone umlegen.";
    }

    public static void alleZurueck() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.player == null) {
            return;
        }
        if (server == null) {
            anPlugin("alle", false);
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
