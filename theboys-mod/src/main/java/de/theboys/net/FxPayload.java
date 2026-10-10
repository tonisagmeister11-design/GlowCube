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
	/** MiniMaus bites at x/y/z (entityId = MiniMaus, x2 = id of the bitten entity). */
	public static final int BITE = 7;
	/** MiniMaus' rat flood from x/y/z towards yaw x2, lasting y2 ticks. */
	public static final int RATS = 8;
	/** Stormfront's lightning: a jagged bolt from x/y/z to x2/y2/z2. */
	public static final int BOLT = 9;
	/** Starlight drinking light: a golden stream from the light source x/y/z to her (entityId). */
	public static final int LIGHT_STREAM = 10;
	/** Starlight's blinding flash at x/y/z, radius x2. */
	public static final int FLASH = 11;
	/** A blast sphere at x/y/z, radius x2, colour y2 (rgb as a float), z2 = 1 adds lightning arcs. */
	public static final int NOVA = 12;
	/** The Deep's sonar ping from x/y/z, radius x2. */
	public static final int SONAR = 13;
	/** The Deep's tidal wave from x/y/z towards yaw x2, length y2. */
	public static final int WAVE = 14;
	/** Black Noir's throwing knife from x/y/z to x2/y2/z2. */
	public static final int KNIFE = 15;
	/** Black Adam's golden lightning from x/y/z to x2/y2/z2. */
	public static final int GOD_BOLT = 16;
	/** A hunter's bullet tracer from x/y/z to x2/y2/z2. */
	public static final int TRACER = 17;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
