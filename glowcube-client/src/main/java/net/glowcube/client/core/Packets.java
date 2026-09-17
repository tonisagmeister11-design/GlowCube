package net.glowcube.client.core;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.util.Rotations;
import net.minecraft.network.protocol.Packet;

/**
 * Die Tuer zwischen Mixin und Modulen.
 *
 * <p>Der Mixin auf {@code Connection} laeuft schon, bevor der Mod fertig
 * geladen ist, und auch noch, wenn die Modulliste beim Herunterfahren
 * abgeraeumt wurde. Ein Fehler an dieser Stelle landet mitten im Netz-Thread
 * und reisst die Verbindung ab. Deshalb geht hier alles durch einen
 * Torwaechter, der im Zweifel einfach nichts tut.
 */
public final class Packets {
    /**
     * Schutz gegen Endlosschleifen: ein Modul, das im Haken selbst ein Paket
     * schickt (FakeLag beim Ausschalten, Criticals mit seinen Positionen),
     * wuerde sonst wieder im Haken landen.
     */
    private static boolean drin;

    private Packets() {
    }

    public static boolean send(Packet<?> packet) {
        return verteilen(packet, true);
    }

    public static boolean receive(Packet<?> packet) {
        return verteilen(packet, false);
    }

    /** Vor dem Bewegungspaket. True heisst: gar nicht senden. */
    public static boolean sendMovement() {
        ModuleManager modules = GlowCubeClient.modules();
        if (drin || modules == null) {
            return false;
        }
        drin = true;
        try {
            Rotations.vorPaket();
            boolean unterbunden = modules.onSendMovement();
            if (unterbunden) {
                // Faellt das Paket ganz aus, laeuft der TAIL-Haken nie - dann
                // bliebe der gestellte Blick stehen und die Kamera haengt
                // fest. Also hier schon zuruecknehmen.
                Rotations.nachPaket();
            }
            return unterbunden;
        } catch (Throwable fehler) {
            GlowCubeClient.LOGGER.error("Fehler vor dem Bewegungspaket", fehler);
            Rotations.nachPaket();
            return false;
        } finally {
            drin = false;
        }
    }

    /** Nach dem Bewegungspaket: die gestellte Blickrichtung zuruecknehmen. */
    public static void afterMovement() {
        try {
            Rotations.nachPaket();
        } catch (Throwable fehler) {
            GlowCubeClient.LOGGER.error("Fehler nach dem Bewegungspaket", fehler);
        }
    }

    /** Ob die clientseitige Bewegung gerade unterbunden ist. */
    public static boolean blockClientMove() {
        ModuleManager modules = GlowCubeClient.modules();
        if (modules == null) {
            return false;
        }
        try {
            return modules.blockClientMove();
        } catch (Throwable fehler) {
            return false;
        }
    }

    private static boolean verteilen(Packet<?> packet, boolean hinaus) {
        if (drin || packet == null) {
            return false;
        }
        ModuleManager modules = GlowCubeClient.modules();
        if (modules == null) {
            return false;
        }
        drin = true;
        try {
            return hinaus ? modules.onPacketSend(packet) : modules.onPacketReceive(packet);
        } catch (Throwable fehler) {
            // Lieber ein Modul aus dem Tritt als eine abgerissene Verbindung.
            GlowCubeClient.LOGGER.error("Fehler beim Paket-Haken", fehler);
            return false;
        } finally {
            drin = false;
        }
    }
}
