package de.theboys.client;

import java.util.ArrayList;
import java.util.List;

import de.theboys.net.FxPayload;
import de.theboys.net.StatusPayload;
import net.minecraft.world.phys.Vec3;

/** Everything the client remembers between ticks: cooldowns, running effects, screen effects. */
public final class ClientState {
	public static StatusPayload status = new StatusPayload(0, 0, 0, 0, 0);

	/** Time Jump overlay: ticks left / total. */
	public static int rewindTicks;
	public static int rewindTotal = 1;
	public static boolean rewindIsMine;

	public static int shakeTicks;
	public static float shakeStrength;

	public static int bloodTicks;

	/** Starlight's flash burnt into the eyes: ticks left and how bright it was. */
	public static int flashTicks;
	public static float flashStrength;

	/** Short flash in the serum's colour after an injection. */
	public static int injectFlash;
	public static int injectColor;
	public static boolean wasInjecting;
	public static int lastInjectRemaining;
	public static int lastInjectColor;

	/** Smoothed speed of the local player in km/h. */
	public static float speedKmh;
	public static Vec3 lastPos;

	/** One-off effects being drawn in the world. */
	public static final List<Fx> EFFECTS = new ArrayList<>();
	/** Fixed third-person camera {x, y, z, yaw, pitch} for screenshots, or null. */
	public static volatile double[] cameraOverride;
	/** Hides the ability panel (for recording videos). */
	public static volatile boolean hideHud;
	/** MiniMaus player id -> game time of her last bite (for the lunge animation). */
	public static final java.util.Map<Integer, Long> BITES = new java.util.HashMap<>();

	public static final class Fx {
		public final FxPayload data;
		public final int duration;
		public int age;

		public Fx(FxPayload data, int duration) {
			this.data = data;
			this.duration = duration;
		}
	}

	private ClientState() {
	}

	public static void reset() {
		status = new StatusPayload(0, 0, 0, 0, 0);
		rewindTicks = 0;
		shakeTicks = 0;
		bloodTicks = 0;
		flashTicks = 0;
		flashStrength = 0;
		EFFECTS.clear();
		lastPos = null;
		speedKmh = 0;
	}
}
