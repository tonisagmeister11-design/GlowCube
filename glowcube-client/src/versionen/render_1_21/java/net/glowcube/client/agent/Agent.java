package net.glowcube.client.agent;

import net.glowcube.client.GlowCubeClient;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ein Agent: eine Spielerfigur (Mannequin), die der Server Tick fuer Tick
 * selbst bewegt - kein Vanilla-KI, sondern eigene Logik:
 *
 * <ol>
 *   <li><b>Arbeiten</b> - im Umkreis die naechsten Zielbloecke suchen, per
 *       {@link AgentPfad} einen Weg dorthin planen (wenn noetig durch Stein
 *       graben), hinlaufen, abbauen, Beute einlagern. Nichts in Sicht: in die
 *       beste Hoehe fuer das Erz graben bzw. an der Oberflaeche weiterziehen.</li>
 *   <li><b>Zurueck</b> - zum Spieler laufen oder graben; ist er weit weg, in
 *       einer anderen Dimension oder der Weg zu lang, teleportieren.</li>
 *   <li><b>Abliefern</b> - dem Spieler alles zuwerfen, Stapel fuer Stapel,
 *       dann verschwinden.</li>
 * </ol>
 *
 * Laeuft nur auf dem Server-Thread. Der Agent haelt die Chunks um sich herum
 * geladen, solange er arbeitet.
 */
final class Agent {
    private enum Zustand { ARBEITEN, ZURUECK, ABLIEFERN, FERTIG }

    private static final int SCHRITT_TICKS = 4;
    private static final int SUCH_WEITE = 20;
    private static final int SUCH_HOEHE = 12;
    private static final int NAH_GENUG = 48;
    private static final int MAX_BAUSTEINE = 64;

    private final UUID besitzer;
    private final Auftrag auftrag;
    private final String art;
    private final SimpleContainer lager = new SimpleContainer(36);

    private ServerLevel welt;
    private Mannequin koerper;
    private Zustand zustand = Zustand.ARBEITEN;

    // Bewegung
    private BlockPos fuesse;
    private List<BlockPos> pfad;
    private int pfadIndex;
    private Vec3 bewegVon;
    private Vec3 bewegNach;
    private int bewegTick;
    private int bewegDauer;
    private BlockPos turmBlock;

    // Abbau
    private BlockPos abbauPos;
    private int abbauFortschritt;
    private int abbauDauer;

    // Planung
    private BlockPos zielBlock;
    private int planPause;
    private int richtung;
    private final Set<Long> gesperrt = new HashSet<>();
    private int gesperrtAlter;

    // Rueckweg / Abliefern
    private int zurueckTicks;
    private int wurfPause;

    private final Set<Long> erzwungen = new HashSet<>();
    private int ticks;
    private int abgebaut;
    private int geliefert;
    private boolean voll;

    private Agent(UUID besitzer, Auftrag auftrag, String art) {
        this.besitzer = besitzer;
        this.auftrag = auftrag;
        this.art = art;
    }

    // --------------------------------------------------------------- Leben

    static Agent erschaffen(ServerPlayer spieler, Auftrag auftrag, String art) {
        Agent agent = new Agent(spieler.getUUID(), auftrag, art);
        agent.richtung = Math.floorMod(Math.round(spieler.getYRot() / 90f), 4);
        ServerLevel welt = (ServerLevel) spieler.level();
        BlockPos platz = sichererPlatzBei(welt, spieler.blockPosition());
        if (!agent.koerperBauen(welt, platz)) {
            return null;
        }
        agent.effekt(ParticleTypes.PORTAL, 40);
        return agent;
    }

