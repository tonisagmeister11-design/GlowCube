package net.glowcube.client.agent;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Was jeder Agent (Abbau, Guardian, Builder) fuer {@link AgentWelt} koennen muss. */
interface AgentArbeiter {
    UUID besitzer();

    Auftrag auftrag();

    String titel();

    boolean beimZurueckkehren();

    boolean fertig();

    void zurueckrufen();

    void einstellen(AgentWerte werte);

    void tick(MinecraftServer server);

    /** Welt schliesst oder Absturz: Beute (falls vorhanden) direkt uebergeben. */
    void notfallUebergabe(MinecraftServer server);

    void aufraeumen();
}
