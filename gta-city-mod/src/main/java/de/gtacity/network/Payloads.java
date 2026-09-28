package de.gtacity.network;

import de.gtacity.GtaCity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** All custom packets of the mod. */
public final class Payloads {
    private Payloads() {
    }

    /** Client -> server: trigger pulled (held = automatic fire). */
    public record Fire(boolean held) implements CustomPacketPayload {
        public static final Type<Fire> TYPE = new Type<>(GtaCity.id("fire"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fire> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, Fire::held, Fire::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: reload key. */
    public record Reload() implements CustomPacketPayload {
        public static final Type<Reload> TYPE = new Type<>(GtaCity.id("reload"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reload> CODEC = StreamCodec.unit(new Reload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: horn. */
    public record Horn() implements CustomPacketPayload {
        public static final Type<Horn> TYPE = new Type<>(GtaCity.id("horn"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Horn> CODEC = StreamCodec.unit(new Horn());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: buy offer number {@code index} in shop {@code shop}. */
    public record Buy(int shop, int index) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = new Type<>(GtaCity.id("buy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Buy> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Buy::shop, ByteBufCodecs.VAR_INT, Buy::index, Buy::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: open the shop screen. */
    public record OpenShop(int shop) implements CustomPacketPayload {
        public static final Type<OpenShop> TYPE = new Type<>(GtaCity.id("open_shop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenShop> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenShop::shop, OpenShop::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: draw a bullet tracer and muzzle flash. */
    public record ShotFx(double fx, double fy, double fz, double tx, double ty, double tz)
            implements CustomPacketPayload {
        public static final Type<ShotFx> TYPE = new Type<>(GtaCity.id("shot_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShotFx> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, ShotFx::fx, ByteBufCodecs.DOUBLE, ShotFx::fy, ByteBufCodecs.DOUBLE, ShotFx::fz,
                ByteBufCodecs.DOUBLE, ShotFx::tx, ByteBufCodecs.DOUBLE, ShotFx::ty, ByteBufCodecs.DOUBLE, ShotFx::tz,
                ShotFx::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: kick the camera up a little. */
    public record Recoil(float amount) implements CustomPacketPayload {
        public static final Type<Recoil> TYPE = new Type<>(GtaCity.id("recoil"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Recoil> CODEC =
                StreamCodec.composite(ByteBufCodecs.FLOAT, Recoil::amount, Recoil::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
