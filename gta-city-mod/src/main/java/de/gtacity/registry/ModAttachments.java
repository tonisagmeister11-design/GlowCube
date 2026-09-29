package de.gtacity.registry;

import com.mojang.serialization.Codec;
import de.gtacity.GtaCity;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** Per-player data that lives on the player entity: money (saved) and wanted stars (synced to the HUD). */
public final class ModAttachments {
    private ModAttachments() {
    }

    public static final AttachmentType<Long> MONEY = AttachmentRegistry.create(GtaCity.id("money"),
            builder -> builder
                    .initializer(() -> 500L)
                    .persistent(Codec.LONG)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

    public static final AttachmentType<Integer> WANTED = AttachmentRegistry.create(GtaCity.id("wanted"),
            builder -> builder
                    .initializer(() -> 0)
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    /** 1 while the wanted stars flash (police lost sight of the player). */
    public static final AttachmentType<Integer> WANTED_HIDDEN = AttachmentRegistry.create(GtaCity.id("wanted_hidden"),
            builder -> builder
                    .initializer(() -> 0)
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

    /** Set once the player received the starter kit. */
    public static final AttachmentType<Boolean> STARTER_KIT = AttachmentRegistry.create(GtaCity.id("starter_kit"),
            builder -> builder
                    .persistent(Codec.BOOL)
                    .copyOnDeath());

    /** Cars the player bought (CarVariant ordinals). They can be delivered anywhere from the phone / map. */
    public static final AttachmentType<List<Integer>> GARAGE = AttachmentRegistry.create(GtaCity.id("garage"),
            builder -> builder
                    .initializer(List::of)
                    .persistent(Codec.INT.listOf())
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

    /** Villas the player owns, as packed entrance positions (see {@link de.gtacity.gameplay.Villas}). */
    public static final AttachmentType<List<Long>> VILLAS = AttachmentRegistry.create(GtaCity.id("villas"),
            builder -> builder
                    .initializer(List::of)
                    .persistent(Codec.LONG.listOf())
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

    /** The current job goal, shown on the map with a GPS route. */
    public static final AttachmentType<Mission> MISSION = AttachmentRegistry.create(GtaCity.id("mission"),
            builder -> builder.syncWith(Mission.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    public record Mission(String label, int x, int z) {
        public static final StreamCodec<ByteBuf, Mission> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Mission::label, ByteBufCodecs.VAR_INT, Mission::x, ByteBufCodecs.VAR_INT,
                Mission::z, Mission::new);
    }

    public static void init() {
    }
}
