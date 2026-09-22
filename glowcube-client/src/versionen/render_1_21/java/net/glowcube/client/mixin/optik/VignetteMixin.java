package net.glowcube.client.mixin.optik;

import net.glowcube.client.module.optik.KeineVignette;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keine Vignette - wie AxolotlClients "removeVignette", hier an {@code Gui.renderVignette}. */
@Mixin(Gui.class)
public abstract class VignetteMixin {
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void glowcube$keineVignette(CallbackInfo ci) {
        if (KeineVignette.aktiv()) {
            ci.cancel();
        }
    }
}
