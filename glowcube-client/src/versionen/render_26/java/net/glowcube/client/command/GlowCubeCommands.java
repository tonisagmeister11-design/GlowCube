package net.glowcube.client.command;

/**
 * Fassung fuer <b>26.3 und neuer</b>: die /glowcube-Kommandos haengen an
 * Fabrics Kommando-API, die ab 26.x umbenannt wurde. Die Anbindung folgt;
 * SeedHunt findet den Seed ohnehin von selbst.
 */
public final class GlowCubeCommands {
    private GlowCubeCommands() {
    }

    public static void registrieren() {
        // Bewusst leer auf 26.3.
    }
}
