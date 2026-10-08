package de.theboys.net;

import de.theboys.TheBoys;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: tiny MiniMaus chips one pixel out of the block at pos, where she hit it (x/y/z) on face. */
public record PixelMinePayload(BlockPos pos, float x, float y, float z, int face) implements CustomPacketPayload {
	public static final Type<PixelMinePayload> TYPE = new Type<>(TheBoys.id("pixel_mine"));
	public static final StreamCodec<ByteBuf, PixelMinePayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, PixelMinePayload::pos,
			ByteBufCodecs.FLOAT, PixelMinePayload::x,
			ByteBufCodecs.FLOAT, PixelMinePayload::y,
			ByteBufCodecs.FLOAT, PixelMinePayload::z,
			ByteBufCodecs.VAR_INT, PixelMinePayload::face,
			PixelMinePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