    private boolean koerperBauen(ServerLevel neueWelt, BlockPos platz) {
        Mannequin neu = EntityType.MANNEQUIN.create(neueWelt, EntitySpawnReason.COMMAND);
        if (neu == null) {
            return false;
        }
        neu.snapTo(platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5, richtung * 90f, 0);
        neu.setNoGravity(true);
        neu.setInvulnerable(true);
        neu.setCustomNameVisible(true);
        neu.setItemSlot(EquipmentSlot.MAINHAND,
                (auftrag == Auftrag.HOLZ ? AgentBloecke.AXT : AgentBloecke.SPITZHACKE).copy());
        if (!neueWelt.addFreshEntity(neu)) {
            return false;
        }
        if (koerper != null && !koerper.isRemoved()) {
            koerper.discard();
        }
        chunksFreigeben();
        welt = neueWelt;
        koerper = neu;
        fuesse = platz;
        namenAktualisieren();
        return true;
    }

    UUID besitzer() {
        return besitzer;
    }

    Auftrag auftrag() {
        return auftrag;
    }

    boolean beimZurueckkehren() {
        return zustand == Zustand.ZURUECK || zustand == Zustand.ABLIEFERN;
    }

    boolean fertig() {
        return zustand == Zustand.FERTIG;
    }

    String titel() {
        return auftrag == Auftrag.ERZ && !art.isEmpty() && !art.equals("Alle")
                ? auftrag.anzeigename() + " (" + art + ")" : auftrag.anzeigename();
    }

    void zurueckrufen() {
        if (zustand == Zustand.ARBEITEN) {
            abbauAbbrechen();
            pfad = null;
            zurueckTicks = 0;
            zustand = Zustand.ZURUECK;
        }
    }

    // ---------------------------------------------------------------- Tick

