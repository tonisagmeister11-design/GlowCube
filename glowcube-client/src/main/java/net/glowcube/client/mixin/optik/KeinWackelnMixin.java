package net.glowcube.client.mixin.optik;

import net.glowcube.client.module.optik.KeinWackeln;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kein Kamerawackeln bei Treffern - wie AxolotlClients "noHurtCam": der
 * Anfang von {@code GameRenderer.bobHurt} bricht ab. Die Methode heisst auf
 * 1.21.11 und 26.x gleich, nur ihre Parameter unterscheiden sich; die
 * werden hier nicht gebraucht.
 */
@Mixin(GameRenderer.class)
public abstract class KeinWackelnMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void glowcube$keinWackeln(CallbackInfo ci) {
        if (KeinWackeln.aktiv()) {
            ci.cancel();
        }
    }
}
