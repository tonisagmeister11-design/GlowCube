package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.theboys.client.render.SmashPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/** Remembers which entity a render state belongs to (vanilla only keeps that for players). */
@Mixin(EntityRenderer.class)
public class EntityRendererIdMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
	private void theboys$rememberId(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		state.setData(SmashPose.ENTITY_ID, entity.getId());
	}
}
