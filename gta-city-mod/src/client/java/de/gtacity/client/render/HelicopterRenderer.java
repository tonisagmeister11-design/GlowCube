package de.gtacity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.gtacity.GtaCity;
import de.gtacity.entity.HelicopterEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class HelicopterRenderer extends EntityRenderer<HelicopterEntity, HelicopterRenderer.State> {
    private static final Identifier TEXTURE = GtaCity.id("textures/entity/helicopter/police.png");
    private static final float SCALE = 1.35F;

    public static class State extends EntityRenderState {
        public float yRot;
        public float xRot;
        public float rotor;
    }

    private final HelicopterModel model;

    public HelicopterRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 2.0F;
        this.model = new HelicopterModel(context.bakeLayer(HelicopterModel.LAYER));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HelicopterEntity heli, State state, float partialTick) {
        super.extractRenderState(heli, state, partialTick);
        state.yRot = Mth.rotLerp(partialTick, heli.yRotO, heli.getYRot());
        state.xRot = Mth.lerp(partialTick, heli.xRotO, heli.getXRot());
        state.rotor = Mth.lerp(partialTick, heli.prevRotor, heli.rotor);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
        poseStack.rotateDegrees(Axis.XP, -state.xRot);
        poseStack.scale(-SCALE, -SCALE, SCALE);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
