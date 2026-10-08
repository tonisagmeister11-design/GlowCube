package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.theboys.client.ClientState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;

/** A fixed third-person camera (used for the automated screenshots); the player stays the camera entity. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Shadow
	public abstract boolean isDetached();

	/**
	 * The near clipping plane is 0.05 blocks: fine for a normal player, but tiny MiniMaus would see
	 * through every pixel wall she hugs. It shrinks with the camera entity.
	 */
	@ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"), index = 0)
	private float theboys$scaledNearPlane(float near) {
		if (net.minecraft.client.Minecraft.getInstance().getCameraEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
			float scale = living.getScale();
			if (scale < 1f) return near * Math.max(0.05f, scale);
		}
		return near;
	}

	@Inject(method = "update", at = @At("TAIL"))
	private void theboys$fixedCamera(DeltaTracker delta, CallbackInfo ci) {
		double[] o = ClientState.cameraOverride;
		if (o != null && isDetached()) {
			setRotation((float) o[3], (float) o[4]);
			setPosition(new Vec3(o[0], o[1], o[2]));
		}
	}
}
