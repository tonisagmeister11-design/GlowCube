package net.glowcube.client.integration;

import net.glowcube.client.GlowCubeClient;

/**
 * Fassung fuer <b>26.x</b>: hier gibt es <b>kein</b> SeedCrackerX. Die Bruecke
 * bindet darum auch keine fremde Schnittstelle mehr ein (auf 1.21.x
 * {@code implements SeedCrackerAPI}) - genau das liess das Spiel abstuerzen,
 * sobald etwas die Klasse anfasste, weil die Schnittstelle in der JAR fehlt.
 *
 * <p>Uebrig bleibt ein schlichter Seed-Speicher fuer OreSim: seed() und
 * setzeSeed() halten einen von Hand gesetzten Wert, bits() gibt es ohne
 * SeedCrackerX nicht. SeedHunt und die Seed-Anzeige erscheinen auf 26.x gar
 * nicht erst (siehe ModuleManager und die 26.3-Fassung des ClickGUI).
 */
public final class SeedBridge {
    private static volatile Long gefundenerSeed;

    /** Der zuletzt bekannte Seed, oder null. */
    public static Long seed() {
        return gefundenerSeed;
    }

    /** Den Seed von Hand setzen; OreSim baut danach seine Karte neu auf. */
    public static void setzeSeed(Long seed) {
        gefundenerSeed = seed;
        try {
            if (GlowCubeClient.modules() != null) {
                GlowCubeClient.modules().get(
                        net.glowcube.client.module.world.OreSim.class).seedGewechselt();
            }
        } catch (RuntimeException fehler) {
            GlowCubeClient.LOGGER.warn("OreSim liess sich nicht benachrichtigen", fehler);
        }
    }

    /** Ohne SeedCrackerX gibt es keinen Fortschritt zu zeigen. */
    public static Double bits() {
        return null;
    }
}
