package net.glowcube.client.agent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Was jeder Agent (Abbau, Farm, Tunnel, Guardian, Jaeger, Builder) fuer {@link AgentWelt} koennen muss. */
interface AgentArbeiter {
    UUID besitzer();

    Auftrag auftrag();

    /** Welcher Agent seiner Art (1 bis 5). */
    int nummer();

    String titel();

    boolean beimZurueckkehren();

    boolean fertig();

    void zurueckrufen();

    void einstellen(AgentWerte werte);

    void tick(MinecraftServer server);

    /** Welt schliesst oder Absturz: Beute (falls vorhanden) direkt uebergeben. */
    void notfallUebergabe(MinecraftServer server);

    void aufraeumen();

    /** Der Koerper (fuer Position und Uebersicht) - kann null sein. */
    Entity koerper();

    /** Kurz fuer die Uebersicht: "arbeitet", "zur Kiste" ... */
    String zustandText();

    /** Beute im Lager (Guardian: besiegte Gegner, Builder: gesetzte Bloecke). */
    int beute();

    /** Der Block, den er sich gerade vorgenommen hat - andere Agenten lassen ihn in Ruhe. */
    default long reservierung() {
        return Long.MIN_VALUE;
    }
}
