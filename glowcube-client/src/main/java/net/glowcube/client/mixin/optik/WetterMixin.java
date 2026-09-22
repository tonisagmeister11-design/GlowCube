package net.glowcube.client.mixin.optik;

import net.glowcube.client.module.optik.KeinRegen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kein Regen: die Welt auf dem Bildschirm meldet Regen- und Gewitterstaerke 0.
 * Nur die Client-Welt - die Welt des eingebauten Servers (Einzelspieler)
 * regnet weiter, Felder und Blitze bleiben also wie sie sind.
 */
@Mixin(Level.class)
public abstract class WetterMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void glowcube$regen(float teil, CallbackInfoReturnable<Float> cir) {
        if (KeinRegen.aktiv() && (Object) this instanceof ClientLevel) {
            cir.setReturnValue(0.0f);
        }
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void glowcube$gewitter(float teil, CallbackInfoReturnable<Float> cir) {
        if (KeinRegen.aktiv() && (Object) this instanceof ClientLevel) {
            cir.setReturnValue(0.0f);
        }
    }
}
