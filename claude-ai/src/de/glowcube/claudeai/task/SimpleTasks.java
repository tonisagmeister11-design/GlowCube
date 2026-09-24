package de.glowcube.claudeai.task;

import de.glowcube.claudeai.npc.Npc;

/** Kleine Bausteine fuer Plaene: warten, etwas sagen, etwas ausfuehren. */
public final class SimpleTasks {

    private SimpleTasks() {}

    public static Task say(String message) {
        return new Task() {
            @Override
            public String label() {
                return "rede";
            }

            @Override
            public Status tick(Npc npc) {
                npc.say(message);
                return Status.DONE;
            }
        };
    }

    public static Task wait(int ticks) {
        return new Task() {
            private int t;

            @Override
            public String label() {
                return "warte";
            }

            @Override
            public void start(Npc npc) {
                t = 0;
            }

            @Override
            public Status tick(Npc npc) {
                return ++t >= ticks ? Status.DONE : Status.RUNNING;
            }
        };
    }

    public static Task call(String label, Runnable action) {
        return new Task() {
            @Override
            public String label() {
                return label;
            }

            @Override
            public Status tick(Npc npc) {
                action.run();
                return Status.DONE;
            }
        };
    }
}
