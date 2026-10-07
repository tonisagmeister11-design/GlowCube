package de.theboys.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fight animations for every player: each attack swing gets its own move out of a combo (slashes,
 * backhands, overhead cleaves, thrusts, punches) depending on the weapon, plus a slash trail.
 * Swings that break blocks stay the plain vanilla swing.
 */
public final class CombatAnim {
	public static final int FIST = 0;
	public static final int SWORD = 1;
	public static final int AXE = 2;
	public static final int THRUST = 3;

	/** How long after the last swing the next one continues the combo. */
	private static final int COMBO_WINDOW = 22;

	public static final class Swing {
		public int kind;
		public int combo;
		public long start;
		public boolean active;
		public boolean leftArm;
		boolean wasSwinging;
		float lastProgress;
	}

	/** A slash trail in the world: the arc the weapon cut through. */
	public record Slash(int playerId, long start, int kind, int combo, boolean leftArm, Vec3 origin, float yaw, float pitch) {
	}

	private static final Map<Integer, Swing> SWINGS = new HashMap<>();
	public static final List<Slash> SLASHES = new ArrayList<>();

	private CombatAnim() {
	}

	public static Swing get(int id) {
		Swing s = SWINGS.get(id);
		return s != null && s.active ? s : null;
	}

	public static void tick(ClientLevel level) {
		long now = level.getGameTime();
		for (AbstractClientPlayer p : level.players()) {
			Swing s = SWINGS.computeIfAbsent(p.getId(), id -> new Swing());
			boolean swinging = p.isSwinging();
			float progress = p.getSwingAnimation(1f);
			boolean started = swinging && (!s.wasSwinging || progress < s.lastProgress - 0.05f);
			s.wasSwinging = swinging;
			s.lastProgress = progress;
			if (!swinging && now - s.start > 12) s.active = false;
			if (!started) continue;
			if (isMining(p)) {
				s.active = false;
				continue;
			}
			ItemStack held = p.getMainHandItem();
			int kind = kindOf(held);
			s.combo = now - s.start <= COMBO_WINDOW && s.active ? s.combo + 1 : 0;
			s.kind = kind;
			s.start = now;
			s.active = true;
			s.leftArm = p.getMainArm() == HumanoidArm.LEFT;
			if (kind != FIST) {
				SLASHES.add(new Slash(p.getId(), now, kind, s.combo, s.leftArm, p.position(), p.getYRot(), p.getXRot()));
			}
		}
		SWINGS.keySet().removeIf(id -> level.getEntity(id) == null);
		Iterator<Slash> it = SLASHES.iterator();
		while (it.hasNext()) {
			if (now - it.next().start() > 8) it.remove();
		}
	}

	public static int kindOf(ItemStack held) {
		if (held.is(ItemTags.SWORDS)) return SWORD;
		if (held.is(ItemTags.AXES) || held.is(Items.MACE)) return AXE;
		if (held.is(Items.TRIDENT)) return THRUST;
		if (held.isEmpty()) return FIST;
		// anything else held like a club
		return SWORD;
	}

	/** True when the swing hits a block (mining) rather than a mob or thin air. */
	private static boolean isMining(AbstractClientPlayer p) {
		double reach = 4.5;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1f);
		Vec3 end = eye.add(look.scale(reach));
		HitResult block = p.pick(reach, 1f, false);
		double blockDist = block.getType() == HitResult.Type.BLOCK ? block.getLocation().distanceTo(eye) : Double.MAX_VALUE;
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1),
				e -> e != p && e.isPickable() && !e.isSpectator(), reach * reach);
		if (entity != null && entity.getLocation().distanceTo(eye) < blockDist) return false;
		return block instanceof BlockHitResult && block.getType() == HitResult.Type.BLOCK;
	}

	static float smooth(float x) {
		x = Math.max(0f, Math.min(1f, x));
		return x * x * (3 - 2 * x);
	}

	/** Forward lean (degrees) of the whole body while striking. */
	public static float lean(Entity e, float attackTime) {
		Swing s = get(e.getId());
		if (s == null || attackTime <= 0f) return 0f;
		float strike = strikeCurve(attackTime);
		return switch (s.kind) {
			case AXE -> s.combo % 2 == 0 ? -8f + 26f * strike : 14f * strike;
			case THRUST -> 18f * strike;
			case SWORD -> s.combo % 4 == 2 ? -6f + 22f * strike : s.combo % 4 == 3 ? 20f * strike : 10f * strike;
			default -> 9f * strike;
		};
	}

	/** 0 during the wind-up, rising fast to 1 in the strike, easing back during recovery. */
	public static float strikeCurve(float p) {
		if (p < 0.22f) return 0f;
		if (p < 0.5f) return smooth((p - 0.22f) / 0.28f);
		return 1f - smooth((p - 0.5f) / 0.5f);
	}
}
