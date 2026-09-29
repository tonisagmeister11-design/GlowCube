package de.gtacity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.gtacity.GtaCity;
import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

public class CarRenderer extends EntityRenderer<CarEntity, CarRenderer.State> {
    public static class State extends EntityRenderState {
        public float yRot;
        public float wheelRot;
        public float steer;
        public boolean siren;
        public boolean flash;
        public CarVariant variant = CarVariant.SEDAN_RED;
        public float doorDriver;
        public float doorPassenger;
    }

    private final Map<CarVariant.Shape, CarModel> models = new EnumMap<>(CarVariant.Shape.class);

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.3F;
        for (CarVariant.Shape shape : CarVariant.Shape.values()) {
            models.put(shape, new CarModel(context.bakeLayer(CarModel.layer(shape))));
        }
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CarEntity car, State state, float partialTick) {
        super.extractRenderState(car, state, partialTick);
        state.yRot = Mth.rotLerp(partialTick, car.yRotO, car.getYRot());
        state.wheelRot = Mth.lerp(partialTick, car.prevWheelRot, car.wheelRot);
        state.steer = car.steer;
        state.siren = car.isSirenOn();
        state.flash = (car.tickCount / 4) % 2 == 0;
        state.variant = car.getVariant();
        state.doorDriver = car.doorOpen(true, partialTick);
        state.doorPassenger = car.doorOpen(false, partialTick);
    }

    private static Identifier texture(CarVariant variant) {
        return GtaCity.id("textures/entity/car/" + variant.texture + ".png");
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        CarModel model = models.get(state.variant.shape);
        collector.submitModel(model, state, poseStack, texture(state.variant), state.lightCoords,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
