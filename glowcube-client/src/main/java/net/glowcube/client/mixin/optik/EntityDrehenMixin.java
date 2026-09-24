package net.glowcube.client.mixin.optik;

import net.glowcube.client.module.optik.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook: die Mausbewegung dreht die freie Kamera statt der Figur. Wie bei
 * AxolotlClient am Anfang von {@code Entity.turn} - dort kommt jede
 * Mausbewegung an, auf 1.21.11 und 26.x gleich.
 */
@Mixin(Entity.class)
public abstract class EntityDrehenMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void glowcube$drehen(double dy, double dx, CallbackInfo ci) {
        if (net.glowcube.client.module.render.Freecam.aktiv() && (Object) this == Minecraft.getInstance().player) {
            net.glowcube.client.module.render.Freecam.drehen(dy, dx);
            ci.cancel();
            return;
        }
        if (Freelook.aktiv() && (Object) this == Minecraft.getInstance().getCameraEntity()) {
            Freelook.drehen(dy, dx);
            ci.cancel();
        }
    }
}
