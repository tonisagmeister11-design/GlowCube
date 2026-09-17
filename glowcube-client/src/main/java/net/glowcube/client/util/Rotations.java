package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Nach dem Vorbild von Meteor Client (GPL-3.0), {@code Rotations}.
 *
 * <p>Das Problem, das diese Klasse loest: der Server glaubt nur, was in den
 * Bewegungspaketen steht. Wer bauen oder schlagen will, muss dort hinsehen -
 * aber die Kamera soll nicht springen. Also wird der Blick genau fuer die
 * Dauer eines Pakets verstellt und danach wieder zurueckgesetzt. Was in
 * dieser Zeit passieren soll, haengt als Rueckruf an der Drehung.
 *
 * <p>Zwei Dinge sind aus dem Original uebernommen, weil sie sonst
 * Ungereimtheiten erzeugen:
 *
 * <ul>
 *   <li><b>Prioritaeten.</b> Wollen in einem Tick zwei Module in
 *       verschiedene Richtungen sehen, gewinnt die hoehere Zahl. Ohne das
 *       zappelt der gemeldete Blick.</li>
 *   <li><b>Nachlauf.</b> Nach der letzten Drehung bleibt der gemeldete Blick
 *       noch ein paar Ticks stehen, statt sofort zurueckzuspringen. Ein
 *       Sprung um 180 Grad in einem Tick ist genau das, worauf Server
 *       achten.</li>
 * </ul>
 *
 * <p><b>Vereinfacht gegenueber dem Original:</b> Meteor fuehrt eine ganze
 * Liste von Drehungen je Tick und arbeitet sie ueber mehrere Pakete ab.
 * Hier gibt es eine je Tick - die mit der hoechsten Prioritaet. Fuer die
 * Module, die es hier gibt, kommt das aufs Gleiche heraus.
 */
public final class Rotations {
    /** Wie viele Ticks der gemeldete Blick nach der letzten Drehung stehen bleibt. */
    private static final int NACHLAUF = 2;

    private static boolean angefordert;
    private static double zielYaw;
    private static double zielPitch;
    private static int prioritaet = Integer.MIN_VALUE;
    private static Runnable rueckruf;

    private static boolean verstellt;
    private static float vorherYaw;
    private static float vorherPitch;
    private static int nachlauf;

    /** Was der Server zuletzt als Blickrichtung gesehen hat. */
    private static float serverYaw;
    private static float serverPitch;

    private Rotations() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static void rotate(double yaw, double pitch, int prioritaet, Runnable rueckruf) {
        if (angefordert && prioritaet <= Rotations.prioritaet) {
            // Eine wichtigere Drehung liegt schon vor. Der Rueckruf will
            // trotzdem laufen, sonst faellt die Aktion still aus.
            if (rueckruf != null) {
                rueckruf.run();
            }
            return;
        }
        angefordert = true;
        zielYaw = yaw;
        zielPitch = pitch;
        Rotations.prioritaet = prioritaet;
        Rotations.rueckruf = rueckruf;
    }

    public static void rotate(double yaw, double pitch, int prioritaet) {
        rotate(yaw, pitch, prioritaet, null);
    }

    /** Wird unmittelbar vor dem Bewegungspaket gerufen. */
    public static void vorPaket() {
        if (mc().player == null) {
            zuruecksetzen();
            return;
        }
        if (!angefordert) {
            if (nachlauf > 0) {
                nachlauf--;
                anwenden(serverYaw, serverPitch);
            }
            return;
        }

        Runnable auszufuehren = rueckruf;
        anwenden((float) zielYaw, (float) zielPitch);
        serverYaw = (float) zielYaw;
        serverPitch = (float) zielPitch;
        nachlauf = NACHLAUF;

        angefordert = false;
        prioritaet = Integer.MIN_VALUE;
        rueckruf = null;

        if (auszufuehren != null) {
            auszufuehren.run();
        }
    }

    /** Wird unmittelbar nach dem Bewegungspaket gerufen. */
    public static void nachPaket() {
        if (!verstellt || mc().player == null) {
            verstellt = false;
            return;
        }
        mc().player.setYRot(vorherYaw);
        mc().player.setXRot(vorherPitch);
        verstellt = false;
    }

    private static void anwenden(float yaw, float pitch) {
        vorherYaw = mc().player.getYRot();
        vorherPitch = mc().player.getXRot();
        mc().player.setYRot(yaw);
        mc().player.setXRot(pitch);
        verstellt = true;
    }

    private static void zuruecksetzen() {
        angefordert = false;
        verstellt = false;
        nachlauf = 0;
        rueckruf = null;
        prioritaet = Integer.MIN_VALUE;
    }

    /** Ob gerade eine gestellte Blickrichtung gemeldet wird. */
    public static boolean dreht() {
        return angefordert || nachlauf > 0;
    }

    public static float serverYaw() {
        return dreht() ? serverYaw : (mc().player == null ? 0.0f : mc().player.getYRot());
    }

    public static float serverPitch() {
        return dreht() ? serverPitch : (mc().player == null ? 0.0f : mc().player.getXRot());
    }

    // ------------------------------------------------------------ Rechnerei

    public static double getYaw(Vec3 ziel) {
        if (mc().player == null) {
            return 0.0;
        }
        Vec3 auge = mc().player.getEyePosition();
        return Mth.wrapDegrees(
                Math.toDegrees(Math.atan2(ziel.z - auge.z, ziel.x - auge.x)) - 90.0);
    }

    public static double getPitch(Vec3 ziel) {
        if (mc().player == null) {
            return 0.0;
        }
        Vec3 auge = mc().player.getEyePosition();
        double dx = ziel.x - auge.x;
        double dy = ziel.y - auge.y;
        double dz = ziel.z - auge.z;
        double waagrecht = Math.sqrt(dx * dx + dz * dz);
        return Mth.wrapDegrees(-Math.toDegrees(Math.atan2(dy, waagrecht)));
    }

    public static double getYaw(BlockPos pos) {
        return getYaw(Vec3.atCenterOf(pos));
    }

    public static double getPitch(BlockPos pos) {
        return getPitch(Vec3.atCenterOf(pos));
    }
}
