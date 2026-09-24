package net.glowcube.client.agent;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Module;
import net.glowcube.client.module.agent.AgentModul;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Alle laufenden Agenten - lebt ausschliesslich auf dem Server-Thread.
 * Je Spieler, Auftrag und Nummer hoechstens ein Agent; bis zu fuenf je Art.
 *
 * <p>Dazu je Spieler: der <b>Einsatzort</b> ({@code /agentort}) - dort
 * arbeiten neue Abbau-, Farm- und Tunnel-Agenten und baut der Builder - und
 * die <b>Sammelkiste</b> ({@code /agentkiste}), in die volle Agenten ihre
 * Beute bringen, statt zurueckzukommen.
 */
final class AgentWelt {
    /** Ein Ort in einer bestimmten Welt. */
    record WeltOrt(ServerLevel welt, BlockPos pos) {
    }

    private static final List<AgentArbeiter> AGENTEN = new ArrayList<>();
    private static final Map<UUID, WeltOrt> ORTE = new HashMap<>();
    private static final Map<UUID, WeltOrt> KISTEN = new HashMap<>();
    private static int ticks;

    private AgentWelt() {
    }

    static void starten(MinecraftServer server, UUID besitzer, Auftrag auftrag, int nummer, String art,
                        AgentWerte werte) {
        ServerPlayer spieler = server.getPlayerList().getPlayer(besitzer);
        if (spieler == null) {
            return;
        }
        // Die eigene Welt: der Befehl kommt immer vom Spieler, dem sie gehoert -
        // darum auch im Survival ohne Cheats erlaubt. (Auf fremden Servern
        // entscheidet das Plugin.)
        for (AgentArbeiter agent : AGENTEN) {
            if (agent.besitzer().equals(besitzer) && agent.auftrag() == auftrag && agent.nummer() == nummer
                    && !agent.beimZurueckkehren()) {
                melden(spieler, ChatFormatting.YELLOW, agent.titel() + " ist schon unterwegs.");
                return;
            }
        }
        AgentArbeiter agent = switch (auftrag) {
            case WAECHTER, JAEGER -> AgentWaechter.erschaffen(spieler, auftrag, nummer, art, werte);
            case BAUMEISTER -> AgentBaumeister.erschaffen(spieler, nummer, art, werte, baureiheVersatz(besitzer, nummer));
            default -> Agent.erschaffen(spieler, auftrag, nummer, art, werte);
        };
        if (agent == null) {
            melden(spieler, ChatFormatting.RED, auftrag.anzeigename() + " #" + nummer + " konnte nicht erscheinen.");
            modulAus(auftrag, nummer);
            return;
        }
        AGENTEN.add(agent);
        melden(spieler, ChatFormatting.AQUA, agent.titel() + " ist losgezogen.");
    }

    /** Mehrere Builder bauen nebeneinander: Nummer n faengt rechts neben den Baustellen von 1 bis n-1 an. */
    private static int baureiheVersatz(UUID besitzer, int nummer) {
        int versatz = 0;
        for (AgentArbeiter agent : AGENTEN) {
            if (agent instanceof AgentBaumeister b && agent.besitzer().equals(besitzer) && agent.nummer() < nummer
                    && !agent.fertig()) {
                versatz = Math.max(versatz, b.rechterRand() + 4);
            }
        }
        return versatz;
    }

    /** nummer 0: alle dieser Art. */
    static void zurueck(MinecraftServer server, UUID besitzer, Auftrag auftrag, int nummer) {
        for (AgentArbeiter agent : AGENTEN) {
            if (agent.besitzer().equals(besitzer) && agent.auftrag() == auftrag
                    && (nummer == 0 || agent.nummer() == nummer)) {
                agent.zurueckrufen();
            }
        }
    }

