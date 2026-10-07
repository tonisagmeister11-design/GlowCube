package de.theboys.client;

import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Client-side movement for Butcher's Cancer Walk and Homelander smashing through walls. */
public final class SupeMovement {
	/** How high the tendrils carry Butcher above the ground. */
	public static final double WALK_HEIGHT = 4.5;
	private static Vec3 lastFlight = Vec3.ZERO;
	private static boolean wasJumpDown;

	private SupeMovement() {
	}

	public static void tick(LocalPlayer player) {
		Power power = PowerAttachments.powerOf(player);
		ActiveState active = PowerAttachments.active(player);
		if (power == Power.BUTCHER && active.has(ActiveState.CANCER_WALK)) {
			cancerWalk(player);
		}
		if (power == Power.HOMELANDER && player.getAbilities().flying) {
			// at full speed he does not stop for walls: the server clears the way, keep the momentum
			Vec3 v = player.getDeltaMovement();
			if (player.horizontalCollision && lastFlight.horizontalDistance() > 0.75) {
				player.setDeltaMovement(lastFlight);
			} else {
				lastFlight = v;
			}
		} else {
			lastFlight = Vec3.ZERO;
		}
	}

	private static void cancerWalk(LocalPlayer player) {
		Level level = player.level();
		double ground = groundBelow(level, player.position());
		double target = ground + WALK_HEIGHT;
		Vec3 v = player.getDeltaMovement();
		double vy = v.y;
		boolean jump = player.input.keyPresses.jump();
		boolean onTendrils = player.getY() - target < 0.35 && player.getY() - target > -1.5;
		if (jump && !wasJumpDown && onTendrils) {
			// the tendrils push off the ground
			vy = 1.15;
		} else if (player.getY() < target) {
			vy = Math.max(vy, Math.min(0.45, (target - player.getY()) * 0.35));
		} else if (vy <= 0) {
			// settle softly onto the tendrils instead of falling
			vy = Math.max(vy, (target - player.getY()) * 0.3);
		}
		wasJumpDown = jump;

		// walking: the tendrils do the work, so move like on the ground even though he is in the air
		Vec2 move = player.input.getMoveVector();
		double speed = player.isSprinting() ? 0.42 : 0.3;
		double yaw = Math.toRadians(player.getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		double tx = (fx * move.y + fz * move.x) * speed;
		double tz = (fz * move.y - fx * move.x) * speed;
		double nx = Mth.lerp(0.45, v.x, tx);
		double nz = Mth.lerp(0.45, v.z, tz);
		player.setDeltaMovement(nx, vy, nz);
		player.resetFallDistance();
	}

	/** Top of the first solid block below (searched up to 14 blocks down). */
	public static double groundBelow(Level level, Vec3 pos) {
		BlockPos.MutableBlockPos p = BlockPos.containing(pos.x, pos.y + 0.01, pos.z).mutable();
		for (int i = 0; i < 14; i++) {
			p.move(0, -1, 0);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				return p.getY() + 1;
			}
		}
		return pos.y - WALK_HEIGHT;
	}
}
