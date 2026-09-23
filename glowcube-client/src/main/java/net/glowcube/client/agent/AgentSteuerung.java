package net.glowcube.client.agent;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Module;
import net.glowcube.client.module.agent.AgentModul;
import net.glowcube.client.render.Netz;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

/**
 * Die Bruecke vom Menue (Client-Thread) zum Server. Ein Agent ist ein echtes
 * Wesen in der Welt - das kann nur der Server steuern. In der
 * Einzelspielerwelt (und beim Hosten einer LAN-Welt) laeuft dieser Server im
 * selben Spiel; dorthin wird jeder Befehl per {@code server.execute}
 * uebergeben. Auf einem fremden Server geht der Befehl als {@link AgentPaket}
 * an das GlowCube-Agent-Plugin - hat der Server das nicht, sagt GlowCube
 * Bescheid.
 *
 * <p>Protokoll zum Plugin (Text mit Semikolons):
 * {@code start;AUFTRAG;nummer;art;tempo;abbau;xray;chunks;leuchten;zahl},
 * {@code werte;AUFTRAG;nummer;...}, {@code zurueck;AUFTRAG;nummer} (0 = alle),
 * {@code alle}, {@code ort;x;y;z} / {@code ort;weg}, {@code kiste;x;y;z} /
 * {@code kiste;weg}, {@code ziel;x;y;z}. Zurueck kommen {@code aus;AUFTRAG;nummer},
 * {@code status;...} und {@code pvppro;an}.
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
        AgentFassung.kanaeleRegistrieren();
        ClientPlayNetworking.registerGlobalReceiver(AgentPaket.TYP, (paket, kontext) -> vomServer(paket.text()));
    }

    /** Nachrichten vom Plugin. */
    private static void vomServer(String text) {
        if (text.startsWith("status;")) {
            AgentStatus.setzen(AgentStatus.lesen(text.substring("status;".length())));
            return;
        }
        String[] teile = text.split(";");
        if (teile.length == 2 && teile[0].equals("pvppro") && teile[1].equals("an")) {
            PvpFreigabe.vomServerErlaubt();
            return;
        }
        if (teile.length >= 2 && teile[0].equals("aus")) {
            int nummer = teile.length >= 3 ? zahl(teile[2]) : 0;
            Minecraft.getInstance().execute(() -> {
                for (Module modul : GlowCubeClient.modules().all()) {
                    if (modul instanceof AgentModul agent && agent.auftrag().name().equals(teile[1])) {
                        if (nummer == 0) {
                            agent.setEnabledSilently(false);
                        } else {
                            agent.agentBeendet(nummer);
                        }
                    }
                }
            });
        }
    }

    private static int zahl(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException falsch) {
            return 0;
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
                    "§c[Agent] Dieser Server hat das GlowCube-Agent-Plugin nicht."), false);
        }
        return false;
    }

    /** @return false, wenn es hier nicht geht - der Agent gilt dann als nicht losgeschickt */
    public static boolean starten(Auftrag auftrag, int nummer, String art, AgentWerte werte) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.player == null) {
            return false;
        }
        if (server == null) {
            return anPlugin("start;" + auftrag.name() + ";" + nummer + ";" + art + ";" + werte.alsText(), nummer == 1);
        }
        UUID spieler = mc.player.getUUID();
        server.execute(() -> AgentWelt.starten(server, spieler, auftrag, nummer, art, werte));
        return true;
    }

    /** nummer 0: alle dieser Art. */
    public static void zurueck(Auftrag auftrag, int nummer) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.player == null) {
            return;
        }
        if (server == null) {
            anPlugin("zurueck;" + auftrag.name() + ";" + nummer, false);
            return;
        }
        UUID spieler = mc.player.getUUID();
        server.execute(() -> AgentWelt.zurueck(server, spieler, auftrag, nummer));
    }

    public static void einstellen(Auftrag auftrag, int nummer, AgentWerte werte) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.player == null) {
            return;
        }
        if (server == null) {
            anPlugin("werte;" + auftrag.name() + ";" + nummer + ";" + werte.alsText(), false);
            return;
        }
        UUID spieler = mc.player.getUUID();
        server.execute(() -> AgentWelt.einstellen(spieler, auftrag, nummer, werte));
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
                AgentWelt.zurueck(server, spieler, auftrag, 0);
            }
        });
    }

    // ------------------------------------------------------ Einsatzort, Kiste

    /**
     * Einsatzort setzen: neue Abbau-, Farm- und Tunnel-Agenten arbeiten dort,
     * der Builder baut dort. Guardian und Jaeger bleiben bei dir.
     *
     * @return die Meldung fuer den Chat
     */
    public static String ortSetzen(int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return "Nicht in einer Welt.";
        }
        IntegratedServer server = mc.getSingleplayerServer();
        BlockPos pos = new BlockPos(x, y, z);
        if (server != null) {
            UUID spieler = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(spieler);
                if (sp != null) {
                    AgentWelt.ortSetzen(spieler, new AgentWelt.WeltOrt((ServerLevel) sp.level(), pos));
                }
            });
        } else if (!anPlugin("ort;" + x + ";" + y + ";" + z, false)) {
            return "Dieser Server hat das GlowCube-Agent-Plugin nicht.";
        }
        return "Einsatzort: " + x + " " + y + " " + z
                + " - neue Agenten arbeiten jetzt dort (Guardian und Jaeger bleiben bei dir).";
    }

    public static String ortLoeschen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return "Nicht in einer Welt.";
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            UUID spieler = mc.player.getUUID();
            server.execute(() -> AgentWelt.ortSetzen(spieler, null));
        } else {
            anPlugin("ort;weg", false);
        }
        return "Einsatzort geloescht - Agenten starten wieder bei dir.";
    }

    /**
     * Sammelkiste: die Kiste (oder das Fass), auf die man schaut. Volle
     * Agenten bringen ihre Beute dorthin und arbeiten danach weiter.
     */
    public static String kisteMarkieren() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return "Nicht in einer Welt.";
        }
        HitResult treffer = mc.player.pick(6, 1.0f, false);
        if (!(treffer instanceof BlockHitResult block) || treffer.getType() != HitResult.Type.BLOCK) {
            return "Schau auf eine Kiste oder ein Fass (hoechstens 6 Bloecke weit).";
        }
        BlockPos pos = block.getBlockPos();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            UUID spieler = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(spieler);
                if (sp == null) {
                    return;
                }
                ServerLevel welt = (ServerLevel) sp.level();
                if (!AgentKiste.istKiste(welt, pos)) {
                    AgentWelt.melden(sp, net.minecraft.ChatFormatting.RED, "Das ist keine Kiste und kein Fass.");
                    return;
                }
                AgentWelt.kisteSetzen(spieler, new AgentWelt.WeltOrt(welt, pos));
                AgentWelt.melden(sp, net.minecraft.ChatFormatting.GREEN, "Sammelkiste gesetzt: " + pos.getX() + " "
                        + pos.getY() + " " + pos.getZ() + " - volle Agenten bringen ihre Beute hierher.");
            });
        } else if (!anPlugin("kiste;" + pos.getX() + ";" + pos.getY() + ";" + pos.getZ(), false)) {
            return "Dieser Server hat das GlowCube-Agent-Plugin nicht.";
        }
        return "Sammelkiste wird gesetzt ...";
    }

    public static String kisteLoeschen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return "Nicht in einer Welt.";
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            UUID spieler = mc.player.getUUID();
            server.execute(() -> AgentWelt.kisteSetzen(spieler, null));
        } else {
            anPlugin("kiste;weg", false);
        }
        return "Sammelkiste geloescht - volle Agenten kommen wieder zu dir.";
    }

    // ------------------------------------------------------- Orbital Strike

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
        HitResult treffer = mc.player.pick(500, 1.0f, false);
        if (!(treffer instanceof BlockHitResult block) || treffer.getType() != HitResult.Type.BLOCK) {
            return "Kein Block in Sicht - schau auf die Stelle oder gib /strike x y z ein.";
        }
        BlockPos pos = block.getBlockPos();
        return zielSetzen(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Setzt das Ziel fuer den Orbital Strike auf feste Koordinaten - in der
     * eigenen Welt direkt, auf einem Server ueber das GlowCube-Plugin.
     *
     * @return die Meldung fuer den Chat
     */
    public static String zielSetzen(int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return "Nicht in einer Welt.";
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            UUID spieler = mc.player.getUUID();
            BlockPos pos = new BlockPos(x, y, z);
            server.execute(() -> OrbitalStrike.zielSetzen(spieler, pos));
        } else if (!anPlugin("ziel;" + x + ";" + y + ";" + z, false)) {
            return "Dieser Server hat das GlowCube-Plugin nicht - dort gibt es keinen Orbital Strike.";
        }
        return "Orbital-Strike-Ziel: " + x + " " + y + " " + z
                + " - jetzt den Hebel oben auf der Kanone umlegen.";
    }
}
