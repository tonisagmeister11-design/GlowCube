package net.glowcube.client.agent;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.glowcube.client.bauplan.Bauplaene;
import net.glowcube.client.bauplan.Kanone;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
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
 * Orbital Strike: gefeuert wird mit dem Hebel ganz oben auf der Orbital Strike
 * Cannon (Leitstein mit Hebel darauf - der Builder setzt ihn mit). Der Hebel
 * feuert nur, wenn rund um ihn wirklich die Kanone steht (Stichprobe gegen den
 * Bauplan, auch gedreht).
 *
 * <p>Dann laeuft die Kanone sichtbar ab: das Signal wandert vom Hebel durch
 * die Redstone-Leitungen, das TNT in den vier Ladearmen zuendet von aussen
 * nach innen, die Portale leuchten, im Kern knallt es und die Salve schiesst
 * senkrecht aus der Kanone in den Himmel. Nach der Flugzeit fallen am Ziel
 * ({@code /strike x y z}) Ringe aus TNT herab.
 *
 * <p>Laeuft auf dem Server der eigenen Welt.
 */
final class OrbitalStrike {
    private static final Map<UUID, BlockPos> ZIELE = new HashMap<>();
    private static final List<Einschlag> LAUFEND = new ArrayList<>();
    private static final ParticleOptions SIGNAL = new DustParticleOptions(0xFF2020, 1.4f);

    /** Takte der Vorfuehrung (Ticks ab dem Umlegen). */
    private static final int SIGNAL_ENDE = 30;
    private static final int LADEN_ENDE = 65;
    private static final int ZUENDUNG = 80;
    private static final int SALVE_ENDE = 100;

    private static Kanone kanonePlan;

    /** Ein laufender Strike: erst die Kanone, dann Ring fuer Ring am Ziel. */
    private static final class Einschlag {
        final ServerLevel welt;
        final ServerPlayer schuetze;
        final BlockPos ziel;
        final BlockPos hebel;
        final Kanone kanone;
        final int drehung;
        final int ankunft;
        final List<PrimedTnt> salve = new ArrayList<>();
        final ChunkHalter chunks = new ChunkHalter();
        int tick;

        Einschlag(ServerLevel welt, ServerPlayer schuetze, BlockPos ziel, BlockPos hebel, Kanone kanone,
                  int drehung) {
            this.welt = welt;
            this.schuetze = schuetze;
            this.ziel = ziel;
            this.hebel = hebel;
            this.kanone = kanone;
            this.drehung = drehung;
            double weg = Math.sqrt(ziel.distSqr(hebel));
            this.ankunft = SALVE_ENDE + 20 + (int) Math.min(100, weg / 20);
            chunks.halten(welt, ziel, 1);
        }

        /** Eine Stelle der Kanone (relativ zum Hebel) in der Welt. */
        double[] ort(int[] rel) {
            int[] w = Kanone.drehen(rel, drehung);
            return new double[] {hebel.getX() + w[0] + 0.5, hebel.getY() + w[1] + 0.5, hebel.getZ() + w[2] + 0.5};
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
                feuern(sp, pos);
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(OrbitalStrike::tick);
    }

    static void zielSetzen(UUID spieler, BlockPos ziel) {
        ZIELE.put(spieler, ziel);
    }

    private static Kanone plan() {
        if (kanonePlan == null) {
            kanonePlan = Kanone.aus(Bauplaene.laden(Kanone.NAME));
        }
        return kanonePlan;
    }

