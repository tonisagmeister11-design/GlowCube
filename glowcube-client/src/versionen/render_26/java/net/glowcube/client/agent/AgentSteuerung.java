package net.glowcube.client.agent;

import net.glowcube.client.render.Netz;
import net.minecraft.network.chat.Component;

/**
 * Fassung fuer <b>26.x</b>: der Agent ist fuer 1.21.11 gebaut. Auf 26.3
 * unterscheiden sich die Server-Schnittstellen (Armschwung, Entity-Typen);
 * bis er dort nachgezogen ist, sagt dieser Platzhalter nur Bescheid.
 */
public final class AgentSteuerung {
    private AgentSteuerung() {
    }

    public static void registrieren() {
    }

    public static boolean starten(Auftrag auftrag, String art, AgentWerte werte) {
        Netz.nachricht(Component.literal("[Agent] Den Agenten gibt es bisher nur auf 1.21.11."), false);
        return false;
    }

    public static void zurueck(Auftrag auftrag) {
    }

    public static void einstellen(Auftrag auftrag, AgentWerte werte) {
    }

    public static String zielMarkieren() {
        return "Den Orbital Strike gibt es bisher nur auf 1.21.11.";
    }

    public static String zielSetzen(int x, int y, int z) {
        return zielMarkieren();
    }

    public static void alleZurueck() {
    }
}
