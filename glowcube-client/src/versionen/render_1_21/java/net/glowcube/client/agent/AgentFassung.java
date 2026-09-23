package net.glowcube.client.agent;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.ChunkPos;

/**
 * Fassung fuer <b>1.21.x</b>: die wenigen Stellen, an denen sich der Agent
 * zwischen 1.21.11 und 26.x anders schreibt. Der uebrige Agent-Code liegt
 * einmal in src/main und nutzt nur diese Klasse.
 */
final class AgentFassung {
    private AgentFassung() {
    }

    static Mannequin mannequin(ServerLevel welt) {
        return EntityType.MANNEQUIN.create(welt, EntitySpawnReason.COMMAND);
    }

    static void unverwundbar(Entity wer) {
        wer.setInvulnerable(true);
    }

    static void schwingen(LivingEntity wer) {
        wer.swing(InteractionHand.MAIN_HAND, true);
    }

    /** Der Chunk (dx, dz) Chunks neben dem Chunk von pos, als long. */
    static long chunk(BlockPos pos, int dx, int dz) {
        ChunkPos c = new ChunkPos(pos);
        return ChunkPos.asLong(c.x + dx, c.z + dz);
    }

    static void rueckstoss(LivingEntity ziel, LivingEntity von, ServerLevel welt, double dx, double dz) {
        ziel.knockback(0.5, dx, dz);
    }

    static void kanaeleRegistrieren() {
        PayloadTypeRegistry.playC2S().register(AgentPaket.TYP, AgentPaket.CODEC);
        PayloadTypeRegistry.playS2C().register(AgentPaket.TYP, AgentPaket.CODEC);
    }
}
