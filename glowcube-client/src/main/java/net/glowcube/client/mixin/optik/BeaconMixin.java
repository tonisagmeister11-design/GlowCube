package net.glowcube.client.mixin.optik;

import com.mojang.blaze3d.vertex.PoseStack;
import net.glowcube.client.module.optik.KeinBeaconStrahl;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Beacon-Strahl aus - nach AxolotlClients BeaconBlockEntityRendererMixin.
 * Dieselbe Methode {@code submitBeaconBeam} zeichnet auch den Strahl des
 * Endtransitportals; der bleibt wie bei AxolotlClient stehen.
 */
@Mixin(BeaconRenderer.class)
public abstract class BeaconMixin {
    @Inject(method = "submitBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/resources/Identifier;FFIIIFF)V",
            at = @At("HEAD"), cancellable = true)
    private static void glowcube$keinStrahl(PoseStack pose, SubmitNodeCollector sammler, Identifier textur,
                                            float a, float b, int c, int d, int e, float f, float g, CallbackInfo ci) {
        if (KeinBeaconStrahl.aktiv() && !textur.getPath().contains("end_gateway")) {
            ci.cancel();
        }
    }
}
