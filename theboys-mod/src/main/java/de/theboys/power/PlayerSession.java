package de.theboys.power;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Transient server-side state of one supe (cooldowns, held keys, timers). */
public final class PlayerSession {
	public final int[] cooldown = new int[4];
	public final int[] cooldownTotal = new int[4];
	public final boolean[] keyDown = new boolean[4];

	// Homelander
	public int heat;
	public BlockPos burnPos;
	public int burnProgress;
	public int xrayTicks;
	public int boomCooldown;
	public boolean flying;

	// Soldier Boy
	public int beamTicks;
	public int nukeCharge;

	// A-Train
	public boolean speed;
	public int rewindTicks;
	public int dashTicks;
	public int barrageTicks;
	public int barrageTarget = -1;
	public final Map<Integer, Integer> runThroughHit = new HashMap<>();

	// Butcher
	public int heldId = -1;
	public int holdTicks;
	public int ripTicks;
	public int thrownId = -1;
	public int thrownTicks;
	public int grappleTicks;
	public Vec3 grappleTarget;
	public int strikeDelay = -1;
	public boolean cancerWalk;
	public int frenzyCooldown;
	/** who hurt him recently (entity id -> game time), so Super Cancer goes for every attacker, players too */
	public final java.util.Map<Integer, Long> attackers = new java.util.HashMap<>();
	public int frenzyTicks;
	public final java.util.Map<Integer, Integer> frenzyVictims = new java.util.HashMap<>();
	public final java.util.Map<Integer, net.minecraft.world.phys.Vec3> frenzySpots = new java.util.HashMap<>();
	public Vec3 strikePoint;

	// MiniMaus
	public boolean small;
	public boolean biteArmed;
	public int biteTicks;
	public boolean moonArmed;
	public int moonTarget = -1;
	public int moonTicks;
	public int moonStrikeTicks;
	public Vec3 moonSpot;
	public int smashTarget = -1;
	public int smashTicks;
	public long lastPixel;
	public double speedFactor = 2.0;
	public double scale = 1.0;
	public int giantTicks;
	public int giantCool;
	public int ratTicks;
	public int ratCool;
	public float ratYaw;
	public Vec3 ratOrigin = Vec3.ZERO;
	public final java.util.Set<Integer> ratHit = new java.util.HashSet<>();
	public int squeakCool;

	// Starlight
	public int charge = 400;
	public int novaCool;

	// Stormfront
	public int strikeBolts;
	public int strikeTarget = -1;
	public Vec3 strikeAt;

	// Black Noir
	public int comboTicks;
	public int comboTarget = -1;
	public int shadowTicks;
	public int ambushTicks;

	// The Deep
	public final java.util.Map<Integer, Integer> dolphins = new java.util.HashMap<>();
	public int deepTarget = -1;
	public int dolphinsToSpawn;
	public int dolphinDelay;
	public int moisture = 1000;
	public int waveTicks;
	public float waveYaw;
	public Vec3 waveOrigin = Vec3.ZERO;
	public boolean waveStrong;
	public final java.util.Set<Integer> waveHit = new java.util.HashSet<>();
	public int sonarTicks;
	public int deepDashTicks;
	public Vec3 deepDash = Vec3.ZERO;
	public final java.util.Set<Integer> dashHit = new java.util.HashSet<>();

	// shared
	public Vec3 lastPos;
	public Vec3 motion = Vec3.ZERO;
	public int statusTimer;

	public boolean ready(int slot) {
		return cooldown[slot] <= 0;
	}

	public void cool(int slot, int ticks) {
		cooldown[slot] = ticks;
		cooldownTotal[slot] = ticks;
	}

	public void tickCooldowns() {
		for (int i = 0; i < 4; i++) {
			if (cooldown[i] > 0) cooldown[i]--;
		}
		if (boomCooldown > 0) boomCooldown--;
	}

	public double horizontalSpeed() {
		return Math.sqrt(motion.x * motion.x + motion.z * motion.z);
	}
}
