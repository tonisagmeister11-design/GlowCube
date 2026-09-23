package net.glowcube.client.agent;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orbital Strike: ein Hebel auf einem Leitstein ist der Ausloeser (der
 * Builder setzt einen oben auf die Orbital Strike Cannon, man kann aber auch
 * selbst einen bauen). Wird er eingeschaltet, fallen am markierten Ziel
 * ({@code /glowcube ziel}) Ringe aus gezuendetem TNT vom Himmel - erst die
 * Mitte, dann nach aussen immer groessere Ringe.
 *
 * <p>Laeuft auf dem Server der eigenen Welt.
 */
final class OrbitalStrike {
    private static final Map<UUID, BlockPos> ZIELE = new HashMap<>();
    private static final List<Einschlag> LAUFEND = new ArrayList<>();

    /** Ein laufender Einschlag: Ring fuer Ring ueber mehrere Ticks. */
    private static final class Einschlag {
        final ServerLevel welt;
        final BlockPos ziel;
        final ChunkHalter chunks = new ChunkHalter();
        int tick;

        Einschlag(ServerLevel welt, BlockPos ziel) {
            this.welt = welt;
            this.ziel = ziel;
            chunks.halten(welt, ziel, 1);
        }
    }

    private OrbitalStrike() {
    }

    static void registrieren() {
        UseBlockCallback.EVENT.register((spieler, welt, hand, treffer) -> {
            if (welt.isClientSide() || !(spieler instanceof ServerPlayer sp)) {
                return InteractionResult.PASS;
            }
            BlockPos pos = treffer.getBlockPos();
            BlockState zustand = welt.getBlockState(pos);
            if (zustand.is(Blocks.LEVER) && welt.getBlockState(pos.below()).is(Blocks.LODESTONE)
                    && !zustand.getValue(LeverBlock.POWERED)) {
                // Der Hebel geht gleich an - dann feuern.
                feuern(sp);
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(OrbitalStrike::tick);
    }

    static void zielSetzen(UUID spieler, BlockPos ziel) {
        ZIELE.put(spieler, ziel);
    }

    private static void feuern(ServerPlayer spieler) {
        BlockPos ziel = ZIELE.get(spieler.getUUID());
        if (ziel == null) {
            AgentWelt.melden(spieler, ChatFormatting.YELLOW,
                    "Orbital Strike: erst ein Ziel markieren - draufschauen und /glowcube ziel eingeben.");
            return;
        }
        ServerLevel welt = (ServerLevel) spieler.level();
        LAUFEND.add(new Einschlag(welt, ziel));
        AgentWelt.melden(spieler, ChatFormatting.RED, "Orbital Strike auf " + ziel.getX() + " " + ziel.getY() + " "
                + ziel.getZ() + " - Einschlag in wenigen Sekunden!");
        welt.playSound(null, spieler.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 0.6f);
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Einschlag> it = LAUFEND.iterator(); it.hasNext(); ) {
            Einschlag e = it.next();
            e.tick++;
            BlockPos z = e.ziel;
            if (e.tick == 1) {
                // Der Leitstrahl: ein Lichtband vom Himmel auf das Ziel.
                for (int y = 0; y < 60; y += 2) {
                    e.welt.sendParticles(ParticleTypes.END_ROD, z.getX() + 0.5, z.getY() + 1 + y, z.getZ() + 0.5,
                            3, 0.1, 0.5, 0.1, 0.01);
                }
                e.welt.playSound(null, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 4f, 0.5f);
            }
            // Ring 0 (Mitte) bis Ring 4 (Radius 16), alle 8 Ticks ein Ring.
            if (e.tick % 8 == 0 && e.tick <= 40) {
                int ring = e.tick / 8 - 1;
                int radius = ring * 4;
                int anzahl = ring == 0 ? 3 : (int) Math.round(2 * Math.PI * radius / 2.5);
                for (int i = 0; i < anzahl; i++) {
                    double winkel = 2 * Math.PI * i / anzahl + ring * 0.3;
                    double x = z.getX() + 0.5 + Math.cos(winkel) * radius;
                    double zz = z.getZ() + 0.5 + Math.sin(winkel) * radius;
                    PrimedTnt tnt = new PrimedTnt(e.welt, x, z.getY() + 40, zz, null);
                    tnt.setFuse(58 + e.welt.random.nextInt(6));
                    e.welt.addFreshEntity(tnt);
                }
            }
            if (e.tick > 200) {
                e.chunks.freigeben();
                it.remove();
            }
        }
    }
}
