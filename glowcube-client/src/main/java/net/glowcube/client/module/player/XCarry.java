package net.glowcube.client.module.player;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

/**
 * Vier zusaetzliche Trageplaetze im Bastelgitter des Rucksacks.
 *
 * <p>Nachgebildet dem {@code XCarry} aus Aoba (GPL-3.0): normalerweise raeumt
 * der Server die vier Felder des 2x2-Bastelgitters aus, sobald man den
 * Rucksack schliesst. Wird das Schliessen-Paket des eigenen Rucksacks
 * (Behaelter 0) verschluckt, erfaehrt der Server nie vom Schliessen - die
 * Sachen bleiben im Gitter liegen und man traegt vier Dinge mehr mit sich.
 */
public final class XCarry extends Module {
    public XCarry() {
        super("XCarry", "Vier Extra-Plaetze im Bastelgitter", Category.PLAYER);
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (packet instanceof ServerboundContainerClosePacket p && p.getContainerId() == 0) {
            return true;   // Schliessen des eigenen Rucksacks nicht melden.
        }
        return false;
    }
}
