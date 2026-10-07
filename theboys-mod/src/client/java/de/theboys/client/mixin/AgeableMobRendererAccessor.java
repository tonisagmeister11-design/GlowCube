package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;

/** Babies have their own model. */
@Mixin(AgeableMobRenderer.class)
public interface AgeableMobRendererAccessor {
	@Accessor("adultModel")
	EntityModel<?> theboys$adultModel();

	@Accessor("babyModel")
	EntityModel<?> theboys$babyModel();
}
