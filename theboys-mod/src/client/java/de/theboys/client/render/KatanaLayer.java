package de.theboys.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import de.theboys.TheBoys;
import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import de.theboys.power.PowerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** Black Noir's katana: sheathed across his back, drawn in his right hand while he cuts. */
public class KatanaLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = TheBoys.id("textures/entity/noir_katana.png");

	public KatanaLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || state.isInvisible) return;
		Entity entity = mc.level.getEntity(state.id);
		if (entity == null) return;
		PowerData data = PowerAttachments.power(entity);
		if (data.power() != Power.BLACK_NOIR || !data.suit()) return;
		boolean drawn = PowerAttachments.active(entity).has(ActiveState.KATANA);
		PlayerModel model = getParentModel();

		// the scabbard always hangs on his back
		poseStack.pushPose();
		model.body.translateAndRotate(poseStack);
		poseStack.translate(0f, 0.38f, 0.19f);
		poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-38f));
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
			// scabbard
			box(buffer, pose, light, 0f, 0.05f, 0f, 0.035f, 0.42f, 0.03f, 0.5f, 0.5f, 1f, 1f);
			if (!drawn) {
				// grip and guard sticking out over the shoulder
				box(buffer, pose, light, 0f, -0.5f, 0f, 0.07f, 0.012f, 0.06f, 0f, 0.5f, 0.5f, 1f);
				box(buffer, pose, light, 0f, -0.68f, 0f, 0.03f, 0.17f, 0.03f, 0.5f, 0f, 1f, 0.5f);
			}
		});
		poseStack.popPose();

		if (drawn) {
			poseStack.pushPose();
			model.rightArm.translateAndRotate(poseStack);
			// in the fist, blade pointing forward along the arm's swing
			poseStack.translate(-0.06f, 0.62f, -0.02f);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
				box(buffer, pose, light, 0f, 0f, 0.04f, 0.03f, 0.03f, 0.16f, 0.5f, 0f, 1f, 0.5f);
				box(buffer, pose, light, 0f, 0f, -0.13f, 0.07f, 0.07f, 0.012f, 0f, 0.5f, 0.5f, 1f);
				// the blade: long, thin, slightly curved upwards at the tip
				box(buffer, pose, light, 0f, 0.0f, -0.48f, 0.012f, 0.03f, 0.34f, 0f, 0f, 0.5f, 0.5f);
				box(buffer, pose, light, 0f, -0.012f, -0.9f, 0.01f, 0.024f, 0.09f, 0f, 0f, 0.5f, 0.5f);
			});
			poseStack.popPose();
		}
	}

	/** A box centred at x/y/z with half sizes hx/hy/hz, all faces showing the same texture region. */
	private static void box(VertexConsumer b, PoseStack.Pose pose, int light, float x, float y, float z, float hx, float hy, float hz,
			float u0, float v0, float u1, float v1) {
		float[][] faces = {
				{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
		};
		for (float[] n : faces) {
			float[][] q = corners(n, hx, hy, hz);
			float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
			for (int i = 0; i < 4; i++) {
				b.addVertex(pose, x + q[i][0], y + q[i][1], z + q[i][2]).setColor(0xFFFFFFFF).setUv(uv[i][0], uv[i][1])
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
			}
		}
	}

	/** The four corners of the face with normal n, counter-clockwise seen from outside. */
	private static float[][] corners(float[] n, float hx, float hy, float hz) {
		if (n[0] != 0) {
			float s = n[0];
			return new float[][] {{s * hx, -hy, -s * hz}, {s * hx, -hy, s * hz}, {s * hx, hy, s * hz}, {s * hx, hy, -s * hz}};
		}
		if (n[1] != 0) {
			float s = n[1];
			return new float[][] {{-hx, s * hy, -s * hz}, {hx, s * hy, -s * hz}, {hx, s * hy, s * hz}, {-hx, s * hy, s * hz}};
		}
		float s = n[2];
		return new float[][] {{s * hx, -hy, s * hz}, {-s * hx, -hy, s * hz}, {-s * hx, hy, s * hz}, {s * hx, hy, s * hz}};
	}
}
