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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
final class Agent implements AgentArbeiter {
    private enum Zustand { ARBEITEN, KISTE, ZURUECK, ABLIEFERN, FERTIG }

    private static final int SCHRITT_TICKS = 4;
    private static final int SUCH_WEITE = 20;
    private static final int SUCH_HOEHE = 12;
    private static final int NAH_GENUG = 48;
    private static final int MAX_BAUSTEINE = 64;

    private final UUID besitzer;
    private final Auftrag auftrag;
    private final int nummer;
    private final String art;
    private AgentWerte werte = AgentWerte.STANDARD;
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

    // Startpunkt (Farm: Feldmitte, Tunnel: Tunnelanfang) und Arbeitsplatz vor dem Kistengang
    private BlockPos heimat;
    private BlockPos arbeitsPlatz;
    private ServerLevel arbeitsWelt;
    private int kisteTicks;

    // Tunnel
    private int tunnelDx;
    private int tunnelDz;
    private int tunnelBreite = 3;
    private int tunnelHoehe = 3;
    private int tunnelSchritt;
    private int tunnelVersuche;

    private final Set<Long> erzwungen = new HashSet<>();
    private int ticks;
    private int abgebaut;
    private int geliefert;
    private boolean voll;

    private Agent(UUID besitzer, Auftrag auftrag, int nummer, String art) {
        this.besitzer = besitzer;
        this.auftrag = auftrag;
        this.nummer = nummer;
        this.art = art;
    }

    // --------------------------------------------------------------- Leben

    static Agent erschaffen(ServerPlayer spieler, Auftrag auftrag, int nummer, String art, AgentWerte werte) {
        Agent agent = new Agent(spieler.getUUID(), auftrag, nummer, art);
        agent.werte = werte;
        int blick = Math.floorMod(Math.round(spieler.getYRot() / 90f), 4);
        // Mehrere Agenten schwaermen in verschiedene Richtungen aus.
        agent.richtung = auftrag == Auftrag.TUNNEL ? blick : (blick + nummer - 1) % 4;
        ServerLevel welt = (ServerLevel) spieler.level();
        BlockPos start = spieler.blockPosition();
        // Einsatzort: dort arbeiten, statt beim Spieler.
        AgentWelt.WeltOrt ort = AgentWelt.ort(spieler.getUUID());
        if (ort != null) {
            welt = ort.welt();
            start = ort.pos();
        }
        agent.heimat = start;
        if (auftrag == Auftrag.TUNNEL) {
            int[][] r = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
            agent.tunnelDx = r[blick][0];
            agent.tunnelDz = r[blick][1];
            switch (art) {
                case "1x2" -> { agent.tunnelBreite = 1; agent.tunnelHoehe = 2; }
                case "2x2" -> { agent.tunnelBreite = 2; agent.tunnelHoehe = 2; }
                default -> { agent.tunnelBreite = 3; agent.tunnelHoehe = 3; }
            }
            // Bausteine zum Abdichten von Lava und Wasser.
            agent.lager.addItem(new ItemStack(Items.COBBLESTONE, 64));
        }
        BlockPos platz = sichererPlatzBei(welt, start, nummer);
        if (!agent.koerperBauen(welt, platz)) {
            return null;
        }
        agent.effekt(ParticleTypes.PORTAL, 40);
        return agent;
    }

