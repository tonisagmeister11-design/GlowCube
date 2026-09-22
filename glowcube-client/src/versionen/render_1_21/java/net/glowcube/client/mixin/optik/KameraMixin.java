package net.glowcube.client.mixin.optik;

import net.glowcube.client.module.optik.Freelook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Freelook: die Kamera nimmt den freien Blick statt des Blicks der Figur.
 * Wie AxolotlClients CameraMixin an den setRotation-Aufrufen in
 * {@code Camera.setup} - so heisst die Methode auf dieser Fassung.
 */
@Mixin(Camera.class)
public abstract class KameraMixin {
    @Shadow
    protected abstract void setRotation(float gier, float neigung);

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void glowcube$freieKamera(Camera kamera, float gier, float neigung) {
        if (Freelook.aktiv()) {
            setRotation(Freelook.gier(), Freelook.neigung());
        } else {
            setRotation(gier, neigung);
        }
    }
}
