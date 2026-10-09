package de.theboys.client;

import java.util.HashMap;
import java.util.Map;

import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * How far a player's whole body is tilted forward in third person: Homelander lies into his flight
 * like Superman, A-Train leans into his sprint, and everybody leans into a heavy strike.
 */
public final class BodyLean {
	private static final class State {
		float lean;
		long nanos;
	}

	private static final Map<Integer, State> STATES = new HashMap<>();

	private BodyLean() {
	}

	/** Lean in degrees for this frame (smoothed), forward is positive. */
	public static float update(Entity e, float swingProgress) {
		State st = STATES.computeIfAbsent(e.getId(), id -> new State());
		long now = System.nanoTime();
		float dt = st.nanos == 0 ? 1f : Math.min(0.25f, (now - st.nanos) / 1.0e9f);
		st.nanos = now;
		float target = powerLean(e);
		// strikes come on top and react instantly
		float strike = CombatAnim.lean(e, swingProgress);
		st.lean += (target - st.lean) * (1f - (float) Math.exp(-dt * 5.0));
		return st.lean + strike;
	}

	/** The smoothed lean without the strike part (for the head, so he keeps looking where he goes). */
	public static float current(Entity e) {
		State st = STATES.get(e.getId());
		return st == null ? 0f : st.lean;
	}

	private static float powerLean(Entity e) {
		Power power = PowerAttachments.powerOf(e);
		ActiveState active = PowerAttachments.active(e);
		Vec3 vel = e.position().subtract(e.xo, e.yo, e.zo);
		boolean superman = (power == Power.HOMELANDER || power == Power.STORMFRONT) && active.has(ActiveState.FLYING)
				|| power == Power.THE_DEEP && active.has(ActiveState.DASH);
		if (power == Power.STARLIGHT && active.has(ActiveState.FLYING)) {
			// she floats upright and only tips a little into the direction she glides
			double yaw = Math.toRadians(e.getYRot());
			double forward = -Math.sin(yaw) * vel.x + Math.cos(yaw) * vel.z;
			return Mth.clamp((float) (forward * 60), -12f, 32f);
		}
		if (superman) {
			double yaw = Math.toRadians(e.getYRot());
			double forward = -Math.sin(yaw) * vel.x + Math.cos(yaw) * vel.z;
			double speed = vel.length();
			float k = Mth.clamp((float) ((speed - 0.05) / 0.45), 0f, 1f);
			// body along the flight path: 0 = straight up, 90 = level, 160 = diving
			float tilt = (float) Math.toDegrees(Math.atan2(forward, vel.y));
			tilt = Mth.clamp(tilt, -25f, 160f);
			return k * tilt + (1 - k) * 10f;
		}
		if (power == Power.A_TRAIN && active.has(ActiveState.SPEED) && vel.horizontalDistanceSqr() > 0.09) {
			return 28f;
		}
		return 0f;
	}
}
