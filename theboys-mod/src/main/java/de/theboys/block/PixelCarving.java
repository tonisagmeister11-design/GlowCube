package de.theboys.block;

import de.theboys.net.PixelMinePayload;
import de.theboys.power.PlayerSession;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import de.theboys.power.PowerManager;
import de.theboys.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Tiny MiniMaus chips single pixels out of blocks. */
public final class PixelCarving {
	private PixelCarving() {
	}

	public static void mine(ServerPlayer player, PixelMinePayload p) {
		if (PowerAttachments.powerOf(player) != Power.MINIMAUS) return;
		PlayerSession s = PowerManager.session(player);
		if (!s.small) return;
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now - s.lastPixel < 1) return;
		s.lastPixel = now;
		BlockPos pos = p.pos();
		if (!level.isLoaded(pos) || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 49) return;
		Vec3 hit = new Vec3(p.x(), p.y(), p.z());
		Direction face = Direction.from3DDataValue(p.face());
		carve(level, pos, hit, face, player.isCreative());
	}

	/** Removes the pixel at the hit point (just behind the face that was hit). Returns false if nothing was carved. */
	public static boolean carve(ServerLevel level, BlockPos pos, Vec3 hit, Direction face, boolean creative) {
		BlockState state = level.getBlockState(pos);
		CarvedBlockEntity be;
		if (state.is(ModBlocks.CARVED)) {
			if (!(level.getBlockEntity(pos) instanceof CarvedBlockEntity c)) return false;
			be = c;
		} else {
			// only plain full blocks: no chests, no fluids, nothing unbreakable
			if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
					|| state.getDestroySpeed(level, pos) < 0 || !state.isCollisionShapeFullBlock(level, pos)) {
				return false;
			}
			level.setBlock(pos, ModBlocks.CARVED.defaultBlockState(), 3);
			if (!(level.getBlockEntity(pos) instanceof CarvedBlockEntity c)) return false;
			be = c;
			be.init(state);
		}
		// the pixel just inside the face that was hit; if it is already gone, dig deeper
		Vec3 inside = hit.subtract(face.getStepX() * 0.01, face.getStepY() * 0.01, face.getStepZ() * 0.01);
		int x = Mth.clamp(Mth.floor((inside.x - pos.getX()) * 16), 0, 15);
		int y = Mth.clamp(Mth.floor((inside.y - pos.getY()) * 16), 0, 15);
		int z = Mth.clamp(Mth.floor((inside.z - pos.getZ()) * 16), 0, 15);
		boolean found = false;
		for (int k = 0; k < 16 && !found; k++) {
			if (be.filled(x, y, z)) {
				found = true;
			} else {
				x -= face.getStepX();
				y -= face.getStepY();
				z -= face.getStepZ();
			}
		}
		if (!found) return false;
		BlockState original = be.original();
		be.remove(x, y, z);
		level.playSound(null, pos, original.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.25f, 1.9f);
		if (be.isEmpty()) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			if (!creative) {
				Block.popResource(level, pos, new ItemStack(original.getBlock()));
			}
		}
		return true;
	}
}
