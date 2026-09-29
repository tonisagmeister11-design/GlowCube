package de.gtacity.registry;

import com.mojang.serialization.Codec;
import de.gtacity.GtaCity;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModComponents {
    private ModComponents() {
    }

    /** Rounds left in the magazine of a gun. */
    public static final DataComponentType<Integer> AMMO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            GtaCity.id("ammo"), DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Dollar value of one cash item. */
    public static final DataComponentType<Integer> CASH_VALUE = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, GtaCity.id("cash_value"), DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * When a gun last fired and started reloading (game time), so every client can play the gun's shoot and reload
     * animations - for the player's own gun, other players' and the police's alike.
     */
    public record GunAnim(int shot, int reloadStart, int reloadTicks) {
        public static final GunAnim NONE = new GunAnim(-100000, -100000, 0);
        public static final com.mojang.serialization.Codec<GunAnim> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                        Codec.INT.fieldOf("shot").forGetter(GunAnim::shot),
                        Codec.INT.fieldOf("reload_start").forGetter(GunAnim::reloadStart),
                        Codec.INT.fieldOf("reload_ticks").forGetter(GunAnim::reloadTicks)).apply(i, GunAnim::new));
        public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, GunAnim> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.composite(ByteBufCodecs.VAR_INT, GunAnim::shot,
                        ByteBufCodecs.VAR_INT, GunAnim::reloadStart, ByteBufCodecs.VAR_INT, GunAnim::reloadTicks,
                        GunAnim::new);
    }

    public static final DataComponentType<GunAnim> GUN_ANIM = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            GtaCity.id("gun_anim"), DataComponentType.<GunAnim>builder()
                    .networkSynchronized(GunAnim.STREAM_CODEC)
                    .build());

    public static void init() {
    }
}
