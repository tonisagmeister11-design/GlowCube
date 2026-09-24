package de.glowcube.claudeai.task;

import de.glowcube.claudeai.npc.Npc;

/**
 * Eine Aufgabe, die ueber viele Ticks laeuft. Aufgaben muessen neu startbar sein:
 * wird eine unterbrochen (z.B. weil ein Monster angreift), ruft Npc spaeter wieder start().
 */
public abstract class Task {

    public enum Status { RUNNING, DONE, FAILED }

    private String error;
    /** Aufgaben, die zu einem Plan gehoeren, brechen bei Fehlern den ganzen Plan ab. */
    public int plan;

    /** Kurzbeschreibung fuer "was machst du gerade?" */
    public abstract String label();

    public void start(Npc npc) {}

    public abstract Status tick(Npc npc);

    public void stop(Npc npc) {}

    public String error() {
        return error;
    }

    protected Status fail(String message) {
        this.error = message;
        return Status.FAILED;
    }
}
