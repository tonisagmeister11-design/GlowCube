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
        public static final int CLAIM_CAR = 5;
        public static final int BRING_CAR = 6;
        public static final int START_STORY = 7;
        public static final int START_TEAM_JOB = 8;
        public static final int CONTINUE_JOB = 9;

        public static final Type<Phone> TYPE = new Type<>(GtaCity.id("phone"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Phone> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Phone::action, ByteBufCodecs.VAR_LONG, Phone::arg, Phone::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: open the job board of a station (0 = job centre, 1 = harbour office). */
    public record OpenJobs(int station) implements CustomPacketPayload {
        public static final Type<OpenJobs> TYPE = new Type<>(GtaCity.id("open_jobs"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenJobs> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenJobs::station, OpenJobs::new);

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

    /** Another player on the radar / map: crew mates are drawn green. */
    public record PlayerDot(String name, int x, int z, float yaw, boolean crew, int wanted, boolean inCar) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerDot> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PlayerDot::name, ByteBufCodecs.VAR_INT, PlayerDot::x, ByteBufCodecs.VAR_INT,
                PlayerDot::z, ByteBufCodecs.FLOAT, PlayerDot::yaw, ByteBufCodecs.BOOL, PlayerDot::crew,
                ByteBufCodecs.VAR_INT, PlayerDot::wanted, ByteBufCodecs.BOOL, PlayerDot::inCar, PlayerDot::new);
    }

    /** Server -> client: the other players in the world, and who invited this player into a crew (or ""). */
    public record Players(java.util.List<PlayerDot> dots, String invitedBy) implements CustomPacketPayload {
        public static final Type<Players> TYPE = new Type<>(GtaCity.id("players"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Players> CODEC = StreamCodec.composite(
                PlayerDot.CODEC.apply(ByteBufCodecs.list(256)), Players::dots, ByteBufCodecs.STRING_UTF8,
                Players::invitedBy, Players::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: a job is done - show the "Weitermachen" window (next level of the same job). */
    public record JobDone(int job, int level, long earned) implements CustomPacketPayload {
        public static final Type<JobDone> TYPE = new Type<>(GtaCity.id("job_done"));
        public static final StreamCodec<RegistryFriendlyByteBuf, JobDone> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, JobDone::job, ByteBufCodecs.VAR_INT, JobDone::level, ByteBufCodecs.VAR_LONG,
                JobDone::earned, JobDone::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
