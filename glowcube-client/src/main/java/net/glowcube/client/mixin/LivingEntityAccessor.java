package net.glowcube.client.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * Springen von aussen ausloesen. Criticals braucht das fuer die Betriebsart
 * "Jump"; die Methode dahinter ist geschuetzt.
 *
 * <p>Dazu die laufenden Trankwirkungen fuer die Traenke-Anzeige - wie bei
 * AxolotlClient direkt aus dem Feld {@code activeEffects}, das auf 1.21.11
 * und 26.x gleich heisst.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Invoker("jumpFromGround")
    void glowcube$springen();

    @Accessor("activeEffects")
    Map<?, MobEffectInstance> glowcube$wirkungen();
}
