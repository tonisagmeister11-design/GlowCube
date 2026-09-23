package net.glowcube.client.agent;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Module;
import net.glowcube.client.module.agent.AgentModul;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Alle laufenden Agenten - lebt ausschliesslich auf dem Server-Thread.
 * Je Spieler und Auftrag hoechstens ein Agent.
 */
final class AgentWelt {
    private static final List<Agent> AGENTEN = new ArrayList<>();

    private AgentWelt() {
    }

    static void starten(MinecraftServer server, UUID besitzer, Auftrag auftrag, String art) {
        ServerPlayer spieler = server.getPlayerList().getPlayer(besitzer);
        if (spieler == null) {
            return;
        }
        // Admin-Rechte: dieselbe Stufe, die /give und /summon brauchen. In der
        // Einzelspielerwelt heisst das: Cheats an.
        if (!spieler.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            melden(spieler, ChatFormatting.RED, "Dafuer brauchst du Admin-Rechte (Cheats an).");
            modulAus(auftrag);
            return;
        }
        for (Agent agent : AGENTEN) {
            if (agent.besitzer().equals(besitzer) && agent.auftrag() == auftrag && !agent.beimZurueckkehren()) {
                melden(spieler, ChatFormatting.YELLOW, auftrag.anzeigename() + " ist schon unterwegs.");
                return;
            }
        }
        Agent agent = Agent.erschaffen(spieler, auftrag, art);
        if (agent == null) {
            melden(spieler, ChatFormatting.RED, "Der Agent konnte nicht erscheinen.");
            modulAus(auftrag);
            return;
        }
        AGENTEN.add(agent);
        melden(spieler, ChatFormatting.AQUA, agent.titel() + " ist losgezogen.");
    }

    static void zurueck(MinecraftServer server, UUID besitzer, Auftrag auftrag) {
        for (Agent agent : AGENTEN) {
            if (agent.besitzer().equals(besitzer) && agent.auftrag() == auftrag) {
                agent.zurueckrufen();
            }
        }
    }

    static void tick(MinecraftServer server) {
        if (AGENTEN.isEmpty()) {
            return;
        }
        for (Iterator<Agent> it = AGENTEN.iterator(); it.hasNext(); ) {
            Agent agent = it.next();
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
        for (Agent agent : AGENTEN) {
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
