package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

	@Inject(method = "update", at = @At("TAIL"))
	private void theboys$fixedCamera(DeltaTracker delta, CallbackInfo ci) {
		double[] o = ClientState.cameraOverride;
		if (o != null && isDetached()) {
			setRotation((float) o[3], (float) o[4]);
			setPosition(new Vec3(o[0], o[1], o[2]));
		}
	}
}
