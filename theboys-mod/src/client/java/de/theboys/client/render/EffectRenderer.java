package de.theboys.client.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import de.theboys.TheBoys;
import de.theboys.client.ClientState;
import de.theboys.net.FxPayload;
import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import de.theboys.power.SoldierBoy;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Draws all power effects as real geometry in the world, so they look the same in first and third
 * person and for every other player: heat vision, Soldier Boy's chest beam and nuke, shock rings,
 * A-Train's lightning trail and Butcher's tendrils.
 */
public final class EffectRenderer {
	private static final Identifier TENTACLE = TheBoys.id("textures/entity/tentacle.png");
	private static final Identifier GORE = TheBoys.id("textures/entity/gore.png");
	/** Where each tendril leg of a Cancer-Walking Butcher stands (per player id). */
	private static final Map<Integer, Vec3[]> FEET = new HashMap<>();
	private static final Map<Integer, ArrayDeque<Vec3>> TRAILS = new HashMap<>();

	/** Which level render phase draws the effects (switchable for testing). */
	public static int phase = 0;
	public static int debugCalls;
	public static int debugGlows;
	public static int debugTendrils;

	private EffectRenderer() {
	}

	public static void renderCollect(LevelRenderContext context) {
		if (phase == 0) render(context);
	}

	public static void renderBeforeTranslucent(LevelRenderContext context) {
		if (phase == 1) render(context);
	}

	public static void renderAfterTranslucentFeatures(LevelRenderContext context) {
		if (phase == 2) render(context);
	}

