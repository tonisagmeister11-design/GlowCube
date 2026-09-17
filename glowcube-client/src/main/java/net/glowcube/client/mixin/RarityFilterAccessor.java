package net.glowcube.client.mixin;

import net.minecraft.world.level.levelgen.placement.RarityFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Der Seltenheitsteiler einer Erzader - fuer OreSim. */
@Mixin(RarityFilter.class)
public interface RarityFilterAccessor {
    @Accessor("chance")
    int glowcube$haeufigkeit();
}
