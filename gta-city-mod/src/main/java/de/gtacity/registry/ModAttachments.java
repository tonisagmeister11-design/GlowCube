package de.gtacity.registry;

import com.mojang.serialization.Codec;
import de.gtacity.GtaCity;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

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

    public static void init() {
    }
}
