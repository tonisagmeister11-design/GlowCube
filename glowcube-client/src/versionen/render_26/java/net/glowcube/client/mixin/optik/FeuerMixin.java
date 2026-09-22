package net.glowcube.client.mixin.optik;

import net.glowcube.client.module.optik.NiedrigesFeuer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Niedriges Feuer auf 26.x - wie bei AxolotlClient fuer 26.2 in der
 * Zeichen-Lambda von {@code submitFire}: die Verschiebung des Feuers nach
 * oben wird um 0,2 kleiner.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class FeuerMixin {
    @ModifyArg(method = "lambda$submitFire$0",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;translate(FFF)Lorg/joml/Matrix4f;"),
            index = 1)
    private static float glowcube$niedrigesFeuer(float hoehe) {
        return NiedrigesFeuer.aktiv() ? hoehe - 0.2f : hoehe;
    }
}
