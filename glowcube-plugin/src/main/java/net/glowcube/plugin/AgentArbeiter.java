package net.glowcube.plugin;

import org.bukkit.entity.Entity;

import java.util.UUID;

/** Was jeder Agent (Abbau, Farm, Tunnel, Guardian, Jaeger, Builder) fuer das Plugin koennen muss. */
interface AgentArbeiter {
    UUID besitzer();

    Auftrag auftrag();

    /** Welcher Agent seiner Art (1 bis 5). */
    int nummer();

    String titel();

    boolean beimZurueckkehren();

    boolean fertig();

    void zurueckrufen();

    void einstellen(Werte werte);

    void tick();

    void notfallUebergabe();

    void aufraeumen();

    /** Der Koerper (fuer die Uebersicht) - kann null sein. */
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
