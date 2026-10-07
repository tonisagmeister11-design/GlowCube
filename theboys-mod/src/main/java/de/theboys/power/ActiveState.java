package de.theboys.power;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a supe is doing right now (lasers on, running, holding someone ...).
 * Not saved, but synced to all clients so the 3D effects show up in every perspective.
 */
public record ActiveState(int flags, int targetId, int charge) {
	public static final int LASER = 1;
	public static final int SPEED = 1 << 1;
	public static final int CHEST_BEAM = 1 << 2;
	public static final int NUKE_CHARGE = 1 << 3;
	public static final int REWIND = 1 << 4;
	public static final int HOLD = 1 << 5;
	public static final int RIP = 1 << 6;
	public static final int XRAY = 1 << 7;
	public static final int FLYING = 1 << 8;
	public static final int GRAPPLE = 1 << 9;

	public static final ActiveState IDLE = new ActiveState(0, -1, 0);

	public static final StreamCodec<ByteBuf, ActiveState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ActiveState::flags,
			ByteBufCodecs.VAR_INT, ActiveState::targetId,
			ByteBufCodecs.VAR_INT, ActiveState::charge,
			ActiveState::new);

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public ActiveState with(int flag, boolean on) {
		return new ActiveState(on ? flags | flag : flags & ~flag, targetId, charge);
	}

	public ActiveState withTarget(int id) {
		return new ActiveState(flags, id, charge);
	}

	public ActiveState withCharge(int c) {
		return new ActiveState(flags, targetId, c);
	}
}
