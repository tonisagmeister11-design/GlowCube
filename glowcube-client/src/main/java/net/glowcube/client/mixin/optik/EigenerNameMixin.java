package net.glowcube.client.mixin.optik;

import net.glowcube.client.module.optik.EigenerName;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Das eigene Namensschild - wortgleich zu AxolotlClients LivingEntityRendererMixin. */
@Mixin(LivingEntityRenderer.class)
public abstract class EigenerNameMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            at = @At("HEAD"), cancellable = true)
    private void glowcube$eigenerName(LivingEntity wesen, double abstand, CallbackInfoReturnable<Boolean> cir) {
        if (EigenerName.aktiv() && wesen == Minecraft.getInstance().getCameraEntity()) {
            cir.setReturnValue(true);
        }
    }
}
