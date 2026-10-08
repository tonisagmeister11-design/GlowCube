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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** MiniMaus' round ears on the head and the long pink tail, attached to the player model so they move with it. */
public class MouseLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = TheBoys.id("textures/entity/minimaus_parts.png");

	public MouseLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || state.isInvisible) return;
		Entity entity = mc.level.getEntity(state.id);
		if (entity == null) return;
		PowerData data = PowerAttachments.power(entity);
		if (data.power() != Power.MINIMAUS || !data.suit()) return;
		PlayerModel model = getParentModel();
		float t = state.ageInTicks;
		boolean busy = PowerAttachments.active(entity).has(ActiveState.SMASH) || PowerAttachments.active(entity).has(ActiveState.MOON);
		// ears twitch now and then, and perk up when she fights
		float twitch = Mth.sin(t * 0.7f) * (Mth.sin(t * 0.05f) > 0.85f ? 0.25f : 0.02f) + (busy ? 0.15f : 0f);

		poseStack.pushPose();
		model.head.translateAndRotate(poseStack);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
			ear(buffer, pose, light, 1, twitch);
			ear(buffer, pose, light, -1, -twitch);
		});
		poseStack.popPose();

		float speed = (float) Math.min(1.0, entity.getDeltaMovement().horizontalDistance() * 6);
		poseStack.pushPose();
		model.body.translateAndRotate(poseStack);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> tail(buffer, pose, light, t, speed));
		poseStack.popPose();
	}

	/** A round ear on the side of the head (model space: y down, -z is the face). */
	private static void ear(VertexConsumer b, PoseStack.Pose pose, int light, int side, float twitch) {
		float cx = side * 0.25f, cy = -0.55f, cz = 0.02f;
		float h = 0.19f;
		float tilt = side * (0.32f + twitch);
		float cos = Mth.cos(tilt), sin = Mth.sin(tilt);
		float[][] corners = {{-h, -h}, {h, -h}, {h, h}, {-h, h}};
		float[][] uv = {{0f, 0f}, {0.5f, 0f}, {0.5f, 0.5f}, {0f, 0.5f}};
		// front (pink inside of the ear)
		for (int i = 0; i < 4; i++) {
			float x = corners[i][0], y = corners[i][1];
			vertex(b, pose, cx + x * cos - y * sin, cy + x * sin + y * cos, cz, uv[i][0], uv[i][1], 0, 0, -1, light);
		}
		// back (fur), a hair behind, wound the other way
		for (int i = 3; i >= 0; i--) {
			float x = corners[i][0], y = corners[i][1];
			vertex(b, pose, cx + x * cos - y * sin, cy + x * sin + y * cos, cz + 0.012f, uv[i][0] + 0.5f, uv[i][1], 0, 0, 1, light);
		}
	}

	/** Long, thin, ringed tail that curls up behind her and sways while she runs. */
	private static void tail(VertexConsumer b, PoseStack.Pose pose, int light, float t, float speed) {
		int n = 14;
		Vec3[] pts = new Vec3[n + 1];
		for (int i = 0; i <= n; i++) {
			float s = i / (float) n;
			float sway = Mth.sin(t * (0.12f + speed * 0.35f) + s * 3.2f) * (0.08f + speed * 0.12f) * s;
			double x = sway;
			double y = 0.66 + 0.12 * s - 0.55 * Math.sin(s * Math.PI * 0.85) * s;
			double z = 0.12 + 0.85 * s;
			pts[i] = new Vec3(x, y, z);
		}
		int sides = 6;
		for (int i = 0; i < n; i++) {
			Vec3 a = pts[i], c = pts[i + 1];
			Vec3 dir = c.subtract(a).normalize();
			Vec3[] uvw = Geo.basis(dir);
			float r0 = 0.045f - 0.03f * i / n;
			float r1 = 0.045f - 0.03f * (i + 1) / n;
			float v0 = 0.5f + 0.5f * i / n, v1 = 0.5f + 0.5f * (i + 1) / n;
			for (int k = 0; k < sides; k++) {
				double a0 = 2 * Math.PI * k / sides, a1 = 2 * Math.PI * (k + 1) / sides;
				Vec3 n0 = uvw[0].scale(Math.cos(a0)).add(uvw[1].scale(Math.sin(a0)));
				Vec3 n1 = uvw[0].scale(Math.cos(a1)).add(uvw[1].scale(Math.sin(a1)));
				float u0 = (float) k / sides, u1 = (float) (k + 1) / sides;
				put(b, pose, a.add(n0.scale(r0)), u0, v0, n0, light);
				put(b, pose, c.add(n0.scale(r1)), u0, v1, n0, light);
				put(b, pose, c.add(n1.scale(r1)), u1, v1, n1, light);
				put(b, pose, a.add(n1.scale(r0)), u1, v0, n1, light);
			}
		}
	}

	private static void put(VertexConsumer b, PoseStack.Pose pose, Vec3 p, float u, float v, Vec3 n, int light) {
		vertex(b, pose, (float) p.x, (float) p.y, (float) p.z, u, v, (float) n.x, (float) n.y, (float) n.z, light);
	}

	private static void vertex(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
		b.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}
}
