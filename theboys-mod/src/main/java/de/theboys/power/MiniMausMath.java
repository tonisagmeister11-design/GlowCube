package de.theboys.power;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Multi Smash timing and geometry, shared by the server (moves the victim) and the client (turns its
 * body so it really swings around by the feet).
 */
public final class MiniMausMath {
	/** Ticks to rip the victim off the ground. */
	public static final int LIFT = 10;
	/** Ticks of one swing from one side over the head to the other. */
	public static final int SWING = 7;
	public static final int SLAMS = 5;
	/** After the last slam he spins once more and lets go. */
	public static final int THROW = LIFT + SWING * SLAMS + 3;

	private MiniMausMath() {
	}

	/** Swing angle in degrees: 0 = victim straight up over the head, +-max = head on the ground, + = to the right. */
	public static float angle(float t, float max) {
		if (t < LIFT) {
			float u = t / LIFT;
			return Mth.lerp(u * u * (3 - 2 * u), -max * 0.5f, 0f);
		}
		int k = Math.min(SLAMS - 1, (int) ((t - LIFT) / SWING));
		float u = Mth.clamp((t - LIFT - k * SWING) / SWING, 0f, 1f);
		float from = k == 0 ? 0f : side(k - 1) * max;
		float to = side(k) * max;
		if (t >= LIFT + SWING * SLAMS) {
			// the last slam is done: whirl round once more for the throw
			float v = Mth.clamp((t - LIFT - SWING * SLAMS) / 3f, 0f, 1f);
			return Mth.lerp(v, side(SLAMS - 1) * max, side(SLAMS - 1) * max * 0.3f);
		}
		// accelerate into the ground
		return Mth.lerp(u * u, from, to);
	}

	public static int side(int slam) {
		return slam % 2 == 0 ? 1 : -1;
	}

	/** True on the tick a slam hits the ground (1-based slam number), else 0. */
	public static int slamAt(int tick) {
		int rel = tick - LIFT + 1;
		if (rel <= 0 || rel % SWING != 0) return 0;
		int n = rel / SWING;
		return n <= SLAMS ? n : 0;
	}

	public static float handHeight(Entity holder) {
		return holder.getBbHeight() * 0.85f;
	}

	/** How far past the vertical it swings so the head really hits the ground. */
	public static float maxAngle(Entity holder, Entity victim) {
		float h = Math.max(0.3f, victim.getBbHeight());
		float c = Mth.clamp(-handHeight(holder) / h, -1f, 0f);
		return Math.min(150f, (float) Math.toDegrees(Math.acos(c)));
	}

	/** Where the victim's feet are held: in the holder's hands, a little in front of him. */
	public static Vec3 pivot(Entity holder, float pt) {
		Vec3 fwd = forward(holder.getYRot());
		return holder.getPosition(pt).add(0, handHeight(holder), 0).add(fwd.scale(0.2 + holder.getBbWidth() * 0.4));
	}

	public static Vec3 forward(float yaw) {
		double r = Math.toRadians(yaw);
		return new Vec3(-Math.sin(r), 0, Math.cos(r));
	}

	public static Vec3 right(float yaw) {
		double r = Math.toRadians(yaw);
		return new Vec3(-Math.cos(r), 0, -Math.sin(r));
	}

	/** Direction from the feet to the head of the swung victim. */
	public static Vec3 body(float yaw, float angleDeg) {
		double a = Math.toRadians(angleDeg);
		return new Vec3(0, Math.cos(a), 0).add(right(yaw).scale(Math.sin(a)));
	}
}
