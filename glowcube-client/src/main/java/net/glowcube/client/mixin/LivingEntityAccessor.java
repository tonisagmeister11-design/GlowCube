package net.glowcube.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Springen von aussen ausloesen. Criticals braucht das fuer die Betriebsart
 * "Jump"; die Methode dahinter ist geschuetzt.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Invoker("jumpFromGround")
    void glowcube$springen();
}
