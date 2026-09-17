package net.glowcube.client.mixin;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Welches Wesen in einem Schlagpaket steht.
 *
 * <p>Das Paket gibt die Nummer nicht her - man kommt nur ueber einen
 * Besucher daran, den das Spiel selbst benutzt. Criticals muss aber wissen,
 * ob ueberhaupt ein lebendes Wesen getroffen wird, bevor es Positionen
 * faelscht. Meteor greift an derselben Stelle zu.
 */
@Mixin(ServerboundInteractPacket.class)
public interface ServerboundInteractPacketAccessor {
    @Accessor("entityId")
    int glowcube$zielNummer();
}
