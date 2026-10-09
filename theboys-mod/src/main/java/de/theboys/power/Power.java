package de.theboys.power;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** The supe powers a player can get from Compound V. */
public enum Power implements StringRepresentable {
	NONE("none", 0xFFFFFF),
	HOMELANDER("homelander", 0xC9A227),
	SOLDIER_BOY("soldier_boy", 0x6B8E23),
	A_TRAIN("a_train", 0x3A7BFF),
	BUTCHER("butcher", 0x8A0303),
	MINIMAUS("minimaus", 0xFF6FA5),
	STARLIGHT("starlight", 0xFFE9A8),
	THE_DEEP("the_deep", 0x2F9C9A),
	BLACK_NOIR("black_noir", 0x55575E),
	STORMFRONT("stormfront", 0x6FA8FF),
	BLACK_ADAM("black_adam", 0xF2C230);

	public static final Codec<Power> CODEC = StringRepresentable.fromEnum(Power::values);
	public static final StreamCodec<ByteBuf, Power> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> values()[i], Power::ordinal);

	/** Compound V gives one of these. */
	public static final Power[] COMPOUND_V_POOL = {A_TRAIN, BUTCHER, STARLIGHT, THE_DEEP, BLACK_NOIR};
	/** The rare V-One gives one of these. */
	public static final Power[] V_ONE_POOL = {SOLDIER_BOY, HOMELANDER, STORMFRONT};
	/** Mini V only ever makes MiniMaus. */
	public static final Power[] MINI_V_POOL = {MINIMAUS};
	/** The Shazam serum (the rarest of all) only ever makes Black Adam. */
	public static final Power[] SHAZAM_POOL = {BLACK_ADAM};

	private final String id;
	private final int color;

	Power(String id, int color) {
		this.id = id;
		this.color = color;
	}

	/** Supes that fly with Homelander-style flight. */
	public boolean flier() {
		return this == HOMELANDER || this == STORMFRONT || this == STARLIGHT || this == BLACK_ADAM;
	}

	public String id() {
		return id;
	}

	public int color() {
		return color;
	}

	public String translationKey() {
		return "power.theboys." + id;
	}

	/** Translation key of ability slot 0-3. */
	public String abilityKey(int slot) {
		return "ability.theboys." + id + "." + slot;
	}

	@Override
	public String getSerializedName() {
		return id;
	}
}