    private boolean koerperBauen(ServerLevel neueWelt, BlockPos platz) {
        Mannequin neu = AgentFassung.mannequin(neueWelt);
        if (neu == null) {
            return false;
        }
        neu.snapTo(platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5, richtung * 90f, 0);
        neu.setNoGravity(true);
        AgentFassung.unverwundbar(neu);
        neu.setCustomNameVisible(true);
        neu.setItemSlot(EquipmentSlot.MAINHAND, (auftrag == Auftrag.HOLZ ? AgentBloecke.AXT
                : auftrag == Auftrag.BAUER ? AgentBloecke.HACKE : AgentBloecke.SPITZHACKE).copy());
        neu.setGlowingTag(werte.leuchten());
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

    @Override
    public UUID besitzer() {
        return besitzer;
    }

    @Override
    public Auftrag auftrag() {
        return auftrag;
    }

    @Override
    public int nummer() {
        return nummer;
    }

    @Override
    public net.minecraft.world.entity.Entity koerper() {
        return koerper;
    }

    @Override
    public String zustandText() {
        return switch (zustand) {
            case KISTE -> "zur Kiste";
            case ZURUECK, ABLIEFERN -> "kommt zurueck";
            case FERTIG -> "fertig";
            default -> auftrag == Auftrag.TUNNEL ? "graebt " + tunnelSchritt + "/" + werte.zahl()
                    : abbauPos != null ? "baut ab" : pfad != null ? "unterwegs" : "sucht";
        };
    }

    @Override
    public int beute() {
        return AgentKiste.anzahl(lager);
    }

    @Override
    public long reservierung() {
        return zielBlock != null ? zielBlock.asLong() : abbauPos != null ? abbauPos.asLong() : Long.MIN_VALUE;
    }

    @Override
    public boolean beimZurueckkehren() {
        return zustand == Zustand.ZURUECK || zustand == Zustand.ABLIEFERN;
    }

    @Override
    public boolean fertig() {
        return zustand == Zustand.FERTIG;
    }

    @Override
    public String titel() {
        String name = auftrag.anzeigename() + " #" + nummer;
        if (auftrag == Auftrag.ERZ && !art.isEmpty() && !art.equals("Alle") || auftrag == Auftrag.TUNNEL) {
            name += " (" + art + ")";
        }
        return name;
    }

    /** Neue Einstellungen aus dem Menue - gelten ab dem naechsten Schritt bzw. Block. */
    @Override
    public void einstellen(AgentWerte neu) {
        werte = neu;
        if (koerper != null) {
            koerper.setGlowingTag(neu.leuchten());
        }
        // Einen laufenden Abbau gleich mit beschleunigen.
        if (abbauPos != null) {
            abbauDauer = abbauZeit(welt.getBlockState(abbauPos), abbauPos);
        }
    }

    /** Ticks fuer einen Schritt: 4 bei Tempo 1, 1 bei Tempo 4. */
    private int schrittTicks(boolean stufe) {
        int basis = (int) Math.max(1, Math.round(SCHRITT_TICKS / Math.max(1.0, werte.tempo())));
        return stufe ? basis + 1 : basis;
    }

    private int abbauZeit(BlockState s, BlockPos pos) {
        int normal = AgentBloecke.abbauTicks(welt, pos, s);
        return Math.max(1, (int) Math.round(normal / Math.max(1.0, werte.abbauTempo())));
    }

    @Override
    public void zurueckrufen() {
        if (zustand == Zustand.ARBEITEN || zustand == Zustand.KISTE) {
            abbauAbbrechen();
            pfad = null;
            zurueckTicks = 0;
            zustand = Zustand.ZURUECK;
        }
    }

    // ---------------------------------------------------------------- Tick

    @Override
    public void tick(MinecraftServer server) {
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
            case ARBEITEN -> {
                if (auftrag == Auftrag.TUNNEL) {
                    tunnelArbeiten();
                } else {
                    arbeiten();
                }
            }
            case KISTE -> zurKiste();
            case ZURUECK -> zurueckkehren(spieler);
            case ABLIEFERN -> abliefern(spieler);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ Arbeiten

    private void arbeiten() {
        if (voll) {
            vollGeworden();
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

    /**
     * Laufen mehrere Agenten derselben Art, teilt sich die Umgebung wie eine
     * Torte um die Heimat: jeder nimmt zuerst die Ziele in seinem Stueck.
     * Ohne das griffen alle nach derselben Erzader und liefen denselben Weg -
     * dann sieht es aus, als waere nur einer unterwegs. Ist das eigene Stueck
     * leer, hilft er bei den anderen aus.
     */
    private List<BlockPos> eigenerSektor(List<BlockPos> kandidaten) {
        int[] rang = AgentWelt.rang(this);
        if (rang[1] <= 1) {
            return kandidaten;
        }
        double breite = 2 * Math.PI / rang[1];
        double von = rang[0] * breite;
        List<BlockPos> meine = new ArrayList<>();
        for (BlockPos p : kandidaten) {
            double winkel = Math.atan2(p.getZ() + 0.5 - (heimat.getZ() + 0.5), p.getX() + 0.5 - (heimat.getX() + 0.5));
            winkel = (winkel + 2 * Math.PI) % (2 * Math.PI);
            if (winkel >= von && winkel < von + breite) {
                meine.add(p);
            }
        }
        return meine.isEmpty() ? kandidaten : meine;
    }

    /** Die naechsten Zielbloecke im Umkreis anpeilen - der erste, zu dem ein Weg fuehrt, gewinnt. */
    private boolean zielPlanen() {
        if (werte.xray()) {
            return xrayPlanen();
        }
        List<BlockPos> kandidaten = new ArrayList<>();
        Set<Long> belegt = AgentWelt.reserviertVonAnderen(this);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean feld = auftrag == Auftrag.BAUER;
        BlockPos mitte = feld ? heimat : fuesse;
        int weite = feld ? Math.max(4, werte.zahl()) : SUCH_WEITE;
        int hoehe = feld ? 6 : SUCH_HOEHE;
        for (int dy = -hoehe; dy <= hoehe; dy++) {
            for (int dx = -weite; dx <= weite; dx++) {
                for (int dz = -weite; dz <= weite; dz++) {
                    p.set(mitte.getX() + dx, mitte.getY() + dy, mitte.getZ() + dz);
                    if (welt.isOutsideBuildHeight(p) || !welt.isLoaded(p) || gesperrt.contains(p.asLong())
                            || belegt.contains(p.asLong())) {
                        continue;
                    }
                    if (feld && dx * dx + dz * dz > weite * weite) {
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
        kandidaten = eigenerSektor(kandidaten);
        kandidaten.sort((a, b) -> Double.compare(a.distSqr(fuesse), b.distSqr(fuesse)));
        AgentPfad suche = feld ? new AgentPfad(welt, false, false) : new AgentPfad(welt, bausteine() > 0);
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

    /**
     * X-Ray: die Chunks um den Agenten nach Zielbloecken durchsuchen - ganze
     * Hoehe, ohne Ruecksicht auf Stein dazwischen ({@link AgentXray}). Dann
     * direkt hin: ist der naechste Block zu weit fuer eine Wegsuche am Stueck,
     * geht es in Etappen auf ihn zu.
     */
    private boolean xrayPlanen() {
        Set<Long> meiden = new HashSet<>(gesperrt);
        meiden.addAll(AgentWelt.reserviertVonAnderen(this));
        List<BlockPos> kandidaten = AgentXray.suchen(welt, fuesse, werte.xrayChunks(), auftrag, art, meiden);
        if (kandidaten.isEmpty()) {
            return false;
        }
        kandidaten = eigenerSektor(kandidaten);
        AgentPfad suche = new AgentPfad(welt, bausteine() > 0);
        for (int i = 0; i < Math.min(3, kandidaten.size()); i++) {
            BlockPos ziel = kandidaten.get(i);
            if (erreichbar(fuesse, ziel)) {
                zielBlock = ziel;
                return true;
            }
            if (ziel.distManhattan(fuesse) > 40) {
                break;
            }
            List<BlockPos> weg = suche.suchen(fuesse, n -> erreichbar(n, ziel), ziel, 10000, 64);
            if (weg != null) {
                pfadSetzen(weg);
                zielBlock = ziel;
                return true;
            }
            gesperrt.add(ziel.asLong());
        }
        // Weit weg: eine Etappe von 12 Bloecken in seine Richtung.
        BlockPos ziel = kandidaten.get(0);
        double abstand = Math.sqrt(ziel.distSqr(fuesse));
        if (abstand <= 12) {
            gesperrt.add(ziel.asLong());
            return false;
        }
        double f = 12 / abstand;
        BlockPos etappe = new BlockPos(
                fuesse.getX() + (int) Math.round((ziel.getX() - fuesse.getX()) * f),
                fuesse.getY() + (int) Math.round((ziel.getY() - fuesse.getY()) * f),
                fuesse.getZ() + (int) Math.round((ziel.getZ() - fuesse.getZ()) * f));
        List<BlockPos> weg = suche.suchen(fuesse, n -> n.distManhattan(etappe) <= 2, etappe, 8000, 40);
        if (weg != null && !weg.isEmpty()) {
            pfadSetzen(weg);
            return true;
        }
        gesperrt.add(ziel.asLong());
        return false;
    }

    /** Nichts in Sicht: weiterziehen - beim Erz in die beste Hoehe, beim Holz ueber die Oberflaeche. */
    private void erkunden() {
        if (auftrag == Auftrag.BAUER) {
            // Nichts reif: zurueck zur Feldmitte und warten, bis etwas nachwaechst.
            if (fuesse.distManhattan(heimat) > 3) {
                BlockPos h = heimat;
                List<BlockPos> weg = new AgentPfad(welt, false, false)
                        .suchen(fuesse, n -> n.distManhattan(h) <= 2, h, 6000, 48);
                if (weg != null && !weg.isEmpty()) {
                    pfadSetzen(weg);
                    return;
                }
            }
            planPause = 40;
            return;
        }
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
        bewegungStarten(nach, schrittTicks(seitlich && dy != 0));
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
        abbauDauer = abbauZeit(s, pos);
        koerper.setItemSlot(EquipmentSlot.MAINHAND,
                (auftrag == Auftrag.BAUER ? AgentBloecke.HACKE : AgentBloecke.werkzeug(s)).copy());
        anschauen(Vec3.atCenterOf(pos));
        AgentFassung.schwingen(koerper);
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
            AgentFassung.schwingen(koerper);
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
        AgentFassung.schwingen(koerper);
        if (ziel) {
            abgebaut++;
        }
        for (ItemStack stapel : beute) {
            einlagern(stapel, ziel);
        }
        if (ziel && auftrag == Auftrag.BAUER) {
            neuPflanzen(abbauPos, s);
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
        boolean behalten = vomZiel || auftrag == Auftrag.STEIN || auftrag == Auftrag.BAUER
                || AgentBloecke.istBaustein(stapel) && bausteine() < MAX_BAUSTEINE;
        if (!behalten) {
            return;
        }
        ItemStack rest = lager.addItem(stapel);
        if (!rest.isEmpty() && !voll) {
            voll = true;
            ServerPlayer spieler = welt.getServer().getPlayerList().getPlayer(besitzer);
            if (spieler != null) {
                AgentWelt.melden(spieler, ChatFormatting.YELLOW, titel() + (AgentWelt.kiste(besitzer) != null
                        ? ": Inventar voll - ich bringe alles in die Sammelkiste."
                        : ": Inventar voll - ich komme zurueck."));
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
                AgentFassung.schwingen(koerper);
                welt.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, 1f);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------- Sammelkiste

    /** Lager voll: mit Sammelkiste dorthin, sonst zurueck zum Spieler. */
    private void vollGeworden() {
        AgentWelt.WeltOrt kiste = AgentWelt.kiste(besitzer);
        if (kiste == null || !AgentKiste.istKiste(kiste.welt(), kiste.pos())) {
            zurueckrufen();
            return;
        }
        abbauAbbrechen();
        pfad = null;
        zielBlock = null;
        arbeitsPlatz = fuesse;
        arbeitsWelt = welt;
        kisteTicks = 0;
        zustand = Zustand.KISTE;
    }

    /** Was der Agent beim Abladen behaelt: Bausteine fuer Bruecken (ausser beim Stein-Agenten - der sammelt sie). */
    private boolean behaeltBeimAbladen(ItemStack stapel) {
        return auftrag != Auftrag.STEIN && AgentBloecke.istBaustein(stapel);
    }

    private void zurKiste() {
        AgentWelt.WeltOrt kiste = AgentWelt.kiste(besitzer);
        if (kiste == null) {
            zustand = Zustand.ZURUECK;
            zurueckTicks = 0;
            return;
        }
        kisteTicks++;
        BlockPos ziel = kiste.pos();
        boolean andereWelt = kiste.welt() != welt;
        double abstand = andereWelt ? Double.MAX_VALUE : Math.sqrt(fuesse.distSqr(ziel));
        if (!andereWelt && abstand <= 2.5) {
            anschauen(Vec3.atCenterOf(ziel));
            AgentFassung.schwingen(koerper);
            ServerPlayer spieler = welt.getServer().getPlayerList().getPlayer(besitzer);
            int bewegt = AgentKiste.einlagern(welt, ziel, lager, this::behaeltBeimAbladen);
            if (bewegt < 0) {
                if (spieler != null) {
                    AgentWelt.melden(spieler, ChatFormatting.YELLOW, titel() + ": Die Sammelkiste ist weg - ich komme zu dir.");
                }
                zustand = Zustand.ZURUECK;
                zurueckTicks = 0;
                return;
            }
            geliefert += bewegt;
            welt.playSound(null, ziel, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.6f, 1f);
            for (int i = 0; i < lager.getContainerSize(); i++) {
                ItemStack rest = lager.getItem(i);
                if (!rest.isEmpty() && !behaeltBeimAbladen(rest)) {
                    if (spieler != null) {
                        AgentWelt.melden(spieler, ChatFormatting.YELLOW, titel() + ": Die Sammelkiste ist voll - ich komme zu dir.");
                    }
                    zustand = Zustand.ZURUECK;
                    zurueckTicks = 0;
                    return;
                }
            }
            voll = false;
            // Zurueck an die Arbeit, genau dorthin, wo er aufgehoert hat.
            zustand = Zustand.ARBEITEN;
            pfad = null;
            planPause = 0;
            if (arbeitsPlatz != null && arbeitsWelt != null) {
                teleportierenNach(arbeitsWelt, arbeitsPlatz);
            }
            return;
        }
        if (andereWelt || abstand > NAH_GENUG || kisteTicks > 600) {
            teleportierenNach(kiste.welt(), ziel);
            return;
        }
        if (pfad == null || pfadIndex >= pfad.size() || kisteTicks % 40 == 0) {
            List<BlockPos> weg = new AgentPfad(welt, bausteine() > 0)
                    .suchen(fuesse, n -> n.distManhattan(ziel) <= 2, ziel, 8000, 64);
            if (weg == null) {
                teleportierenNach(kiste.welt(), ziel);
                return;
            }
            pfadSetzen(weg);
            if (weg.isEmpty()) {
                return;
            }
        }
        schritt();
    }

    // ----------------------------------------------------------------- Farm

    /** Frisch geerntet: dieselbe Pflanze wieder setzen, mit Saat aus dem Lager. */
    private void neuPflanzen(BlockPos pos, BlockState alt) {
        BlockState neu = alt.getBlock().defaultBlockState();
        if (!welt.getBlockState(pos).isAir() || !neu.canSurvive(welt, pos)) {
            return;
        }
        net.minecraft.world.item.Item saat = alt.getBlock().asItem();
        for (int i = 0; i < lager.getContainerSize(); i++) {
            ItemStack stapel = lager.getItem(i);
            if (stapel.is(saat)) {
                stapel.shrink(1);
                lager.setChanged();
                welt.setBlockAndUpdate(pos, neu);
                welt.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8f, 1f);
                return;
            }
        }
    }

    // --------------------------------------------------------------- Tunnel

    /** Eine Zelle des Tunnels: schritt nach vorn, seite nach rechts, hoch nach oben (0 = Boden). */
    private BlockPos tunnelZelle(int schritt, int seite, int hoch) {
        return heimat.offset(tunnelDx * schritt - tunnelDz * seite, hoch, tunnelDz * schritt + tunnelDx * seite);
    }

    private int[] seiten() {
        return tunnelBreite == 1 ? new int[] {0} : tunnelBreite == 2 ? new int[] {0, 1} : new int[] {-1, 0, 1};
    }

    private List<BlockPos> scheibe(int schritt) {
        List<BlockPos> zellen = new ArrayList<>();
        for (int hoch = 0; hoch < tunnelHoehe; hoch++) {
            for (int seite : seiten()) {
                zellen.add(tunnelZelle(schritt, seite, hoch));
            }
        }
        return zellen;
    }

    /**
     * Scheibe fuer Scheibe: erst Lava und Wasser ringsum abdichten, dann die
     * naechste Scheibe freigraben, Erze aus den Waenden mitnehmen, einen Schritt
     * vor - und alle acht Bloecke eine Fackel.
     */
    private void tunnelArbeiten() {
        if (voll) {
            vollGeworden();
            return;
        }
        if (pfad != null && pfadIndex < pfad.size()) {
            schritt();
            return;
        }
        pfad = null;
        BlockPos stand = tunnelZelle(tunnelSchritt, 0, 0);
        if (!fuesse.equals(stand)) {
            List<BlockPos> weg = new AgentPfad(welt, bausteine() > 0)
                    .suchen(fuesse, n -> n.equals(stand), stand, 6000, 64);
            if (weg != null && !weg.isEmpty()) {
                pfadSetzen(weg);
                return;
            }
            for (BlockPos zelle : new BlockPos[] {stand, stand.above()}) {
                BlockState zs = welt.getBlockState(zelle);
                if (!AgentBloecke.frei(welt, zelle, zs) && AgentBloecke.abbaubar(welt, zelle, zs)
                        && fuesse.distManhattan(zelle) <= 4) {
                    abbauStarten(zelle);
                    return;
                }
            }
            koerper.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, koerper.getYRot(), 0);
            fuesse = stand;
            return;
        }
        if (tunnelSchritt >= Math.max(1, werte.zahl())) {
            tunnelFertig(null);
            return;
        }
        int naechste = tunnelSchritt + 1;
        List<BlockPos> vorn = scheibe(naechste);
        Set<BlockPos> offen = new HashSet<>(vorn);
        offen.addAll(scheibe(tunnelSchritt));
        // 1. Abdichten: Fluessigkeit ringsum und in der Scheibe selbst
        for (BlockPos zelle : vorn) {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos n = zelle.relative(d);
                if (offen.contains(n)) {
                    continue;
                }
                BlockState ns = welt.getBlockState(n);
                if (!ns.getFluidState().isEmpty() && ns.canBeReplaced()) {
                    if (!bausteinSetzen(n)) {
                        tunnelFertig("Mir sind die Bausteine zum Abdichten ausgegangen");
                    }
                    return;
                }
            }
            BlockState zs = welt.getBlockState(zelle);
            if (!zs.getFluidState().isEmpty() && zs.canBeReplaced()) {
                if (!bausteinSetzen(zelle)) {
                    tunnelFertig("Mir sind die Bausteine zum Abdichten ausgegangen");
                }
                return;
            }
        }
        // 2. Freigraben (herabfallender Kies wird einfach nochmal abgebaut)
        for (BlockPos zelle : vorn) {
            BlockState zs = welt.getBlockState(zelle);
            if (AgentBloecke.frei(welt, zelle, zs)) {
                continue;
            }
            if (++tunnelVersuche > 400) {
                tunnelFertig("Hier komme ich nicht weiter");
                return;
            }
            if (AgentBloecke.abbaubar(welt, zelle, zs)) {
                abbauStarten(zelle);
                return;
            }
            tunnelFertig("Ein Block, den ich nicht abbauen kann (z. B. Grundgestein)");
            return;
        }
        // 3. Erze aus den Waenden der Scheibe, in der er steht
        Set<BlockPos> hier = new HashSet<>(scheibe(tunnelSchritt));
        for (BlockPos zelle : hier) {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos n = zelle.relative(d);
                if (hier.contains(n) || offen.contains(n)) {
                    continue;
                }
                BlockState ns = welt.getBlockState(n);
                if (AgentBloecke.istZiel(ns, auftrag, art) && AgentBloecke.abbaubar(welt, n, ns)) {
                    abbauStarten(n);
                    return;
                }
            }
        }
        // 4. Boden pruefen, einen Schritt vor
        BlockPos ziel = tunnelZelle(naechste, 0, 0);
        BlockPos unten = ziel.below();
        if (!AgentBloecke.traegt(welt, unten, welt.getBlockState(unten)) && !bausteinSetzen(unten)) {
            tunnelFertig("Mir sind die Bausteine fuer den Boden ausgegangen");
            return;
        }
        tunnelVersuche = 0;
        bewegungStarten(ziel, schrittTicks(false));
        tunnelSchritt = naechste;
        if (tunnelSchritt % 8 == 0) {
            fackelSetzen(tunnelSchritt);
        }
    }

    private void fackelSetzen(int schritt) {
        int[] seiten = seiten();
        BlockPos p = tunnelBreite >= 2 ? tunnelZelle(schritt, seiten[seiten.length - 1], 0)
                : tunnelZelle(schritt - 1, 0, 0);
        if (welt.getBlockState(p).isAir() && AgentBloecke.traegt(welt, p.below(), welt.getBlockState(p.below()))) {
            welt.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState());
            welt.playSound(null, p, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
    }

    /** Fertig (grund == null) oder abgebrochen: Bescheid geben und mit der Beute zurueck. */
    private void tunnelFertig(String grund) {
        ServerPlayer spieler = welt.getServer().getPlayerList().getPlayer(besitzer);
        if (spieler != null) {
            AgentWelt.melden(spieler, grund == null ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                    titel() + (grund == null ? ": Tunnel fertig - " : ": " + grund + " - Tunnel endet nach ")
                            + tunnelSchritt + " Bloecken, " + abgebaut + " Erze gefunden.");
        }
        zurueckrufen();
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
        teleportierenNach((ServerLevel) spieler.level(), spieler.blockPosition());
    }

    private void teleportierenNach(ServerLevel zielWelt, BlockPos um) {
        BlockPos platz = sichererPlatzBei(zielWelt, um, nummer);
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
    static BlockPos sichererPlatzBei(ServerLevel welt, BlockPos mitte) {
        return sichererPlatzBei(welt, mitte, 1);
    }

    /**
     * Wie oben, aber jede Nummer faengt an einer anderen Stelle des Rings an.
     * Sonst erscheinen mehrere Agenten derselben Art exakt uebereinander - und
     * sehen aus wie einer.
     */
    static BlockPos sichererPlatzBei(ServerLevel welt, BlockPos mitte, int nummer) {
        int[][] ring = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}, {1, 1}, {-1, -1}};
        int[][] versuche = new int[ring.length][];
        int start = Math.floorMod(nummer - 1, ring.length);
        for (int i = 0; i < ring.length; i++) {
            versuche[i] = ring[(start + i) % ring.length];
        }
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
        AgentFassung.schwingen(koerper);
    }

    /** Ohne Umweg: alles direkt ins Inventar (Welt schliesst, Agent weg). Was nicht passt, faellt vor die Fuesse. */
    @Override
    public void notfallUebergabe(MinecraftServer server) {
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

    @Override
    public void aufraeumen() {
        abbauAbbrechen();
        chunksFreigeben();
        if (koerper != null && !koerper.isRemoved()) {
            koerper.discard();
        }
    }

    // ------------------------------------------------------------- Kleinkram

    /** Die Chunks um den Agenten geladen halten - nur die, die wir selbst erzwungen haben, geben wir wieder frei. */
    private void chunksHalten() {
        Set<Long> gewuenscht = new HashSet<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                gewuenscht.add(AgentFassung.chunk(fuesse, dx, dz));
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
        String text = titel() + (werte.xray() ? " [X-Ray]" : "") + (abgebaut > 0 ? " · " + abgebaut : "");
        if (zustand == Zustand.ZURUECK || zustand == Zustand.ABLIEFERN) {
            text += " → zurueck";
        } else if (zustand == Zustand.KISTE) {
            text += " → Kiste";
        } else if (auftrag == Auftrag.TUNNEL) {
            text += " · " + tunnelSchritt + "/" + werte.zahl();
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
