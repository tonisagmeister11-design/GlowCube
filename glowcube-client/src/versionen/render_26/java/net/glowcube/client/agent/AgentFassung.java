package net.glowcube.client.agent;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ChunkPos;

/**
 * Fassung fuer <b>26.x</b>: die wenigen Stellen, an denen sich der Agent
 * zwischen 1.21.11 und 26.x anders schreibt. Der uebrige Agent-Code liegt
 * einmal in src/main und nutzt nur diese Klasse.
 */
final class AgentFassung {
    private AgentFassung() {
    }

    /** Ab 26.x fuehrt EntityType kein MANNEQUIN-Feld mehr - ueber das Register. */
    @SuppressWarnings("unchecked")
    static Mannequin mannequin(ServerLevel welt) {
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath().equals("mannequin")) {
                Object neu = ((EntityType<Mannequin>) t).create(welt, EntitySpawnReason.COMMAND);
                return neu instanceof Mannequin m ? m : null;
            }
        }
        return null;
    }

    static void unverwundbar(Entity wer) {
        wer.setPermanentlyInvulnerable(true);
    }

    /** Ab 26.x nimmt swing die Schwung-Animation mit; true zeigt ihn allen. */
    static void schwingen(LivingEntity wer) {
        wer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
    }

    static long chunk(BlockPos pos, int dx, int dz) {
        ChunkPos c = ChunkPos.containing(pos);
        return ChunkPos.pack(c.x() + dx, c.z() + dz);
    }

    static void rueckstoss(LivingEntity ziel, LivingEntity von, ServerLevel welt, double dx, double dz) {
        ziel.knockback(0.5, dx, dz, welt.damageSources().mobAttack(von), 1.0f);
    }

    static void kanaeleRegistrieren() {
        PayloadTypeRegistry.serverboundPlay().register(AgentPaket.TYP, AgentPaket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AgentPaket.TYP, AgentPaket.CODEC);
    }
}
