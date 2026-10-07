package de.theboys.client;

import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side movement help for a running A-Train (player movement is simulated on the client):
 * he hops over walls and trees in one smooth arc instead of stopping, and he runs across water.
 */
public final class ATrainMovement {
	private static int hopCooldown;

	private ATrainMovement() {
	}

	public static void tick(LocalPlayer player) {
		if (hopCooldown > 0) hopCooldown--;
		if (PowerAttachments.powerOf(player) != Power.A_TRAIN || !PowerAttachments.active(player).has(ActiveState.SPEED)) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);

		// run across water
		if (player.isInWater() && horizontal > 0.4 && !player.isShiftKeyDown()) {
			player.setDeltaMovement(v.x, Math.max(v.y, 0.18), v.z);
			player.resetFallDistance();
		}

		// hop over obstacles exactly as high as needed
		if (player.horizontalCollision && hopCooldown == 0 && player.input.hasForwardImpulse()) {
			Vec3 look = player.getLookAngle();
			Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
			int height = obstacleHeight(player.level(), player.position(), dir);
			if (height >= 2) {
				double vy = Math.sqrt(2 * 0.08 * (height + 0.35)) + 0.12;
				player.setDeltaMovement(dir.x * 0.6, vy, dir.z * 0.6);
				hopCooldown = 6;
			}
		}
	}

	/** How many blocks up until there is a 2-high gap in front of the player (max 24). */
	private static int obstacleHeight(Level level, Vec3 feet, Vec3 dir) {
		BlockPos base = BlockPos.containing(feet.x + dir.x * 0.9, feet.y + 0.01, feet.z + dir.z * 0.9);
		for (int h = 0; h <= 24; h++) {
			BlockPos p = base.above(h);
			if (free(level, p) && free(level, p.above())) {
				return h;
			}
		}
		return 0;
	}

	private static boolean free(Level level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}
}
