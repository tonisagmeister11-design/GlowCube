package de.theboys.net;

import de.theboys.TheBoys;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> owning client: cooldowns of the four abilities (remaining / total ticks, packed) and the
 * power's meter (Homelander heat, Soldier Boy nuke charge, Butcher rip progress) from 0 to 1000.
 */
public record StatusPayload(int cd0, int cd1, int cd2, int cd3, int meter) implements CustomPacketPayload {
	public static final Type<StatusPayload> TYPE = new Type<>(TheBoys.id("status"));
	public static final StreamCodec<ByteBuf, StatusPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StatusPayload::cd0,
			ByteBufCodecs.VAR_INT, StatusPayload::cd1,
			ByteBufCodecs.VAR_INT, StatusPayload::cd2,
			ByteBufCodecs.VAR_INT, StatusPayload::cd3,
			ByteBufCodecs.VAR_INT, StatusPayload::meter,
			StatusPayload::new);

	/** Packs remaining and total cooldown ticks into one int. */
	public static int pack(int remaining, int total) {
		return (Math.min(remaining, 0xFFFF) << 16) | Math.min(total, 0xFFFF);
	}

	public static int remaining(int packed) {
		return packed >>> 16;
	}

	public static int total(int packed) {
		return packed & 0xFFFF;
	}

	public int cooldown(int slot) {
		return switch (slot) {
			case 0 -> cd0;
			case 1 -> cd1;
			case 2 -> cd2;
			default -> cd3;
		};
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
