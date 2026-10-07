package de.theboys.net;

import de.theboys.TheBoys;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: an ability key was pressed or released. Slot 0-3 are abilities, 4 toggles the suit. */
public record AbilityKeyPayload(int slot, boolean pressed) implements CustomPacketPayload {
	public static final Type<AbilityKeyPayload> TYPE = new Type<>(TheBoys.id("ability_key"));
	public static final StreamCodec<ByteBuf, AbilityKeyPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AbilityKeyPayload::slot,
			ByteBufCodecs.BOOL, AbilityKeyPayload::pressed,
			AbilityKeyPayload::new);

	public static final int SUIT = 4;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
