package de.theboys.client.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The two halves of a body Butcher tore apart. Each half flies off, tumbles, lands and keeps
 * spraying blood out of the torn side, where the spine and ribs stick out.
 */
public final class TornBodies {
	private static final int LIFE = 160;
	private static final List<Half> HALVES = new ArrayList<>();

	private static final class Half {
		Vec3 pos;
		Vec3 prevPos;
		Vec3 vel;
		float yaw;
		float roll;
		float prevRoll;
		float spin;
		final float w;
		final float h;
		final float d;
		final int side;
		int age;
		boolean landed;

		Half(Vec3 pos, Vec3 vel, float yaw, float w, float h, int side, float spin) {
			this.pos = pos;
			this.prevPos = pos;
			this.vel = vel;
			this.yaw = yaw;
			this.w = w;
			this.h = h;
			this.d = Math.max(0.25f, w * 0.75f);
			this.side = side;
			this.spin = spin;
		}
	}

	private TornBodies() {
	}

	/** Splits a body of the given size at its feet position along the axis given by yaw (degrees). */
	public static void spawn(Vec3 feet, float width, float height, float axisYaw) {
		double rad = Math.toRadians(axisYaw);
		Vec3 axis = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
		float half = Math.max(0.15f, width / 2);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 start = feet.add(axis.scale(side * half / 2)).add(0, 0.6, 0);
			Vec3 vel = axis.scale(side * 0.32).add(0, 0.38, 0);
			HALVES.add(new Half(start, vel, axisYaw, half, height, side, side * 9f));
		}
		while (HALVES.size() > 40) HALVES.remove(0);
	}

	public static void tick(ClientLevel level) {
		RandomSource rnd = level.getRandom();
		Iterator<Half> it = HALVES.iterator();
		while (it.hasNext()) {
			Half b = it.next();
			b.age++;
			if (b.age > LIFE) {
				it.remove();
				continue;
			}
			b.prevPos = b.pos;
			b.prevRoll = b.roll;
			if (!b.landed) {
				b.vel = b.vel.add(0, -0.05, 0).scale(0.98);
				Vec3 next = b.pos.add(b.vel);
				BlockPos below = BlockPos.containing(next.x, next.y - 0.01, next.z);
				if (b.vel.y < 0 && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
					next = new Vec3(next.x, below.getY() + 1, next.z);
					b.landed = true;
					b.vel = Vec3.ZERO;
					// the half flops over onto its side
					b.roll = b.side * 90f;
					for (int i = 0; i < 25; i++) {
						level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
								next.x, next.y + 0.1, next.z, (rnd.nextDouble() - 0.5) * 0.4, 0.15, (rnd.nextDouble() - 0.5) * 0.4);
					}
				} else {
					b.roll += b.spin;
				}
				b.pos = next;
			}
			// blood keeps pumping out of the torn side for a while
			if (b.age < 70 && (b.age < 30 || b.age % 2 == 0)) {
				double rad = Math.toRadians(b.yaw);
				Vec3 axis = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
				Vec3 wound = b.pos.add(0, b.h * 0.5, 0).subtract(axis.scale(b.side * b.w * 0.5));
				Vec3 spray = axis.scale(-b.side * 0.25).add(0, 0.25 + rnd.nextDouble() * 0.2, 0);
				int n = b.age < 15 ? 6 : 2;
				for (int i = 0; i < n; i++) {
					level.addParticle(new DustParticleOptions(i % 3 == 0 ? 0x5A0000 : 0x9A0606, 1.3f),
							wound.x + (rnd.nextDouble() - 0.5) * 0.2, wound.y + (rnd.nextDouble() - 0.5) * b.h * 0.6, wound.z + (rnd.nextDouble() - 0.5) * 0.2,
							spray.x + (rnd.nextDouble() - 0.5) * 0.15, spray.y, spray.z + (rnd.nextDouble() - 0.5) * 0.15);
				}
				if (b.age % 4 == 0) {
					level.addParticle(ParticleTypes.DRIPPING_LAVA, wound.x, wound.y, wound.z, 0, 0, 0);
				}
			}
		}
	}

	public static boolean isEmpty() {
		return HALVES.isEmpty();
	}

	/** Draws all halves with the gore texture: bloody outside, torn flesh and bone on the inner face. */
	public static void draw(VertexConsumer buf, PoseStack.Pose pose, Vec3 cam, float pt, ClientLevel level) {
		for (Half b : HALVES) {
			Vec3 p = b.prevPos.add(b.pos.subtract(b.prevPos).scale(pt));
			float roll = b.prevRoll + (b.roll - b.prevRoll) * pt;
			float sink = b.age > LIFE - 40 ? (b.age - (LIFE - 40) + pt) / 40f * 0.4f : 0f;
			int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(p.x, p.y + 0.3, p.z));
			PoseStack ps = new PoseStack();
			ps.last().pose().set(pose.pose());
			ps.last().normal().set(pose.normal());
			ps.translate((float) (p.x - cam.x), (float) (p.y - cam.y - sink), (float) (p.z - cam.z));
			ps.mulPose(new org.joml.Matrix4f().rotationY((float) Math.toRadians(-b.yaw)));
			ps.mulPose(new org.joml.Matrix4f().rotationZ((float) Math.toRadians(roll)));
			box(buf, ps.last(), b, light);
		}
	}

	/** A box w (along the tearing axis, x) * h * d; the face towards the other half shows the torn side. */
	private static void box(VertexConsumer b, PoseStack.Pose pose, Half half, int light) {
		float x0 = half.side < 0 ? -half.w : 0, x1 = half.side < 0 ? 0 : half.w;
		float y0 = 0, y1 = half.h;
		float z0 = -half.d / 2, z1 = half.d / 2;
		// outside: left half of the texture; torn side: right half
		float ou0 = 0, ou1 = 0.5f, iu0 = 0.5f, iu1 = 1f;
		boolean innerIsMinX = half.side > 0;
		face(b, pose, light, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, innerIsMinX ? iu0 : ou0, innerIsMinX ? iu1 : ou1);
		face(b, pose, light, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1, 0, 0, innerIsMinX ? ou0 : iu0, innerIsMinX ? ou1 : iu1);
		face(b, pose, light, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0, 1, ou0, ou1);
		face(b, pose, light, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1, ou0, ou1);
		face(b, pose, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, ou0, ou1);
		face(b, pose, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, ou0, ou1);
	}

	private static void face(VertexConsumer b, PoseStack.Pose pose, int light,
			float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
			float nx, float ny, float nz, float u0, float u1) {
		v(b, pose, ax, ay, az, u0, 1, nx, ny, nz, light);
		v(b, pose, bx, by, bz, u0, 0, nx, ny, nz, light);
		v(b, pose, cx, cy, cz, u1, 0, nx, ny, nz, light);
		v(b, pose, dx, dy, dz, u1, 1, nx, ny, nz, light);
		// back side so it is never culled
		v(b, pose, dx, dy, dz, u1, 1, -nx, -ny, -nz, light);
		v(b, pose, cx, cy, cz, u1, 0, -nx, -ny, -nz, light);
		v(b, pose, bx, by, bz, u0, 0, -nx, -ny, -nz, light);
		v(b, pose, ax, ay, az, u0, 1, -nx, -ny, -nz, light);
	}

	private static void v(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float vv, float nx, float ny, float nz, int light) {
		b.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}
}