    private static void feuern(ServerPlayer spieler, BlockPos hebel) {
        ServerLevel welt = (ServerLevel) spieler.level();
        Kanone kanone = plan();
        if (kanone == null) {
            AgentWelt.melden(spieler, ChatFormatting.RED, "Orbital Strike: der Bauplan der Kanone fehlt.");
            return;
        }
        int drehung = kanone.erkennen(hebel.getX(), hebel.getY(), hebel.getZ(), (x, y, z) ->
                BuiltInRegistries.BLOCK.getKey(welt.getBlockState(new BlockPos(x, y, z)).getBlock()).toString());
        if (drehung < 0) {
            AgentWelt.melden(spieler, ChatFormatting.YELLOW, "Orbital Strike: hier steht keine Orbital Strike "
                    + "Cannon. Erst die Kanone bauen (Builder, Schematic Orbital-Strike-Cannon) - der Hebel sitzt "
                    + "ganz oben.");
            return;
        }
        BlockPos ziel = ZIELE.get(spieler.getUUID());
        if (ziel == null) {
            AgentWelt.melden(spieler, ChatFormatting.YELLOW,
                    "Orbital Strike: erst ein Ziel setzen - /strike x y z (Koordinaten mit /coordinates).");
            return;
        }
        LAUFEND.add(new Einschlag(welt, spieler, ziel, hebel, kanone, drehung));
        AgentWelt.melden(spieler, ChatFormatting.RED, "Orbital Strike auf " + ziel.getX() + " " + ziel.getY() + " "
                + ziel.getZ() + " - die Kanone laeuft an!");
        welt.playSound(null, hebel, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2f, 0.6f);
    }