	/** Called every client tick: remembers where speedsters were, for the lightning trail. */
	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			TRAILS.clear();
			return;
		}
		for (AbstractClientPlayer p : level.players()) {
			boolean running = PowerAttachments.powerOf(p) == Power.A_TRAIN && PowerAttachments.active(p).has(ActiveState.SPEED);
			ArrayDeque<Vec3> trail = TRAILS.get(p.getId());
			if (running) {
				if (trail == null) {
					trail = new ArrayDeque<>();
					TRAILS.put(p.getId(), trail);
				}
				trail.addFirst(p.position());
				while (trail.size() > 9) trail.pollLast();
			} else if (trail != null) {
				trail.pollLast();
				if (trail.isEmpty()) TRAILS.remove(p.getId());
			}
		}
		TRAILS.keySet().removeIf(id -> level.getEntity(id) == null);
	}

	public static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) return;
		Vec3 cam = context.levelState().cameraRenderState.pos;
		float pt = context.levelState().worldPartialTicks;
		boolean firstPerson = context.levelState().cameraRenderState.isFirstPerson;
		long time = level.getGameTime();

		List<Glow> glows = new ArrayList<>();
		List<Tendril> tendrils = new ArrayList<>();

		for (AbstractClientPlayer p : level.players()) {
			Power power = PowerAttachments.powerOf(p);
			if (power == Power.NONE) continue;
			ActiveState state = PowerAttachments.active(p);
			boolean self = p == mc.player && firstPerson;

			if (state.has(ActiveState.LASER)) {
				heatVision(level, p, pt, self, time, glows);
			}
			if (state.has(ActiveState.CHEST_BEAM)) {
				chestBeam(level, p, pt, time, self, glows);
			}
			if (state.has(ActiveState.NUKE_CHARGE)) {
				float c = Math.min(1f, state.charge() / (float) SoldierBoy.MAX_CHARGE);
				Vec3 chest = chest(p, pt).add(p.getViewVector(pt).scale(0.3));
				double pulse = 1 + 0.12 * Math.sin((time + pt) * (0.6 + c));
				glows.add(Glow.sphere(chest, (0.2 + 0.55 * c) * pulse, argb(0.35f + 0.4f * c, 0xFF8C1A)));
				glows.add(Glow.sphere(chest, (0.08 + 0.25 * c) * pulse, argb(0.8f, 0xFFF2CC)));
			}
			if (power == Power.BUTCHER && state.has(ActiveState.FRENZY)) {
				frenzyTendrils(p, pt, time, self, tendrils);
			}
			if (power == Power.BUTCHER && state.has(ActiveState.CANCER_WALK)) {
				walkingLegs(level, p, pt, time, self, tendrils);
			} else {
				FEET.remove(p.getId());
			}
			if (power == Power.BUTCHER && (state.has(ActiveState.HOLD) || state.has(ActiveState.RIP))) {
				Entity target = level.getEntity(state.targetId());
				if (target != null) {
					heldTendrils(p, target, pt, time, state.has(ActiveState.RIP), self, tendrils);
				}
			}
		}

		// lightning trails of running speedsters
		for (Map.Entry<Integer, ArrayDeque<Vec3>> en : TRAILS.entrySet()) {
			Entity e = level.getEntity(en.getKey());
			if (e == null || e == mc.player && firstPerson) continue;
			boolean rewind = PowerAttachments.active(e).has(ActiveState.REWIND);
			lightningTrail(e, en.getKey(), en.getValue(), pt, time, rewind, cam, glows);
		}

		// one-off effects
		for (ClientState.Fx fx : ClientState.EFFECTS) {
			float age = fx.age + pt;
			FxPayload d = fx.data;
			Vec3 at = new Vec3(d.x(), d.y(), d.z());
			switch (d.kind()) {
				case FxPayload.TENDRIL_STRIKE -> {
					Entity src = level.getEntity(d.entityId());
					if (src == null) break;
					float out = Math.min(1f, age / 3.5f);
					float back = fx.duration - age < 6 ? Math.max(0f, (fx.duration - age) / 6f) : 1f;
					float reach = Math.min(out, back);
					Vec3 from = chest(src, pt).add(src.getViewVector(pt).scale(0.25));
					tendrils.add(Tendril.curve(from, at, reach, time + pt, d.entityId(), 0.22, src == mc.player && firstPerson));
				}
				case FxPayload.NUKE -> {
					float r = d.x2();
					float grow = 1 - (float) Math.pow(1 - Math.min(1f, age / 12f), 3);
					float fade = age < 12 ? 1f : Math.max(0f, 1 - (age - 12) / (fx.duration - 12f));
					glows.add(Glow.sphere(at, r * grow, argb(0.55f * fade, 0xFF7A00)));
					glows.add(Glow.sphere(at, r * grow * 0.75, argb(0.45f * fade, 0xFFB347)));
					glows.add(Glow.sphere(at, r * grow * 0.4, argb(0.9f * fade, 0xFFF2CC)));
					glows.add(Glow.ring(at, new Vec3(0, 1, 0), r * 1.4 * grow, r * 1.4 * grow + 1.2, argb(0.7f * fade, 0xFFD180)));
				}
				case FxPayload.SHOCKWAVE -> {
					float t = Math.min(1f, age / fx.duration);
					float fade = 1 - t;
					if (Float.isNaN(d.y2())) {
						double r = d.x2() * (1 - Math.pow(1 - t, 2));
						glows.add(Glow.ring(at, new Vec3(0, 1, 0), r, r + 0.45, argb(0.6f * fade, 0xFFFFFF)));
					} else {
						Vec3 dir = Vec3.directionFromRotation(d.z2(), d.y2());
						Vec3 c = at.add(dir.scale(t * d.x2() * 0.8));
						double r = 0.6 + t * d.x2() * 0.35;
						glows.add(Glow.ring(c, dir, r, r + 0.35, argb(0.55f * fade, 0xE3F2FD)));
						glows.add(Glow.ring(c.subtract(dir.scale(0.8)), dir, r * 0.7, r * 0.7 + 0.25, argb(0.35f * fade, 0xFFFFFF)));
					}
				}
				default -> { }
			}
		}

		debugCalls++;
		debugGlows = glows.size();
		debugTendrils = tendrils.size();
		if (!glows.isEmpty()) {
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
				for (Glow g : glows) g.draw(buffer, pose, cam);
			});
		}
		if (!TornBodies.isEmpty()) {
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(GORE), (pose, buffer) -> {
				TornBodies.draw(buffer, pose, cam, pt, level);
			});
			// halves cut out of the victim's own model, drawn with its own skin
			for (TornBodies.Half half : TornBodies.meshHalves()) {
				context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(TornBodies.texture(half)), (pose, buffer) -> {
					TornBodies.drawSkin(half, buffer, pose, cam, pt, level);
				});
			}
		}
		if (!tendrils.isEmpty()) {
			List<Integer> lights = new ArrayList<>();
			for (Tendril t : tendrils) {
				lights.add(LightCoordsUtil.getLightCoords(level, BlockPos.containing(t.points.get(t.points.size() / 2))));
			}
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(TENTACLE), (pose, buffer) -> {
				for (int i = 0; i < tendrils.size(); i++) tendrils.get(i).draw(buffer, pose, cam, lights.get(i));
			});
		}
	}

	// ------------------------------------------------------------------ powers

	private static void heatVision(ClientLevel level, AbstractClientPlayer p, float pt, boolean self, long time, List<Glow> glows) {
		Vec3 eye = p.getEyePosition(pt);
		Vec3 dir = p.getViewVector(pt);
		Vec3 end = hit(level, p, eye, dir, 64);
		Vec3 right = dir.cross(new Vec3(0, 1, 0)).normalize();
		if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);
		double flicker = 1 + 0.15 * Math.sin((time + pt) * 2.3);
		double scale = self ? 0.45 : 1.0;
		for (int side = -1; side <= 1; side += 2) {
			Vec3 start = eye.add(right.scale((self ? 0.16 : 0.095) * side)).add(dir.scale(self ? 1.1 : 0.2));
			if (self) start = start.add(0, -0.2, 0);
			glows.add(Glow.beam(start, end, 0.075 * flicker * scale, argb(0.35f, 0xFF1A1A)));
			glows.add(Glow.beam(start, end, 0.035 * scale, argb(0.75f, 0xFF3020)));
			glows.add(Glow.beam(start, end, 0.012 * scale, argb(0.95f, 0xFFE0D0)));
			if (!self) glows.add(Glow.sphere(start, 0.06, argb(0.9f, 0xFF2A1A)));
		}
		glows.add(Glow.sphere(end, 0.25 * flicker, argb(0.6f, 0xFF4020)));
		glows.add(Glow.sphere(end, 0.1, argb(0.9f, 0xFFF0C0)));
	}

	private static void chestBeam(ClientLevel level, AbstractClientPlayer p, float pt, long time, boolean self, List<Glow> glows) {
		Vec3 dir = p.getViewVector(pt);
		Vec3 start = chest(p, pt).add(dir.scale(self ? 1.2 : 0.35));
		Vec3 end = hit(level, p, start, dir, 40);
		double wobble = 1 + 0.1 * Math.sin((time + pt) * 1.7);
		glows.add(Glow.beam(start, end, 0.38 * wobble, argb(0.3f, 0xFF6A00)));
		glows.add(Glow.beam(start, end, 0.2 * wobble, argb(0.55f, 0xFF9A30)));
		glows.add(Glow.beam(start, end, 0.07, argb(0.95f, 0xFFF2CC)));
		glows.add(Glow.sphere(start, 0.45 * wobble, argb(0.5f, 0xFF8C1A)));
		glows.add(Glow.sphere(end, 0.9 * wobble, argb(0.45f, 0xFF7A00)));
	}

	private static void lightningTrail(Entity e, int id, ArrayDeque<Vec3> trail, float pt, long time, boolean rewind, Vec3 cam, List<Glow> glows) {
		if (trail.size() < 2) return;
		List<Vec3> path = new ArrayList<>();
		path.add(e.getPosition(pt));
		for (Vec3 p : trail) {
			// the trail runs right through a third-person camera behind the runner; stop before it
			if (p.add(0, 1, 0).distanceToSqr(cam) < 6.0) break;
			path.add(p);
		}
		if (path.size() < 2) return;
		int bolts = rewind ? 5 : 3;
		int outer = rewind ? 0x40E0FF : 0x5AA9FF;
		Random rnd = new Random(id * 31L + time * 7L);
		for (int k = 0; k < bolts; k++) {
			double height = 0.3 + 1.3 * k / Math.max(1, bolts - 1);
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i < path.size(); i++) {
				Vec3 base = path.get(i).add(0, height, 0);
				double spread = 0.15 + 0.08 * i;
				pts.add(base.add((rnd.nextDouble() - 0.5) * spread, (rnd.nextDouble() - 0.5) * spread, (rnd.nextDouble() - 0.5) * spread));
				if (i + 1 < path.size()) {
					Vec3 mid = base.add(path.get(i + 1).add(0, height, 0)).scale(0.5);
					pts.add(mid.add((rnd.nextDouble() - 0.5) * spread * 2, (rnd.nextDouble() - 0.5) * spread * 2, (rnd.nextDouble() - 0.5) * spread * 2));
				}
			}
			glows.add(Glow.bolt(pts, 0.045, argb(0.45f, outer)));
			glows.add(Glow.bolt(pts, 0.015, argb(0.95f, 0xF0FAFF)));
		}
	}

	private static void heldTendrils(AbstractClientPlayer butcher, Entity target, float pt, long time, boolean ripping, boolean self, List<Tendril> out) {
		Vec3 from = chest(butcher, pt).add(butcher.getViewVector(pt).scale(0.25));
		Vec3 center = target.getPosition(pt).add(0, target.getBbHeight() / 2, 0);
		double t = time + pt;
		int count = ripping ? 4 : 2;
		Vec3 side = butcher.getViewVector(pt).cross(new Vec3(0, 1, 0)).normalize();
		for (int i = 0; i < count; i++) {
			double angle = t * 0.25 + i * Math.PI * 2 / count;
			Vec3 offset;
			if (ripping) {
				// tendrils grab the victim at both sides and pull outwards
				double pull = 0.35 + 0.15 * Math.sin(t * 0.9 + i);
				offset = side.scale((i % 2 == 0 ? 1 : -1) * (target.getBbWidth() * 0.5 + pull)).add(0, (i < 2 ? 0.35 : -0.35) * target.getBbHeight(), 0);
			} else {
				offset = new Vec3(Math.cos(angle) * target.getBbWidth() * 0.55, Math.sin(angle * 0.7) * target.getBbHeight() * 0.3, Math.sin(angle) * target.getBbWidth() * 0.55);
			}
			out.add(Tendril.curve(from, center.add(offset), 1f, t, butcher.getId() * 7 + i, 0.2, self));
		}
		// the "viper's nest": short tendrils writhing out of his chest
		for (int i = 0; i < 3; i++) {
			double a = t * 0.2 + i * 2.1;
			Vec3 tip = from.add(Math.cos(a) * 0.7, 0.35 + Math.sin(a * 1.3) * 0.4, Math.sin(a) * 0.7);
			out.add(Tendril.curve(from, tip, 1f, t, butcher.getId() * 13 + i, 0.11, self));
		}
	}

	/** Super Cancer: a nest of tendrils bursting out of his whole body. */
	private static void frenzyTendrils(AbstractClientPlayer p, float pt, long time, boolean self, List<Tendril> out) {
		Vec3 base = p.getPosition(pt);
		double t = time + pt;
		for (int i = 0; i < 9; i++) {
			double a = i * 0.7 + t * 0.12;
			double h = 0.5 + (i % 3) * 0.45;
			Vec3 root = base.add(Math.cos(a) * 0.25, h, Math.sin(a) * 0.25);
			double reach = 1.8 + Math.sin(t * 0.3 + i) * 0.6;
			Vec3 tip = root.add(Math.cos(a) * reach, 0.6 + Math.sin(t * 0.25 + i * 1.7) * 0.9, Math.sin(a) * reach);
			out.add(Tendril.curve(root, tip, 1f, t, p.getId() * 31 + i, 0.13, self));
		}
	}

	/** Cancer Walk: six tendril legs that step along the ground and carry him. */
	private static void walkingLegs(ClientLevel level, AbstractClientPlayer p, float pt, long time, boolean self, List<Tendril> out) {
		Vec3 body = p.getPosition(pt);
		Vec3 hip = body.add(0, 0.55, 0);
		Vec3 motion = p.position().subtract(p.xo, p.yo, p.zo);
		Vec3[] feet = FEET.computeIfAbsent(p.getId(), id -> new Vec3[6]);
		double yaw = Math.toRadians(p.yBodyRot);
		for (int i = 0; i < 6; i++) {
			double a = yaw + Math.PI / 2 + (i - 2.5) * (Math.PI / 3.2);
			// feet are placed a little ahead when walking so the legs reach forward
			Vec3 desiredXZ = body.add(Math.cos(a) * 2.1, 0, Math.sin(a) * 2.1).add(motion.x * 6, 0, motion.z * 6);
			double gy = de.theboys.client.SupeMovement.groundBelow(level, new Vec3(desiredXZ.x, body.y, desiredXZ.z));
			Vec3 desired = new Vec3(desiredXZ.x, gy, desiredXZ.z);
			if (feet[i] == null || feet[i].distanceToSqr(desired) > 2.6 * 2.6 && (time + i) % 3 == 0) {
				feet[i] = desired;
			}
			Vec3 foot = feet[i];
			// small lift on the foot that is moving
			double sway = Math.sin(time * 0.4 + i * 1.1) * 0.08;
			Vec3 knee = hip.add(foot.subtract(hip).scale(0.5)).add(0, 1.4 + sway, 0);
			List<Vec3> pts = new ArrayList<>();
			for (int k = 0; k <= 12; k++) {
				double s = k / 12.0;
				double u = 1 - s;
				pts.add(hip.scale(u * u).add(knee.scale(2 * u * s)).add(foot.scale(s * s)));
			}
			out.add(new Tendril(pts, 0.17));
		}
	}

	// ------------------------------------------------------------------ helpers

	private static Vec3 chest(Entity e, float pt) {
		return e.getPosition(pt).add(0, e.getBbHeight() * 0.72, 0);
	}

	/** Where a beam from start along dir stops (first block or entity), client-side. */
	private static Vec3 hit(ClientLevel level, Entity source, Vec3 start, Vec3 dir, double range) {
		Vec3 end = start.add(dir.scale(range));
		HitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, source, start, stop, new AABB(start, stop).inflate(1),
				e -> e != source && e.isAlive() && e.isPickable() && !e.isSpectator(), 0.3f);
		return entity != null ? entity.getLocation() : stop;
	}

	private static int argb(float alpha, int rgb) {
		int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** A piece of glowing geometry, stored in world coordinates. */
	private record Glow(int kind, Vec3 a, Vec3 b, double r0, double r1, List<Vec3> pts, int color) {
		static Glow beam(Vec3 from, Vec3 to, double radius, int color) {
			return new Glow(0, from, to, radius, 0, null, color);
		}

		static Glow sphere(Vec3 c, double radius, int color) {
			return new Glow(1, c, null, radius, 0, null, color);
		}

		static Glow ring(Vec3 c, Vec3 normal, double r0, double r1, int color) {
			return new Glow(2, c, normal, r0, r1, null, color);
		}

		static Glow bolt(List<Vec3> pts, double width, int color) {
			return new Glow(3, null, null, width, 0, pts, color);
		}

		void draw(VertexConsumer buf, PoseStack.Pose pose, Vec3 cam) {
			switch (kind) {
				case 0 -> Geo.beam(buf, pose, a.subtract(cam), b.subtract(cam), r0, color);
				case 1 -> Geo.sphere(buf, pose, a.subtract(cam), r0, color, r0 > 3 ? 28 : 14);
				case 2 -> Geo.ring(buf, pose, a.subtract(cam), b, r0, r1, color, r1 > 6 ? 64 : 32);
				default -> {
					List<Vec3> rel = new ArrayList<>(pts.size());
					for (Vec3 p : pts) rel.add(p.subtract(cam));
					Geo.bolt(buf, pose, rel, r0, color);
				}
			}
		}
	}

	/** A tendril, stored as world-space centre line points. */
	private record Tendril(List<Vec3> points, double radius) {
		static Tendril curve(Vec3 from, Vec3 to, float reach, double time, int seed, double radius, boolean firstPersonSelf) {
			Vec3 delta = to.subtract(from);
			double len = delta.length();
			Vec3[] uv = Geo.basis(delta.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : delta);
			Random rnd = new Random(seed);
			double phase = rnd.nextDouble() * Math.PI * 2;
			Vec3 c1 = from.add(delta.scale(0.3)).add(uv[0].scale(Math.sin(phase) * len * 0.25)).add(0, len * 0.15, 0);
			Vec3 c2 = from.add(delta.scale(0.7)).add(uv[1].scale(Math.cos(phase) * len * 0.2));
			int segments = Math.max(6, Math.min(26, (int) (len * 3)));
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i <= segments; i++) {
				double t = reach * i / (double) segments;
				Vec3 p = bezier(from, c1, c2, to, t);
				double wave = Math.sin(time * 0.35 + t * 9 + phase) * 0.14 * Math.sin(Math.PI * t);
				p = p.add(uv[0].scale(wave)).add(uv[1].scale(Math.cos(time * 0.27 + t * 7 + phase) * 0.08 * Math.sin(Math.PI * t)));
				pts.add(p);
			}
			if (firstPersonSelf) {
				// push the root a bit down so the tendril does not fill the screen in first person
				pts.set(0, pts.get(0).add(0, -0.35, 0));
			}
			return new Tendril(pts, radius);
		}

		private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double t) {
			double u = 1 - t;
			return a.scale(u * u * u).add(b.scale(3 * u * u * t)).add(c.scale(3 * u * t * t)).add(d.scale(t * t * t));
		}

		void draw(VertexConsumer buf, PoseStack.Pose pose, Vec3 cam, int light) {
			List<Vec3> rel = new ArrayList<>(points.size());
			for (Vec3 p : points) rel.add(p.subtract(cam));
			Geo.tube(buf, pose, rel, radius, radius * 0.3, light, 7);
		}
	}

}
