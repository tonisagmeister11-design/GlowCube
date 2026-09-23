package net.glowcube.client.agent;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Module;
import net.glowcube.client.module.agent.AgentModul;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Alle laufenden Agenten - lebt ausschliesslich auf dem Server-Thread.
 * Je Spieler und Auftrag hoechstens ein Agent.
 */
final class AgentWelt {
    private static final List<AgentArbeiter> AGENTEN = new ArrayList<>();

    private AgentWelt() {
    }

    static void starten(MinecraftServer server, UUID besitzer, Auftrag auftrag, String art, AgentWerte werte) {
        ServerPlayer spieler = server.getPlayerList().getPlayer(besitzer);
        if (spieler == null) {
            return;
        }
        // Die eigene Welt: der Befehl kommt immer vom Spieler, dem sie gehoert -
        // darum auch im Survival ohne Cheats erlaubt. (Auf fremden Servern
        // entscheidet das Plugin.)
        for (AgentArbeiter agent : AGENTEN) {
            if (agent.besitzer().equals(besitzer) && agent.auftrag() == auftrag && !agent.beimZurueckkehren()) {
                melden(spieler, ChatFormatting.YELLOW, auftrag.anzeigename() + " ist schon unterwegs.");
                return;
            }
        }
        AgentArbeiter agent = switch (auftrag) {
            case WAECHTER -> AgentWaechter.erschaffen(spieler, art, werte);
            case BAUMEISTER -> AgentBaumeister.erschaffen(spieler, art, werte);
            default -> Agent.erschaffen(spieler, auftrag, art, werte);
        };
        if (agent == null) {
            melden(spieler, ChatFormatting.RED, "Der Agent konnte nicht erscheinen.");
            modulAus(auftrag);
            return;
        }
        AGENTEN.add(agent);
        melden(spieler, ChatFormatting.AQUA, agent.titel() + " ist losgezogen.");
    }

    static void zurueck(MinecraftServer server, UUID besitzer, Auftrag auftrag) {
        for (AgentArbeiter agent : AGENTEN) {
            if (agent.besitzer().equals(besitzer) && agent.auftrag() == auftrag) {
                agent.zurueckrufen();
            }
        }
    }

    static void einstellen(UUID besitzer, Auftrag auftrag, AgentWerte werte) {
        for (AgentArbeiter agent : AGENTEN) {
            if (agent.besitzer().equals(besitzer) && agent.auftrag() == auftrag) {
                agent.einstellen(werte);
            }
        }
    }

    static void tick(MinecraftServer server) {
        if (AGENTEN.isEmpty()) {
            return;
        }
        for (Iterator<AgentArbeiter> it = AGENTEN.iterator(); it.hasNext(); ) {
            AgentArbeiter agent = it.next();
            try {
                agent.tick(server);
            } catch (RuntimeException fehler) {
                GlowCubeClient.LOGGER.error("GlowCube: Agent abgestuerzt - Beute wird uebergeben", fehler);
                agent.notfallUebergabe(server);
            }
            if (agent.fertig()) {
                agent.aufraeumen();
                modulAus(agent.auftrag());
                it.remove();
            }
        }
    }

    /** Welt wird geschlossen: Beute direkt ins Inventar, Agenten weg. */
    static void herunterfahren(MinecraftServer server) {
        for (AgentArbeiter agent : AGENTEN) {
            try {
                agent.notfallUebergabe(server);
                agent.aufraeumen();
            } catch (RuntimeException fehler) {
                GlowCubeClient.LOGGER.warn("GlowCube: Agent beim Beenden nicht sauber entfernt", fehler);
            }
        }
        AGENTEN.clear();
    }

    static void melden(ServerPlayer spieler, ChatFormatting farbe, String text) {
        spieler.sendSystemMessage(Component.literal("[Agent] " + text).withStyle(farbe));
    }

    /** Das Menue nachziehen: der Agent ist weg, also Modul aus (ohne erneut zurueckzurufen). */
    private static void modulAus(Auftrag auftrag) {
        Minecraft.getInstance().execute(() -> {
            for (Module modul : GlowCubeClient.modules().all()) {
                if (modul instanceof AgentModul agent && agent.auftrag() == auftrag) {
                    agent.setEnabledSilently(false);
                }
            }
        });
    }
}
