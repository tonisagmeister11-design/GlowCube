package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

/**
 * Kein Rueckstoss mehr.
 *
 * <p>Nachgebildet dem {@code AntiKnockback} aus Aoba (GPL-3.0): den Rueckstoss
 * schickt der Server als Geschwindigkeitspaket
 * ({@code ClientboundSetEntityMotionPacket}) fuer den eigenen Spieler. Wird
 * genau dieses Paket verschluckt, bevor das Spiel es sieht, bleibt man beim
 * Treffer stehen statt weggeschleudert zu werden.
 */
public final class AntiKnockback extends Module {
    public AntiKnockback() {
        super("AntiKnockback", "Kein Rueckstoss beim Getroffenwerden", Category.COMBAT);
    }

    // Der Name des Zugriffs auf die Wesen-Nummer hat sich mit 26.x geaendert:
    // bis 1.21.x getId(), ab 26.x id() (das Paket ist jetzt ein Record). Ueber
    // Spiegelung greift dieselbe eine Klasse in beiden Fassungen.
    private static java.lang.reflect.Method idZugriff;
    private static boolean idGesucht;

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (!inGame()) {
            return false;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket p && wesenNummer(p) == player().getId()) {
            return true;   // Rueckstoss-Paket schlucken.
        }
        return false;
    }

    private static int wesenNummer(ClientboundSetEntityMotionPacket p) {
        if (!idGesucht) {
            idGesucht = true;
            for (String name : new String[]{"id", "getId"}) {
                try {
                    idZugriff = ClientboundSetEntityMotionPacket.class.getMethod(name);
                    break;
                } catch (NoSuchMethodException ignoriert) {
                    // die andere Fassung probieren
                }
            }
        }
        if (idZugriff == null) {
            return -1;
        }
        try {
            return (int) idZugriff.invoke(p);
        } catch (ReflectiveOperationException fehler) {
            return -1;
        }
    }
}
