package de.theboys.time;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A-Train's Time Jump. While an A-Train is around, the last {@link #HISTORY} ticks of every entity
 * near him (position, rotation, health) and every block change are recorded. During a Time Jump this
 * history is played back in reverse at double speed: people walk backwards, dropped items fly back
 * into the hand that threw them, broken blocks reappear. Only the A-Train himself keeps moving forward.
 */
public final class TimeRewind {
	public static final int HISTORY = 220;
	private static final double RECORD_RADIUS = 96;
	private static final int SPEED = 2;

	private record Snapshot(long tick, double x, double y, double z, float yRot, float xRot, float headRot, float health) {
	}

	private static final class Track {
		final ArrayDeque<Snapshot> frames = new ArrayDeque<>();
		long firstSeen;
		UUID thrower;
	}

	private record BlockChange(long tick, BlockPos pos, BlockState before) {
	}

	private static final class LevelHistory {
		final Map<Integer, Track> tracks = new HashMap<>();
		final ArrayDeque<BlockChange> blocks = new ArrayDeque<>();
		Rewind active;
	}

	private static final class Rewind {
		UUID runner;
		long startTick;
		long cursor;
		int ticksLeft;
	}

	private static final Map<ServerLevel, LevelHistory> LEVELS = new HashMap<>();
	private static boolean restoring;

	private TimeRewind() {
	}

	/** True while block changes in this level are being recorded (an A-Train is around and no rewind runs). */
	public static boolean isRecording(ServerLevel level) {
		if (restoring) return false;
		LevelHistory h = LEVELS.get(level);
		return h != null && h.active == null;
	}

	public static boolean isRewinding(ServerLevel level) {
		LevelHistory h = LEVELS.get(level);
		return h != null && h.active != null;
	}

	/** Called from the Level#setBlock mixin before a block changes. */
	public static void onBlockChange(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
		if (restoring) return;
		LevelHistory h = LEVELS.get(level);
		if (h == null || h.active != null) return;
		if (before == after || before.hasBlockEntity() || after.hasBlockEntity()) return;
		if (h.blocks.size() > 6000) h.blocks.pollFirst();
		h.blocks.addLast(new BlockChange(level.getGameTime(), pos.immutable(), before));
	}

	public static boolean start(ServerPlayer runner, int duration) {
		ServerLevel level = runner.level();
		LevelHistory h = LEVELS.get(level);
		if (h == null || h.active != null) return false;
		Rewind r = new Rewind();
		r.runner = runner.getUUID();
		r.startTick = level.getGameTime();
		r.cursor = r.startTick;
		r.ticksLeft = duration;
		h.active = r;
		return true;
	}

	public static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			List<ServerPlayer> runners = new ArrayList<>();
			for (ServerPlayer p : level.players()) {
				if (PowerAttachments.powerOf(p) == Power.A_TRAIN) runners.add(p);
			}
			LevelHistory h = LEVELS.get(level);
			if (runners.isEmpty()) {
				if (h != null && h.active == null) LEVELS.remove(level);
				if (h == null || h.active == null) continue;
			}
			if (h == null) {
				h = new LevelHistory();
				LEVELS.put(level, h);
			}
			if (h.active != null) {
				rewindStep(level, h);
			} else {
				record(level, h, runners);
			}
		}
	}

	private static void record(ServerLevel level, LevelHistory h, List<ServerPlayer> runners) {
		long now = level.getGameTime();
		for (Entity e : level.getAllEntities()) {
			if (e instanceof ServerPlayer p && PowerAttachments.powerOf(p) == Power.A_TRAIN) continue;
			if (e.isRemoved() || !near(e, runners)) continue;
			Track t = h.tracks.computeIfAbsent(e.getId(), id -> {
				Track nt = new Track();
				nt.firstSeen = now;
				if (e instanceof ItemEntity item && item.getOwner() instanceof Player thrower) {
					nt.thrower = thrower.getUUID();
				}
				return nt;
			});
			float health = e instanceof LivingEntity l ? l.getHealth() : 0;
			float head = e instanceof LivingEntity l ? l.getYHeadRot() : e.getYRot();
			t.frames.addLast(new Snapshot(now, e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), head, health));
			while (t.frames.size() > HISTORY) t.frames.pollFirst();
		}
		// forget entities that are gone or were not seen recently
		h.tracks.entrySet().removeIf(en -> {
			Entity e = level.getEntity(en.getKey());
			Snapshot last = en.getValue().frames.peekLast();
			return e == null || e.isRemoved() || last == null || now - last.tick() > 40;
		});
		while (!h.blocks.isEmpty() && now - h.blocks.peekFirst().tick() > HISTORY) {
			h.blocks.pollFirst();
		}
	}

	private static boolean near(Entity e, List<ServerPlayer> runners) {
		for (ServerPlayer r : runners) {
			if (r.distanceToSqr(e) < RECORD_RADIUS * RECORD_RADIUS) return true;
		}
		return false;
	}

	private static void rewindStep(ServerLevel level, LevelHistory h) {
		Rewind r = h.active;
		r.cursor -= SPEED;
		r.ticksLeft--;

		// blocks: undo every change newer than the cursor, newest first
		restoring = true;
		try {
			while (!h.blocks.isEmpty() && h.blocks.peekLast().tick() > r.cursor) {
				BlockChange c = h.blocks.pollLast();
				level.setBlock(c.pos(), c.before(), 3, 512);
			}
		} finally {
			restoring = false;
		}

		Iterator<Map.Entry<Integer, Track>> it = h.tracks.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Track> en = it.next();
			Entity e = level.getEntity(en.getKey());
			Track t = en.getValue();
			if (e == null || e.isRemoved()) {
				it.remove();
				continue;
			}
			if (e.getUUID().equals(r.runner)) continue;
			Snapshot target = null;
			while (!t.frames.isEmpty() && t.frames.peekLast().tick() > r.cursor) {
				target = t.frames.pollLast();
			}
			if (target == null) target = t.frames.peekLast();
			if (target != null) {
				apply(level, e, target);
			}
			// rewound past the moment it appeared: things that were dropped or shot go back where they came from
			if (t.firstSeen > r.cursor && t.frames.isEmpty()) {
				if (e instanceof ItemEntity item) {
					returnItem(level, item, t.thrower);
				} else if (e instanceof Projectile) {
					e.discard();
				}
				it.remove();
			}
		}

		if (r.ticksLeft <= 0 || r.startTick - r.cursor >= HISTORY) {
			h.active = null;
			h.tracks.clear();
			h.blocks.clear();
		}
	}

	private static void apply(ServerLevel level, Entity e, Snapshot s) {
		e.setDeltaMovement(Vec3.ZERO);
		e.resetFallDistance();
		if (e instanceof ServerPlayer p) {
			p.teleportTo(level, s.x(), s.y(), s.z(), java.util.Set.of(), s.yRot(), s.xRot(), false);
		} else {
			e.snapTo(s.x(), s.y(), s.z(), s.yRot(), s.xRot());
			e.setYHeadRot(s.headRot());
		}
		if (e instanceof LivingEntity l && l.isAlive() && s.health() > 0) {
			l.setHealth(Math.min(l.getMaxHealth(), s.health()));
		}
	}

	private static void returnItem(ServerLevel level, ItemEntity item, UUID thrower) {
		if (thrower == null) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(thrower);
		if (owner == null || owner.level() != level) {
			return;
		}
		ItemStack stack = item.getItem().copy();
		if (owner.getInventory().add(stack) && stack.isEmpty()) {
			item.discard();
		} else {
			item.setItem(stack);
		}
	}
}
