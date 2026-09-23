package net.glowcube.client.agent;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.bauplan.Bauplaene;
import net.glowcube.client.bauplan.Bauplan;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builder-Agent: baut ein Schematic. Es steht auf der Hoehe des Spielers,
 * beginnt zwei Bloecke vor ihm, mittig vor ihm ausgerichtet und in seine
 * Blickrichtung gedreht. Er hat alle Bloecke dabei; was im Weg ist (auch
 * dort, wo das Schematic Luft vorsieht), raeumt er ab. Gebaut wird Schicht
 * fuer Schicht von unten nach oben, im Tempo aus dem Menue. Der Builder
 * schwebt dabei ueber der Baustelle wie im Kreativmodus. Am Ende eine
 * Meldung im Chat, dann verschwindet er.
 */
final class AgentBaumeister implements AgentArbeiter {
    private record Auftragsblock(BlockPos pos, BlockState zustand) {
    }

    private final UUID besitzer;
    private final String planName;
    private AgentWerte werte;

    private ServerLevel welt;
    private Mannequin koerper;
    private final ChunkHalter chunks = new ChunkHalter();

    private List<Auftragsblock> liste;
    private int index;
    private double guthaben;
    private int gesetzt;
    private int abgeraeumt;
    private int unbekannt;
    private int ticks;
    private int schwungPause;
    private boolean fertig;
    private boolean abgebrochen;

    private AgentBaumeister(UUID besitzer, String planName, AgentWerte werte) {
        this.besitzer = besitzer;
        this.planName = planName;
        this.werte = werte;
    }

    static AgentBaumeister erschaffen(ServerPlayer spieler, String planName, AgentWerte werte) {
        Bauplan plan = Bauplaene.laden(planName);
        if (plan == null) {
            AgentWelt.melden(spieler, ChatFormatting.RED, "Schematic \"" + planName + "\" gibt es nicht oder es ist kaputt. "
                    + "Eigene Schematics gehoeren nach " + Bauplaene.ordner());
            return null;
        }
        AgentBaumeister b = new AgentBaumeister(spieler.getUUID(), planName, werte);
        ServerLevel welt = (ServerLevel) spieler.level();
        b.welt = welt;
        b.liste = b.planen(plan, spieler);
        if (!b.koerperBauen(Agent.sichererPlatzBei(welt, spieler.blockPosition()))) {
            return null;
        }
        b.effekt(ParticleTypes.PORTAL, 40);
        AgentWelt.melden(spieler, ChatFormatting.AQUA, "Builder: baue \"" + planName + "\" (" + plan.breite + "x"
                + plan.hoehe + "x" + plan.laenge + ", " + plan.festeBloecke() + " Bloecke).");
        return b;
    }

    /**
     * Aus dem Plan die Liste der Weltpositionen und Zustaende: gedreht in die
     * Blickrichtung des Spielers, von unten nach oben, je Schicht in Schlangenlinien.
     */
    private List<Auftragsblock> planen(Bauplan plan, ServerPlayer spieler) {
        Direction blick = spieler.getDirection();
        Rotation drehung = switch (blick) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        BlockPos ursprung = spieler.blockPosition();
        HolderLookup.RegistryLookup<Block> bloecke = welt.registryAccess().lookupOrThrow(Registries.BLOCK);
        Map<String, BlockState> zwischenspeicher = new HashMap<>();

        List<Auftragsblock> ergebnis = new ArrayList<>(plan.bloecke.size());
        int halbeBreite = plan.breite / 2;
        for (Bauplan.Block b : plan.bloecke) {
            BlockState zustand = zwischenspeicher.computeIfAbsent(b.zustand(), text -> lesen(bloecke, text));
            if (zustand == null) {
                unbekannt++;
                continue;
            }
            int lx = b.x() - halbeBreite;
            int lz = b.z() + 2;
            int wx;
            int wz;
            switch (drehung) {
                case CLOCKWISE_90 -> { wx = -lz; wz = lx; }
                case CLOCKWISE_180 -> { wx = -lx; wz = -lz; }
                case COUNTERCLOCKWISE_90 -> { wx = lz; wz = -lx; }
                default -> { wx = lx; wz = lz; }
            }
            BlockPos pos = ursprung.offset(wx, b.y(), wz);
            ergebnis.add(new Auftragsblock(pos, zustand.rotate(drehung)));
        }
        // Von unten nach oben; je Schicht zeilenweise hin und her.
        ergebnis.sort((a, c) -> {
            if (a.pos.getY() != c.pos.getY()) {
                return Integer.compare(a.pos.getY(), c.pos.getY());
            }
            if (a.pos.getX() != c.pos.getX()) {
                return Integer.compare(a.pos.getX(), c.pos.getX());
            }
            return (a.pos.getX() & 1) == 0 ? Integer.compare(a.pos.getZ(), c.pos.getZ())
                    : Integer.compare(c.pos.getZ(), a.pos.getZ());
        });
        return ergebnis;
    }

