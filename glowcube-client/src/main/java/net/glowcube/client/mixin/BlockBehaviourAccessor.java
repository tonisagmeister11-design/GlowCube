package net.glowcube.client.mixin;

import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Ob ein Block ueberhaupt eine Kollision hat.
 *
 * <p>HoleESP braucht genau diese Flagge und nicht die Form: eine Druckplatte
 * hat eine Form, aber keine Kollision - sie waere also kein Hindernis in
 * einem Loch. Meteor greift an derselben Stelle zu.
 */
@Mixin(BlockBehaviour.class)
public interface BlockBehaviourAccessor {
    @Accessor("hasCollision")
    boolean glowcube$hatKollision();
}