    static void einstellen(UUID besitzer, Auftrag auftrag, int nummer, AgentWerte werte) {
        for (AgentArbeiter agent : AGENTEN) {
            if (agent.besitzer().equals(besitzer) && agent.auftrag() == auftrag && agent.nummer() == nummer) {
                agent.einstellen(werte);
            }
        }
    }

    static void ortSetzen(UUID besitzer, WeltOrt ort) {
        if (ort == null) {
            ORTE.remove(besitzer);
        } else {
            ORTE.put(besitzer, ort);
        }
    }

    static void kisteSetzen(UUID besitzer, WeltOrt kiste) {
        if (kiste == null) {
            KISTEN.remove(besitzer);
        } else {
            KISTEN.put(besitzer, kiste);
        }
    }

    /** Der Einsatzort des Spielers oder null. */
    static WeltOrt ort(UUID besitzer) {
        return ORTE.get(besitzer);
    }

    /** Die Sammelkiste des Spielers oder null. */
    static WeltOrt kiste(UUID besitzer) {
        return KISTEN.get(besitzer);
    }

    /** Die Bloecke, die sich andere Agenten schon vorgenommen haben. */
    /**
     * {Platz, Anzahl}: der wievielte (ab 0) unter den laufenden Agenten
     * derselben Art desselben Spielers dieser ist, und wie viele es sind.
     */
    static int[] rang(AgentArbeiter ich) {
        int platz = 0;
        int anzahl = 0;
        for (AgentArbeiter agent : AGENTEN) {
            if (agent.besitzer().equals(ich.besitzer()) && agent.auftrag() == ich.auftrag()
                    && !agent.beimZurueckkehren()) {
                anzahl++;
                if (agent.nummer() < ich.nummer()) {
                    platz++;
                }
            }
        }
        return new int[] {platz, Math.max(1, anzahl)};
    }

    static Set<Long> reserviertVonAnderen(AgentArbeiter ich) {
        Set<Long> belegt = new HashSet<>();
        for (AgentArbeiter agent : AGENTEN) {
            if (agent != ich && agent.reservierung() != Long.MIN_VALUE) {
                belegt.add(agent.reservierung());
            }
        }
        return belegt;
    }

    static void tick(MinecraftServer server) {
        ticks++;
        if (AGENTEN.isEmpty()) {
            if (ticks % 10 == 0) {
                AgentStatus.setzen(List.of());
            }
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
                modulAus(agent.auftrag(), agent.nummer());
                it.remove();
            }
        }
        if (ticks % 10 == 0) {
            List<AgentStatus> stand = new ArrayList<>();
            for (AgentArbeiter agent : AGENTEN) {
                Entity k = agent.koerper();
                if (k == null) {
                    continue;
                }
                stand.add(new AgentStatus(agent.besitzer(), agent.auftrag().name(), agent.nummer(), agent.titel(),
                        agent.zustandText(), agent.beute(), k.getX(), k.getY(), k.getZ(), weltName(k)));
            }
            AgentStatus.setzen(stand);
        }
    }

    /** "overworld", "the_nether" ... */
    static String weltName(Entity k) {
        String text = k.level().dimension().toString();
        int doppelpunkt = text.lastIndexOf(':');
        return text.substring(doppelpunkt + 1).replace("]", "").trim();
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
        ORTE.clear();
        KISTEN.clear();
        AgentStatus.leeren();
    }

    static void melden(ServerPlayer spieler, ChatFormatting farbe, String text) {
        spieler.sendSystemMessage(Component.literal("[Agent] " + text).withStyle(farbe));
    }

    /** Das Menue nachziehen: dieser Agent ist weg. Sind alle seiner Art weg, geht das Modul aus. */
    private static void modulAus(Auftrag auftrag, int nummer) {
        Minecraft.getInstance().execute(() -> {
            for (Module modul : GlowCubeClient.modules().all()) {
                if (modul instanceof AgentModul agent && agent.auftrag() == auftrag) {
                    agent.agentBeendet(nummer);
                }
            }
        });
    }
}
