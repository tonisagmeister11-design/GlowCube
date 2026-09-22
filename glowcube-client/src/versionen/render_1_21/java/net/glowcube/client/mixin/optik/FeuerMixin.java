package net.glowcube.client.mixin.optik;

import com.mojang.blaze3d.vertex.PoseStack;
import net.glowcube.client.module.optik.NiedrigesFeuer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Niedriges Feuer - wortgleich zu AxolotlClients InGameOverlayRendererMixin fuer 1.21.x. */
@Mixin(ScreenEffectRenderer.class)
public abstract class FeuerMixin {
    @Inject(method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"))
    private static void glowcube$niedrigesFeuer(PoseStack pose, MultiBufferSource puffer, TextureAtlasSprite bild,
                                               CallbackInfo ci) {
        if (NiedrigesFeuer.aktiv()) {
            pose.translate(0, -0.2F, 0);
        }
    }
}
