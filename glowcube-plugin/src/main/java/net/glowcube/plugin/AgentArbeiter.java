package net.glowcube.plugin;

import java.util.UUID;

/** Was jeder Agent (Abbau, Guardian, Builder) fuer das Plugin koennen muss. */
interface AgentArbeiter {
    UUID besitzer();

    Auftrag auftrag();

    String titel();

    boolean beimZurueckkehren();

    boolean fertig();

    void zurueckrufen();

    void einstellen(Werte werte);

    void tick();

    void notfallUebergabe();

    void aufraeumen();
}