    private static void anzeige(Einschlag e, String text) {
        e.schuetze.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal(text).withStyle(ChatFormatting.GOLD)));
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Einschlag> it = LAUFEND.iterator(); it.hasNext(); ) {
            Einschlag e = it.next();
            e.tick++;
            kanoneTick(e);
            if (e.tick >= e.ankunft) {
                einschlagTick(e, e.tick - e.ankunft + 1);
            }
            if (e.tick > e.ankunft + 200) {
                for (PrimedTnt tnt : e.salve) {
                    tnt.discard();
                }
                e.chunks.freigeben();
                it.remove();
            }
        }
    }

    /** Die Vorfuehrung an der Kanone selbst. */
    private static void kanoneTick(Einschlag e) {
        ServerLevel w = e.welt;
        Kanone k = e.kanone;
        int t = e.tick;
        // 1. Das Signal laeuft vom Hebel durch die Redstone-Leitungen.
        if (t <= SIGNAL_ENDE) {
            if (t == 1) {
                anzeige(e, "1/4  Signal laeuft durch die Schaltung ...");
            }
            int von = k.leitung.size() * (t - 1) / SIGNAL_ENDE;
            int bis = k.leitung.size() * t / SIGNAL_ENDE;
            for (int i = von; i < bis; i++) {
                double[] o = e.ort(k.leitung.get(i));
                w.sendParticles(SIGNAL, o[0], o[1] - 0.3, o[2], 2, 0.15, 0.05, 0.15, 0);
            }
            if (t % 5 == 1 && !k.leitung.isEmpty()) {
                double[] o = e.ort(k.leitung.get(Math.min(bis, k.leitung.size() - 1)));
                w.playSound(null, o[0], o[1], o[2], SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 1f, 1.2f);
            }
        }
        // 2. Das TNT in den vier Ladearmen zuendet, von aussen nach innen.
        if (t > SIGNAL_ENDE && t <= LADEN_ENDE) {
            if (t == SIGNAL_ENDE + 1) {
                anzeige(e, "2/4  Ladearme: TNT wird gezuendet ...");
            }
            int dauer = LADEN_ENDE - SIGNAL_ENDE;
            int von = k.ladung.size() * (t - SIGNAL_ENDE - 1) / dauer;
            int bis = k.ladung.size() * (t - SIGNAL_ENDE) / dauer;
            for (int i = von; i < bis; i++) {
                double[] o = e.ort(k.ladung.get(i));
                w.sendParticles(ParticleTypes.FLAME, o[0], o[1] + 0.4, o[2], 12, 0.3, 0.3, 0.3, 0.02);
                w.sendParticles(ParticleTypes.LARGE_SMOKE, o[0], o[1] + 0.6, o[2], 4, 0.2, 0.2, 0.2, 0.01);
                w.playSound(null, o[0], o[1], o[2], SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 0.7f, 1.3f);
            }
            if (t % 4 == 0) {
                double[] o = e.ort(k.kern);
                w.playSound(null, o[0], o[1], o[2], SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.5f, 0.8f);
            }
        }
        // 3. Zuendung: die Portale leuchten, im Kern baut sich Druck auf.
        if (t > LADEN_ENDE && t <= ZUENDUNG) {
            if (t == LADEN_ENDE + 1) {
                anzeige(e, "3/4  Zuendung!");
                double[] o = e.ort(k.kern);
                w.playSound(null, o[0], o[1], o[2], SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1.5f, 1.6f);
            }
            for (int[] p : k.portale) {
                double[] o = e.ort(p);
                w.sendParticles(ParticleTypes.REVERSE_PORTAL, o[0], o[1], o[2], 6, 0.3, 0.4, 0.3, 0.05);
            }
            double[] o = e.ort(k.kern);
            w.sendParticles(ParticleTypes.PORTAL, o[0], o[1], o[2], 30, 1.5, 0.5, 1.5, 0.8);
            w.sendParticles(ParticleTypes.FLAME, o[0], o[1], o[2], 10, 1.0, 0.3, 1.0, 0.05);
        }
        // 4. Abschuss: Knall im Kern, die Salve schiesst senkrecht aus der Kanone.
        if (t == ZUENDUNG + 1) {
            anzeige(e, "4/4  Abschuss!");
            double[] kern = e.ort(k.kern);
            w.sendParticles(ParticleTypes.EXPLOSION_EMITTER, kern[0], kern[1], kern[2], 1, 0, 0, 0, 0);
            w.playSound(null, kern[0], kern[1], kern[2], SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                    4f, 0.7f);
            // Der Schuss durch den Lauf: vom Kern bis ueber die Kanone.
            double[] mund = e.ort(k.muendung);
            for (double y = kern[1]; y <= mund[1]; y += 0.7) {
                w.sendParticles(ParticleTypes.FLAME, kern[0], y, kern[2], 3, 0.2, 0.2, 0.2, 0.02);
                w.sendParticles(ParticleTypes.CLOUD, kern[0], y, kern[2], 1, 0.3, 0.2, 0.3, 0.01);
            }
        }
        if (t > ZUENDUNG && t <= SALVE_ENDE && (t - ZUENDUNG) % 4 == 1) {
            double[] mund = e.ort(k.muendung);
            PrimedTnt tnt = new PrimedTnt(w, mund[0], mund[1], mund[2], null);
            tnt.setFuse(60);
            tnt.setNoGravity(true);
            tnt.setDeltaMovement(0, 2.5, 0);
            w.addFreshEntity(tnt);
            e.salve.add(tnt);
            w.sendParticles(ParticleTypes.EXPLOSION, mund[0], mund[1], mund[2], 2, 0.3, 0.3, 0.3, 0);
            w.sendParticles(ParticleTypes.FLAME, mund[0], mund[1], mund[2], 20, 0.2, 0.5, 0.2, 0.08);
            w.playSound(null, mund[0], mund[1], mund[2], SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS,
                    4f, 0.5f);
            w.playSound(null, mund[0], mund[1], mund[2], SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                    1.5f, 1.4f);
        }
        // Oben im Himmel verschwindet die Salve, bevor sie zuendet.
        for (Iterator<PrimedTnt> s = e.salve.iterator(); s.hasNext(); ) {
            PrimedTnt tnt = s.next();
            w.sendParticles(ParticleTypes.FIREWORK, tnt.getX(), tnt.getY() - 1, tnt.getZ(), 2, 0.1, 0.3, 0.1, 0);
            if (tnt.getFuse() <= 40 || tnt.isRemoved()) {
                tnt.discard();
                s.remove();
            }
        }
    }

    /** Am Ziel: Leitstrahl, dann Ring 0 (Mitte) bis Ring 4 (Radius 16), alle 8 Ticks einer. */
    private static void einschlagTick(Einschlag e, int t) {
        BlockPos z = e.ziel;
        if (t == 1) {
            for (int y = 0; y < 60; y += 2) {
                e.welt.sendParticles(ParticleTypes.END_ROD, z.getX() + 0.5, z.getY() + 1 + y, z.getZ() + 0.5,
                        3, 0.1, 0.5, 0.1, 0.01);
            }
            e.welt.playSound(null, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 4f, 0.5f);
        }
        if (t % 8 == 0 && t <= 40) {
            int ring = t / 8 - 1;
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
    }
}
