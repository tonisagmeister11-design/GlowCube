package net.glowcube.client.mixin;

import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Wie oft eine Erzader je Chunk versucht wird - fuer OreSim. */
@Mixin(CountPlacement.class)
public interface CountPlacementAccessor {
    @Accessor("count")
    IntProvider glowcube$anzahl();
}
