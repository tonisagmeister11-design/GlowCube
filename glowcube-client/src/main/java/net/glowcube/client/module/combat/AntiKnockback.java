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

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (!inGame()) {
            return false;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket p && p.getId() == player().getId()) {
            return true;   // Rueckstoss-Paket schlucken.
        }
        return false;
    }
}
