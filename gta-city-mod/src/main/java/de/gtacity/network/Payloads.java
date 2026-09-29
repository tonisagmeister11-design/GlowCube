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

    /** Client -> server: F in a car leaves it, F next to a car gets in. */
    public record CarDoor() implements CustomPacketPayload {
        public static final Type<CarDoor> TYPE = new Type<>(GtaCity.id("car_door"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CarDoor> CODEC = StreamCodec.unit(new CarDoor());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the car the player drives crashed into something (the client drives it). */
    public record CarCrash(float damage) implements CustomPacketPayload {
        public static final Type<CarCrash> TYPE = new Type<>(GtaCity.id("car_crash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CarCrash> CODEC =
                StreamCodec.composite(ByteBufCodecs.FLOAT, CarCrash::damage, CarCrash::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: a button on the map / phone screen (see {@link ModNetworking#phone}). */
    public record Phone(int action, long arg) implements CustomPacketPayload {
        public static final int BUY_VILLA = 0;
        public static final int VILLA_TELEPORT = 1;
        public static final int CALL_CAR = 2;
        public static final int START_JOB = 3;
        public static final int CANCEL_JOB = 4;

        public static final Type<Phone> TYPE = new Type<>(GtaCity.id("phone"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Phone> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Phone::action, ByteBufCodecs.VAR_LONG, Phone::arg, Phone::new);

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
