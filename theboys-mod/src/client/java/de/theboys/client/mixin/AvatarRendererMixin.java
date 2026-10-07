package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.theboys.TheBoys;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import de.theboys.power.PowerData;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Supes wear their suit: the player's skin is swapped for the character's (and Homelander gets his cape). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin<AvatarlikeEntity extends Avatar & ClientAvatarEntity> {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
	private void theboys$suit(AvatarlikeEntity entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		PowerData data = PowerAttachments.power(entity);
		Power power = data.power();
		if (power == Power.NONE || !data.suit()) {
			return;
		}
		Identifier id = TheBoys.id("suit/" + power.id());
		ClientAsset.ResourceTexture body = new ClientAsset.ResourceTexture(id, TheBoys.id("textures/entity/suit/" + power.id() + ".png"));
		ClientAsset.Texture cape = state.skin.cape();
		if (power == Power.HOMELANDER) {
			cape = new ClientAsset.ResourceTexture(TheBoys.id("homelander_cape"), TheBoys.id("textures/entity/homelander_cape.png"));
			state.showCape = true;
		}
		state.skin = new PlayerSkin(body, cape, state.skin.elytra(), PlayerModelType.WIDE, true);
		state.showHat = true;
		state.showJacket = true;
		state.showLeftSleeve = true;
		state.showRightSleeve = true;
		state.showLeftPants = true;
		state.showRightPants = true;
	}
}
