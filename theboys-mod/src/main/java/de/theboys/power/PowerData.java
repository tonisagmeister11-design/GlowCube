package de.theboys.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Persistent per-player power. Synced to every client so suits and effects render for everyone. */
public record PowerData(Power power, boolean suit) {
	public static final PowerData NONE = new PowerData(Power.NONE, true);

	public static final Codec<PowerData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Power.CODEC.fieldOf("power").forGetter(PowerData::power),
			Codec.BOOL.optionalFieldOf("suit", true).forGetter(PowerData::suit)
	).apply(i, PowerData::new));

	public static final StreamCodec<ByteBuf, PowerData> STREAM_CODEC = StreamCodec.composite(
			Power.STREAM_CODEC, PowerData::power,
			ByteBufCodecs.BOOL, PowerData::suit,
			PowerData::new);

	public boolean has(Power p) {
		return power == p;
	}

	public PowerData withPower(Power p) {
		return new PowerData(p, suit);
	}

	public PowerData withSuit(boolean s) {
		return new PowerData(power, s);
	}
}
