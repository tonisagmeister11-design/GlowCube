package de.theboys.net;

import de.theboys.TheBoys;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> clients: a one-off visual effect. */
public record FxPayload(int kind, int entityId, float x, float y, float z, float x2, float y2, float z2) implements CustomPacketPayload {
	public static final Type<FxPayload> TYPE = new Type<>(TheBoys.id("fx"));
	public static final StreamCodec<ByteBuf, FxPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FxPayload::kind,
			ByteBufCodecs.VAR_INT, FxPayload::entityId,
			ByteBufCodecs.FLOAT, FxPayload::x,
			ByteBufCodecs.FLOAT, FxPayload::y,
			ByteBufCodecs.FLOAT, FxPayload::z,
			ByteBufCodecs.FLOAT, FxPayload::x2,
			ByteBufCodecs.FLOAT, FxPayload::y2,
			ByteBufCodecs.FLOAT, FxPayload::z2,
			FxPayload::new);

	/** Tendril shoots from the supe (entityId) to x/y/z. */
	public static final int TENDRIL_STRIKE = 0;
	/** Soldier Boy's nuke at x/y/z with radius x2. */
	public static final int NUKE = 1;
	/** Shock ring (sonic boom / clap) at x/y/z, radius x2, direction (y2, z2 = yaw, pitch; NaN = flat ring). */
	public static final int SHOCKWAVE = 2;
	/** Time starts running backwards around x/y/z for x2 ticks, started by entityId. */
	public static final int REWIND_START = 3;
	/** Screen shake for players near x/y/z, strength x2. */
	public static final int SHAKE = 4;
	/** Blood burst at x/y/z with strength x2 (client adds extra gore on top of the server particles). */
	public static final int GORE = 5;
	/** The body of entityId torn in two at x/y/z: x2 = width, y2 = height, z2 = yaw of the tearing axis. */
	public static final int TORN = 6;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
