package net.glowcube.client.mixin;

import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** In welcher Hoehe eine Erzader entsteht - fuer OreSim. */
@Mixin(HeightRangePlacement.class)
public interface HeightRangePlacementAccessor {
    @Accessor("height")
    HeightProvider glowcube$hoehe();
}
