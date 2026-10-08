package de.theboys.client;

import de.theboys.net.PixelMinePayload;
import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Tiny MiniMaus does not break whole blocks: every hit chips one pixel out. */
public final class PixelMining {
	private static long lastSent;

	private PixelMining() {
	}

	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && PowerAttachments.powerOf(mc.player) == Power.MINIMAUS
				&& PowerAttachments.active(mc.player).has(ActiveState.SMALL);
	}

	/** Called instead of the normal block breaking. Returns true when it took over. */
	public static boolean mine(BlockPos pos, Direction face) {
		if (!active()) return false;
		Minecraft mc = Minecraft.getInstance();
		long now = mc.level.getGameTime();
		if (now - lastSent < 1) return true;
		lastSent = now;
		Vec3 hit = mc.hitResult instanceof BlockHitResult b && b.getBlockPos().equals(pos) ? b.getLocation() : Vec3.atCenterOf(pos);
		ClientPlayNetworking.send(new PixelMinePayload(pos, (float) hit.x, (float) hit.y, (float) hit.z, face.get3DDataValue()));
		return true;
	}
}