    private static BlockState lesen(HolderLookup.RegistryLookup<Block> bloecke, String text) {
        try {
            return BlockStateParser.parseForBlock(bloecke, text, false).blockState();
        } catch (Exception kaputt) {
            GlowCubeClient.LOGGER.warn("GlowCube: Blockzustand im Schematic unbekannt: {}", text);
            return null;
        }
    }

    private boolean koerperBauen(BlockPos platz) {
        Mannequin neu = EntityType.MANNEQUIN.create(welt, EntitySpawnReason.COMMAND);
        if (neu == null) {
            return false;
        }
        neu.snapTo(platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5, 0, 0);
        neu.setNoGravity(true);
        neu.setInvulnerable(true);
        neu.setCustomNameVisible(true);
        neu.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BRICKS));
        if (!welt.addFreshEntity(neu)) {
            return false;
        }
        koerper = neu;
        namenAktualisieren();
        return true;
    }

    @Override
    public UUID besitzer() {
        return besitzer;
    }

    @Override
    public Auftrag auftrag() {
        return Auftrag.BAUMEISTER;
    }

    @Override
    public String titel() {
        return "Builder-Agent (" + planName + ")";
    }

    @Override
    public boolean beimZurueckkehren() {
        return fertig;
    }

    @Override
    public boolean fertig() {
        return fertig;
    }

    @Override
    public void zurueckrufen() {
        if (!fertig) {
            abgebrochen = true;
        }
    }

    @Override
    public void einstellen(AgentWerte neu) {
        werte = neu;
    }

    @Override
    public void notfallUebergabe(MinecraftServer server) {
        fertig = true;
    }

    @Override
    public void aufraeumen() {
        chunks.freigeben();
        if (koerper != null && !koerper.isRemoved()) {
            koerper.discard();
        }
    }

    // ---------------------------------------------------------------- Tick

    @Override
    public void tick(MinecraftServer server) {
        if (fertig) {
            return;
        }
        ticks++;
        if (koerper == null || koerper.isRemoved()) {
            fertig = true;
            return;
        }
        ServerPlayer spieler = server.getPlayerList().getPlayer(besitzer);
        if (abgebrochen) {
            abschliessen(spieler, "abgebrochen");
            return;
        }
        if (ticks % 20 == 0) {
            namenAktualisieren();
        }
        if (schwungPause > 0) {
            schwungPause--;
        }

        // Bau-Tempo = Bloecke pro Sekunde; bereits richtige Bloecke (meist Luft) kosten nichts
        // und werden zu Tausenden je Tick uebersprungen.
        guthaben += Math.max(1, werte.abbauTempo()) / 20.0;
        int geprueft = 0;
        while (index < liste.size() && guthaben >= 1 && geprueft < 6000) {
            Auftragsblock a = liste.get(index);
            geprueft++;
            if (geprueft == 1 && ticks % 10 == 1) {
                chunks.halten(welt, a.pos, 1);
            }
            BlockState jetzt = welt.getBlockState(a.pos);
            if (jetzt == a.zustand || jetzt.isAir() && a.zustand.isAir()) {
                index++;
                continue;
            }
            setzen(a, jetzt);
            index++;
            guthaben -= 1;
        }
        if (index >= liste.size()) {
            abschliessen(spieler, "fertig");
            return;
        }
        schweben(liste.get(index).pos);
    }

    private void setzen(Auftragsblock a, BlockState jetzt) {
        if (a.zustand.isAir()) {
            // Im Weg: abraeumen (mit Bruchpartikeln, ohne Drops).
            welt.destroyBlock(a.pos, false, koerper, 512);
            abgeraeumt++;
        } else {
            if (!jetzt.isAir() && !jetzt.canBeReplaced()) {
                abgeraeumt++;
            }
            // Ohne Nachbar-Updates, damit halbe Tueren und Betten nicht zerfallen,
            // bevor ihre zweite Haelfte steht.
            welt.setBlock(a.pos, a.zustand, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            gesetzt++;
            if (gesetzt % 3 == 0) {
                welt.playSound(null, a.pos, a.zustand.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7f, 1f);
            }
        }
        if (schwungPause == 0) {
            koerper.setItemSlot(EquipmentSlot.MAINHAND, a.zustand.isAir()
                    ? new ItemStack(Items.DIAMOND_PICKAXE) : new ItemStack(a.zustand.getBlock().asItem()));
            anschauen(Vec3.atCenterOf(a.pos));
            koerper.swing(InteractionHand.MAIN_HAND, true);
            schwungPause = 4;
        }
    }

    /** Der Builder schwebt schraeg ueber der Stelle, an der gerade gebaut wird. */
    private void schweben(BlockPos an) {
        Vec3 ziel = new Vec3(an.getX() + 0.5, an.getY() + 2.2, an.getZ() - 1.5);
        Vec3 jetzt = koerper.position();
        Vec3 d = ziel.subtract(jetzt);
        double schritt = 0.35 * Math.max(1.0, werte.tempo());
        Vec3 neu = d.length() <= schritt ? ziel : jetzt.add(d.normalize().scale(schritt));
        koerper.snapTo(neu.x, neu.y, neu.z, koerper.getYRot(), koerper.getXRot());
    }

    private void abschliessen(ServerPlayer spieler, String wie) {
        if (spieler != null) {
            String text = "Builder: \"" + planName + "\" " + wie + " - " + gesetzt + " Bloecke gesetzt, "
                    + abgeraeumt + " abgeraeumt" + (unbekannt > 0 ? ", " + unbekannt + " unbekannte Bloecke ausgelassen" : "") + ".";
            AgentWelt.melden(spieler, wie.equals("fertig") ? ChatFormatting.GREEN : ChatFormatting.YELLOW, text);
        }
        effekt(ParticleTypes.POOF, 30);
        welt.playSound(null, koerper.getX(), koerper.getY(), koerper.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6f, 1.2f);
        fertig = true;
    }

    private void anschauen(Vec3 punkt) {
        Vec3 auge = koerper.getEyePosition();
        Vec3 d = punkt.subtract(auge);
        float gier = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float neigung = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        koerper.setYRot(gier);
        koerper.setXRot(neigung);
        koerper.setYHeadRot(gier);
        koerper.setYBodyRot(gier);
    }

    private void namenAktualisieren() {
        int prozent = liste.isEmpty() ? 100 : (int) (100L * index / liste.size());
        koerper.setCustomName(Component.literal(titel() + " · " + prozent + "%").withStyle(ChatFormatting.GREEN));
    }

    private void effekt(SimpleParticleType art, int anzahl) {
        if (koerper != null) {
            welt.sendParticles(art, koerper.getX(), koerper.getY() + 1, koerper.getZ(), anzahl, 0.3, 0.6, 0.3, 0.05);
        }
    }
}