    void tick(MinecraftServer server) {
        if (zustand == Zustand.FERTIG) {
            return;
        }
        ticks++;
        if (koerper == null || koerper.isRemoved()) {
            // Jemand hat ihn entfernt (/kill) - Beute nicht verlieren.
            notfallUebergabe(server);
            zustand = Zustand.FERTIG;
            return;
        }
        if (ticks % 40 == 1) {
            chunksHalten();
        }
        if (ticks % 20 == 0) {
            namenAktualisieren();
        }
        if (++gesperrtAlter > 600) {
            gesperrt.clear();
            gesperrtAlter = 0;
        }
        ServerPlayer spieler = server.getPlayerList().getPlayer(besitzer);

        // Laufende Handlungen zuerst zu Ende fuehren.
        if (bewegNach != null) {
            bewegen();
            return;
        }
        if (abbauPos != null) {
            abbauen();
            return;
        }
        if (fallen()) {
            return;
        }
        // Im Stand genau auf der Zelle bleiben - Spieler und Mobs schieben ihn sonst weg.
        Vec3 mitte = Vec3.atBottomCenterOf(fuesse);
        if (koerper.position().distanceToSqr(mitte) > 0.01) {
            koerper.snapTo(mitte.x, mitte.y, mitte.z, koerper.getYRot(), koerper.getXRot());
        }

        switch (zustand) {
            case ARBEITEN -> arbeiten();
            case ZURUECK -> zurueckkehren(spieler);
            case ABLIEFERN -> abliefern(spieler);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ Arbeiten

    private void arbeiten() {
        if (voll) {
            zurueckrufen();
            return;
        }
        if (pfad != null && pfadIndex < pfad.size()) {
            schritt();
            return;
        }
        pfad = null;
        if (zielBlock != null) {
            BlockState s = welt.getBlockState(zielBlock);
            if (AgentBloecke.istZiel(s, auftrag, art) && erreichbar(fuesse, zielBlock)) {
                abbauStarten(zielBlock);
                zielBlock = null;
                return;
            }
            zielBlock = null;
        }
        if (planPause > 0) {
            planPause--;
            return;
        }
        if (!zielPlanen()) {
            erkunden();
        }
    }

    /** Die naechsten Zielbloecke im Umkreis anpeilen - der erste, zu dem ein Weg fuehrt, gewinnt. */
    private boolean zielPlanen() {
        List<BlockPos> kandidaten = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -SUCH_HOEHE; dy <= SUCH_HOEHE; dy++) {
            for (int dx = -SUCH_WEITE; dx <= SUCH_WEITE; dx++) {
                for (int dz = -SUCH_WEITE; dz <= SUCH_WEITE; dz++) {
                    p.set(fuesse.getX() + dx, fuesse.getY() + dy, fuesse.getZ() + dz);
                    if (welt.isOutsideBuildHeight(p) || !welt.isLoaded(p) || gesperrt.contains(p.asLong())) {
                        continue;
                    }
                    if (AgentBloecke.istZiel(welt.getBlockState(p), auftrag, art)) {
                        kandidaten.add(p.immutable());
                    }
                }
            }
        }
        if (kandidaten.isEmpty()) {
            return false;
        }
        kandidaten.sort((a, b) -> Double.compare(a.distSqr(fuesse), b.distSqr(fuesse)));
        AgentPfad suche = new AgentPfad(welt, bausteine() > 0);
        for (int i = 0; i < Math.min(4, kandidaten.size()); i++) {
            BlockPos ziel = kandidaten.get(i);
            if (erreichbar(fuesse, ziel)) {
                zielBlock = ziel;
                return true;
            }
            List<BlockPos> weg = suche.suchen(fuesse, n -> erreichbar(n, ziel), ziel, 6000, 48);
            if (weg != null) {
                pfadSetzen(weg);
                zielBlock = ziel;
                return true;
            }
            gesperrt.add(ziel.asLong());
        }
        return false;
    }

    /** Nichts in Sicht: weiterziehen - beim Erz in die beste Hoehe, beim Holz ueber die Oberflaeche. */
    private void erkunden() {
        int[][] r = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        int dx = r[richtung][0];
        int dz = r[richtung][1];
        BlockPos wegpunkt;
        if (auftrag == Auftrag.HOLZ) {
            int x = fuesse.getX() + dx * 12;
            int z = fuesse.getZ() + dz * 12;
            wegpunkt = new BlockPos(x, welt.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        } else {
            int zielY = auftrag == Auftrag.STEIN ? fuesse.getY() - 4
                    : AgentBloecke.besteHoehe(auftrag, art, welt);
            int y = fuesse.getY() + Mth.clamp(zielY - fuesse.getY(), -6, 6);
            wegpunkt = new BlockPos(fuesse.getX() + dx * 8, y, fuesse.getZ() + dz * 8);
        }
        AgentPfad suche = new AgentPfad(welt, bausteine() > 0);
        BlockPos wp = wegpunkt;
        List<BlockPos> weg = suche.suchen(fuesse, n -> n.distManhattan(wp) <= 2, wp, 6000, 40);
        if (weg != null && !weg.isEmpty()) {
            pfadSetzen(weg);
        } else {
            // Sackgasse: im Uhrzeigersinn weiter probieren, kurz verschnaufen.
            richtung = (richtung + 1) % 4;
            planPause = 10;
        }
    }

    /** Vom Stand aus abbaubar: Nachbarblock von Fuessen oder Kopf, bis vier Bloecke darueber (Baumstamm). */
    private static boolean erreichbar(BlockPos f, BlockPos ziel) {
        int dx = Math.abs(ziel.getX() - f.getX());
        int dz = Math.abs(ziel.getZ() - f.getZ());
        int dy = ziel.getY() - f.getY();
        if (dx > 1 || dz > 1 || dy < -1 || dy > 4) {
            return false;
        }
        return !(dx == 0 && dz == 0 && (dy == 0 || dy == 1));
    }

    // ------------------------------------------------------------ Bewegung

    private void pfadSetzen(List<BlockPos> weg) {
        pfad = weg;
        pfadIndex = 0;
    }

    /** Den naechsten Schritt vorbereiten: freiraeumen, Halt schaffen, losgehen. */
    private void schritt() {
        BlockPos nach = pfad.get(pfadIndex);
        int dy = nach.getY() - fuesse.getY();
        boolean seitlich = nach.getX() != fuesse.getX() || nach.getZ() != fuesse.getZ();

        List<BlockPos> frei = new ArrayList<>(3);
        if (seitlich && dy == 1) {
            frei.add(fuesse.above(2));
            frei.add(nach.above());
            frei.add(nach);
        } else if (seitlich && dy == -1) {
            frei.add(nach.above(2));
            frei.add(nach.above());
            frei.add(nach);
        } else if (seitlich) {
            frei.add(nach.above());
            frei.add(nach);
        } else if (dy == -1) {
            frei.add(nach);
        } else {
            frei.add(nach.above());
        }
        for (BlockPos zelle : frei) {
            BlockState s = welt.getBlockState(zelle);
            if (AgentBloecke.frei(welt, zelle, s)) {
                continue;
            }
            if (AgentBloecke.abbaubar(welt, zelle, s)) {
                abbauStarten(zelle);
            } else {
                // Unterwegs hat sich etwas geaendert (Lava, Wasser, ein Spieler baut): neu planen.
                pfad = null;
            }
            return;
        }

        turmBlock = null;
        if (!seitlich && dy == 1) {
            // Turm: hochspringen und unter sich einen Baustein setzen.
            if (bausteine() == 0) {
                pfad = null;
                return;
            }
            turmBlock = fuesse;
        } else {
            BlockPos unten = nach.below();
            if (!AgentBloecke.traegt(welt, unten, welt.getBlockState(unten))) {
                if (!bausteinSetzen(unten)) {
                    pfad = null;
                    return;
                }
            }
        }
        bewegungStarten(nach, seitlich && dy != 0 ? SCHRITT_TICKS + 1 : SCHRITT_TICKS);
        pfadIndex++;
    }

    private void bewegungStarten(BlockPos nach, int dauer) {
        bewegVon = koerper.position();
        bewegNach = Vec3.atBottomCenterOf(nach);
        bewegTick = 0;
        bewegDauer = dauer;
        Vec3 d = bewegNach.subtract(bewegVon);
        if (d.horizontalDistanceSqr() > 0.01) {
            float gier = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
            ausrichten(gier, 0);
        }
        fuesse = nach;
    }

    private void bewegen() {
        bewegTick++;
        double t = Math.min(1.0, bewegTick / (double) bewegDauer);
        Vec3 p = bewegVon.lerp(bewegNach, t);
        koerper.snapTo(p.x, p.y, p.z, koerper.getYRot(), koerper.getXRot());
        if (t >= 1.0) {
            bewegNach = null;
            if (turmBlock != null) {
                bausteinSetzen(turmBlock);
                turmBlock = null;
            }
        }
    }

    /** Ohne Halt unter den Fuessen: einen Block tiefer fallen. */
    private boolean fallen() {
        BlockPos unten = fuesse.below();
        BlockState s = welt.getBlockState(unten);
        if (AgentBloecke.traegt(welt, unten, s) || !welt.isLoaded(unten) || welt.isOutsideBuildHeight(unten)) {
            return false;
        }
        if (!AgentBloecke.frei(welt, unten, s)) {
            // Lava o.ae. darunter: lieber eine Bruecke als hineinfallen.
            bausteinSetzen(unten);
            return false;
        }
        pfad = null;
        bewegungStarten(unten, 2);
        return true;
    }

    private void ausrichten(float gier, float neigung) {
        koerper.setYRot(gier);
        koerper.setXRot(neigung);
        koerper.setYHeadRot(gier);
        koerper.setYBodyRot(gier);
    }

    private void anschauen(Vec3 punkt) {
        Vec3 auge = koerper.getEyePosition();
        Vec3 d = punkt.subtract(auge);
        float gier = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float neigung = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        ausrichten(gier, neigung);
    }

    // -------------------------------------------------------------- Abbauen

    private void abbauStarten(BlockPos pos) {
        BlockState s = welt.getBlockState(pos);
        abbauPos = pos;
        abbauFortschritt = 0;
        abbauDauer = AgentBloecke.abbauTicks(welt, pos, s);
        koerper.setItemSlot(EquipmentSlot.MAINHAND, AgentBloecke.werkzeug(s).copy());
        anschauen(Vec3.atCenterOf(pos));
        koerper.swing(InteractionHand.MAIN_HAND, true);
    }

    private void abbauen() {
        BlockState s = welt.getBlockState(abbauPos);
        if (AgentBloecke.frei(welt, abbauPos, s)) {
            // Schon weg (abgebaut, weggespuelt) - nichts mehr zu tun.
            abbauAbbrechen();
            return;
        }
        abbauFortschritt++;
        anschauen(Vec3.atCenterOf(abbauPos));
        if (abbauFortschritt % 4 == 0) {
            koerper.swing(InteractionHand.MAIN_HAND, true);
        }
        int stufe = Math.min(9, abbauFortschritt * 10 / Math.max(1, abbauDauer));
        welt.destroyBlockProgress(koerper.getId(), abbauPos, stufe);
        if (abbauFortschritt < abbauDauer) {
            return;
        }
        boolean ziel = AgentBloecke.istZiel(s, auftrag, art);
        ItemStack werkzeug = AgentBloecke.werkzeug(s);
        List<ItemStack> beute = Block.getDrops(s, welt, abbauPos, welt.getBlockEntity(abbauPos), koerper, werkzeug);
        welt.destroyBlockProgress(koerper.getId(), abbauPos, -1);
        welt.destroyBlock(abbauPos, false, koerper, 512);
        koerper.swing(InteractionHand.MAIN_HAND, true);
        if (ziel) {
            abgebaut++;
        }
        for (ItemStack stapel : beute) {
            einlagern(stapel, ziel);
        }
        abbauPos = null;
    }

    private void abbauAbbrechen() {
        if (abbauPos != null && koerper != null) {
            welt.destroyBlockProgress(koerper.getId(), abbauPos, -1);
        }
        abbauPos = null;
    }

    /**
     * Beute behalten: alles vom Zielblock; beim Stein-Agenten alles; sonst nur
     * so viele Bausteine, wie er fuer Bruecken und Tuerme braucht.
     */
    private void einlagern(ItemStack stapel, boolean vomZiel) {
        if (stapel.isEmpty()) {
            return;
        }
        boolean behalten = vomZiel || auftrag == Auftrag.STEIN
                || AgentBloecke.istBaustein(stapel) && bausteine() < MAX_BAUSTEINE;
        if (!behalten) {
            return;
        }
        ItemStack rest = lager.addItem(stapel);
        if (!rest.isEmpty() && !voll) {
            voll = true;
            ServerPlayer spieler = welt.getServer().getPlayerList().getPlayer(besitzer);
            if (spieler != null) {
                AgentWelt.melden(spieler, ChatFormatting.YELLOW, titel() + ": Inventar voll - ich komme zurueck.");
            }
        }
    }

    private int bausteine() {
        int n = 0;
        for (int i = 0; i < lager.getContainerSize(); i++) {
            ItemStack s = lager.getItem(i);
            if (AgentBloecke.istBaustein(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Einen Baustein aus dem Lager an diese Stelle setzen (Bruecke, Turm, ueber Lava). */
    private boolean bausteinSetzen(BlockPos pos) {
        BlockState dort = welt.getBlockState(pos);
        if (!dort.canBeReplaced()) {
            return false;
        }
        for (int i = 0; i < lager.getContainerSize(); i++) {
            ItemStack s = lager.getItem(i);
            if (AgentBloecke.istBaustein(s) && s.getItem() instanceof BlockItem block) {
                welt.setBlockAndUpdate(pos, block.getBlock().defaultBlockState());
                s.shrink(1);
                lager.setChanged();
                anschauen(Vec3.atCenterOf(pos));
                koerper.swing(InteractionHand.MAIN_HAND, true);
                welt.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, 1f);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ Rueckweg

    private void zurueckkehren(ServerPlayer spieler) {
        if (spieler == null) {
            return;
        }
        zurueckTicks++;
        BlockPos ziel = spieler.blockPosition();
        boolean andereWelt = spieler.level() != welt;
        double abstand = andereWelt ? Double.MAX_VALUE : Math.sqrt(fuesse.distSqr(ziel));
        if (!andereWelt && abstand <= 2.5) {
            zustand = Zustand.ABLIEFERN;
            pfad = null;
            return;
        }
        // Weit weg, andere Dimension oder schon zu lange unterwegs: teleportieren.
        if (andereWelt || abstand > NAH_GENUG || zurueckTicks > 600) {
            teleportieren(spieler);
            zustand = Zustand.ABLIEFERN;
            return;
        }
        if (pfad == null || pfadIndex >= pfad.size() || zurueckTicks % 40 == 0) {
            AgentPfad suche = new AgentPfad(welt, bausteine() > 0);
            List<BlockPos> weg = suche.suchen(fuesse, n -> n.distManhattan(ziel) <= 2, ziel, 8000, 64);
            if (weg == null) {
                teleportieren(spieler);
                zustand = Zustand.ABLIEFERN;
                return;
            }
            pfadSetzen(weg);
            if (weg.isEmpty()) {
                zustand = Zustand.ABLIEFERN;
                return;
            }
        }
        schritt();
    }

    private void teleportieren(ServerPlayer spieler) {
        ServerLevel zielWelt = (ServerLevel) spieler.level();
        BlockPos platz = sichererPlatzBei(zielWelt, spieler.blockPosition());
        effekt(ParticleTypes.PORTAL, 40);
        welt.playSound(null, koerper.getX(), koerper.getY(), koerper.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1f, 1f);
        pfad = null;
        abbauAbbrechen();
        if (zielWelt == welt) {
            koerper.snapTo(platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5, koerper.getYRot(), 0);
            fuesse = platz;
            chunksHalten();
        } else if (!koerperBauen(zielWelt, platz)) {
            return;
        }
        effekt(ParticleTypes.PORTAL, 40);
        welt.playSound(null, platz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1f, 1f);
    }

    /** Frei, zwei Bloecke hoch und mit Boden - moeglichst nah beim Spieler, aber nicht in ihm. */
    private static BlockPos sichererPlatzBei(ServerLevel welt, BlockPos mitte) {
        int[][] versuche = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {2, 2}, {-2, -2}};
        for (int[] v : versuche) {
            for (int dy = 0; dy >= -2; dy--) {
                BlockPos p = mitte.offset(v[0], dy, v[1]);
                for (int hoch = 0; hoch <= 2; hoch++) {
                    BlockPos q = p.above(hoch);
                    if (AgentBloecke.frei(welt, q, welt.getBlockState(q))
                            && AgentBloecke.frei(welt, q.above(), welt.getBlockState(q.above()))
                            && AgentBloecke.traegt(welt, q.below(), welt.getBlockState(q.below()))) {
                        return q;
                    }
                }
            }
        }
        return mitte;
    }

    // ------------------------------------------------------------ Abliefern

    /** Dem Spieler alles zuwerfen - ein Stapel alle drei Ticks, dann verschwinden. */
    private void abliefern(ServerPlayer spieler) {
        if (spieler == null) {
            return;
        }
        if (spieler.level() != welt || Math.sqrt(fuesse.distSqr(spieler.blockPosition())) > 5) {
            // Er ist weitergelaufen - hinterher.
            zustand = Zustand.ZURUECK;
            return;
        }
        anschauen(spieler.getEyePosition());
        if (wurfPause-- > 0) {
            return;
        }
        wurfPause = 3;
        for (int i = 0; i < lager.getContainerSize(); i++) {
            ItemStack stapel = lager.getItem(i);
            if (stapel.isEmpty()) {
                continue;
            }
            lager.setItem(i, ItemStack.EMPTY);
            werfen(stapel, spieler);
            geliefert += stapel.getCount();
            return;
        }
        // Alles abgeliefert.
        AgentWelt.melden(spieler, ChatFormatting.GREEN, titel() + " hat dir " + geliefert + " Items gebracht ("
                + abgebaut + " Bloecke abgebaut).");
        effekt(ParticleTypes.POOF, 20);
        welt.playSound(null, koerper.getX(), koerper.getY(), koerper.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6f, 0.8f);
        koerper.discard();
        zustand = Zustand.FERTIG;
    }

    private void werfen(ItemStack stapel, ServerPlayer spieler) {
        Vec3 von = koerper.getEyePosition().subtract(0, 0.3, 0);
        Vec3 richtungZum = spieler.getEyePosition().subtract(von);
        Vec3 schwung = richtungZum.normalize().scale(Math.min(0.45, 0.12 * richtungZum.length())).add(0, 0.18, 0);
        ItemEntity wurf = new ItemEntity(welt, von.x, von.y, von.z, stapel, schwung.x, schwung.y, schwung.z);
        wurf.setPickUpDelay(8);
        welt.addFreshEntity(wurf);
        koerper.swing(InteractionHand.MAIN_HAND, true);
    }

    /** Ohne Umweg: alles direkt ins Inventar (Welt schliesst, Agent weg). Was nicht passt, faellt vor die Fuesse. */
    void notfallUebergabe(MinecraftServer server) {
        ServerPlayer spieler = server.getPlayerList().getPlayer(besitzer);
        for (int i = 0; i < lager.getContainerSize(); i++) {
            ItemStack stapel = lager.getItem(i);
            if (stapel.isEmpty()) {
                continue;
            }
            lager.setItem(i, ItemStack.EMPTY);
            if (spieler != null && spieler.getInventory().add(stapel) && stapel.isEmpty()) {
                continue;
            }
            ServerLevel wo = spieler != null ? (ServerLevel) spieler.level() : welt;
            Vec3 p = spieler != null ? spieler.position() : (koerper != null ? koerper.position() : Vec3.atCenterOf(fuesse));
            if (wo != null) {
                wo.addFreshEntity(new ItemEntity(wo, p.x, p.y + 0.5, p.z, stapel));
            }
        }
        zustand = Zustand.FERTIG;
    }

    void aufraeumen() {
        abbauAbbrechen();
        chunksFreigeben();
        if (koerper != null && !koerper.isRemoved()) {
            koerper.discard();
        }
    }

    // ------------------------------------------------------------- Kleinkram

    /** Die Chunks um den Agenten geladen halten - nur die, die wir selbst erzwungen haben, geben wir wieder frei. */
    private void chunksHalten() {
        ChunkPos mitte = new ChunkPos(fuesse);
        Set<Long> gewuenscht = new HashSet<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                gewuenscht.add(ChunkPos.asLong(mitte.x + dx, mitte.z + dz));
            }
        }
        for (long c : new ArrayList<>(erzwungen)) {
            if (!gewuenscht.contains(c)) {
                welt.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
                erzwungen.remove(c);
            }
        }
        for (long c : gewuenscht) {
            if (!erzwungen.contains(c) && !welt.getForceLoadedChunks().contains((long) c)) {
                welt.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
                erzwungen.add(c);
            }
        }
    }

    private void chunksFreigeben() {
        if (welt == null) {
            return;
        }
        for (long c : erzwungen) {
            try {
                welt.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
            } catch (RuntimeException fehler) {
                GlowCubeClient.LOGGER.warn("GlowCube: Chunk nicht freigegeben", fehler);
            }
        }
        erzwungen.clear();
    }

    private void namenAktualisieren() {
        String text = titel() + (abgebaut > 0 ? " · " + abgebaut : "");
        if (zustand == Zustand.ZURUECK || zustand == Zustand.ABLIEFERN) {
            text += " → zurueck";
        }
        koerper.setCustomName(Component.literal(text).withStyle(ChatFormatting.AQUA));
    }

    private void effekt(net.minecraft.core.particles.SimpleParticleType art, int anzahl) {
        if (koerper == null) {
            return;
        }
        welt.sendParticles(art, koerper.getX(), koerper.getY() + 1, koerper.getZ(), anzahl, 0.3, 0.6, 0.3, 0.05);
    }
}
