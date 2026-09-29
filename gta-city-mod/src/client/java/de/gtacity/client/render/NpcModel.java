package de.gtacity.client.render;

import de.gtacity.entity.NpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The player model for city people, plus gestures: pointing the way, waving and talking with the hands. */
public class NpcModel extends HumanoidModel<NpcRenderer.State> {
    public NpcModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(NpcRenderer.State state) {
        super.setupAnim(state);
        float age = state.gestureAge;
        if (age < 0) {
            return;
        }
        float length = NpcEntity.GESTURE_TICKS[state.gesture];
        // Raise the arm in 6 ticks, hold, lower it in the last 8.
        float amount = Mth.clamp(Math.min(age / 6.0F, (length - age) / 8.0F), 0.0F, 1.0F);
        amount = amount * amount * (3 - 2 * amount);
        switch (state.gesture) {
            case NpcEntity.POINT -> {
                // Arm straight out towards the target, turned relative to the body (at most to the side).
                float rel = Mth.wrapDegrees(state.gestureYaw - state.bodyRot) * Mth.DEG_TO_RAD;
                boolean left = rel < -0.35F;
                ModelPart arm = left ? leftArm : rightArm;
                float yaw = Mth.clamp(rel, -1.4F, 1.4F);
                arm.xRot = Mth.lerp(amount, arm.xRot, -Mth.HALF_PI - 0.1F);
                arm.yRot = Mth.lerp(amount, arm.yRot, yaw);
                arm.zRot = Mth.lerp(amount, arm.zRot, 0.0F);
                head.yRot = Mth.lerp(amount * 0.8F, head.yRot, Mth.clamp(rel, -1.2F, 1.2F));
            }
            case NpcEntity.WAVE -> {
                rightArm.xRot = Mth.lerp(amount, rightArm.xRot, -2.7F);
                rightArm.yRot = Mth.lerp(amount, rightArm.yRot, 0.0F);
                rightArm.zRot = Mth.lerp(amount, rightArm.zRot, -0.25F + Mth.sin(age * 0.7F) * 0.45F);
            }
            case NpcEntity.TALK -> {
                rightArm.xRot = Mth.lerp(amount, rightArm.xRot, -0.8F + Mth.sin(age * 0.35F) * 0.3F);
                rightArm.zRot = Mth.lerp(amount, rightArm.zRot, 0.15F + Mth.sin(age * 0.23F) * 0.15F);
                leftArm.xRot = Mth.lerp(amount, leftArm.xRot, -0.6F + Mth.cos(age * 0.3F) * 0.3F);
                leftArm.zRot = Mth.lerp(amount, leftArm.zRot, -0.15F - Mth.sin(age * 0.27F) * 0.15F);
                head.xRot += Mth.sin(age * 0.5F) * 0.08F * amount;
            }
            default -> {
            }
        }
    }
}
