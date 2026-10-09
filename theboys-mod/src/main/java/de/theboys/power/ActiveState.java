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
	public static final int FRENZY = 1 << 10;
	public static final int CANCER_WALK = 1 << 11;
	public static final int SMALL = 1 << 12;
	public static final int BITE = 1 << 13;
	public static final int MOON = 1 << 14;
	public static final int SMASH = 1 << 15;
	/** Starlight's light blast / Stormfront's lightning stream. */
	public static final int HAND_BEAM = 1 << 16;
	/** Starlight is charged up and glowing. */
	public static final int CHARGED = 1 << 17;
	/** Black Noir is hidden in the shadows. */
	public static final int SHADOW = 1 << 18;
	/** The Deep's sonar is pinging. */
	public static final int SONAR = 1 << 19;
	/** Black Noir has his katana drawn. */
	public static final int KATANA = 1 << 20;
	/** The Deep shoots forward like a torpedo. */
	public static final int DASH = 1 << 21;
	/** Black Adam pours lightning into the one he holds. */
	public static final int ZAP = 1 << 22;
	/** Black Adam flies through blocks. */
	public static final int PHASE = 1 << 23;

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
