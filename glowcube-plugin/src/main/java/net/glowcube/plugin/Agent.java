package net.glowcube.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Ein Agent auf dem Server - dieselbe Logik wie im Client (Agent.java dort):
 * Arbeiten (Ziel suchen, Weg planen, graben, abbauen, einlagern), Zurueck
 * (laufen/graben oder teleportieren) und Abliefern (Beute zuwerfen). Der
 * Koerper ist ein Mannequin in Spielergestalt, den das Plugin Tick fuer Tick
 * selbst bewegt.
 */
final class Agent implements AgentArbeiter {
    private enum Zustand { ARBEITEN, KISTE, ZURUECK, ABLIEFERN, FERTIG }

    private static final int SCHRITT_TICKS = 4;
    private static final int SUCH_WEITE = 20;
    private static final int SUCH_HOEHE = 12;
    private static final int NAH_GENUG = 48;
    private static final int MAX_BAUSTEINE = 64;

    private final GlowCubeAgentPlugin plugin;
    private final UUID besitzer;
    private final Auftrag auftrag;
    private final int nummer;
    private final String art;
    private final Inventory lager = Bukkit.createInventory(null, 36);
    private Werte werte;

    private World welt;
    private LivingEntity koerper;
    private Zustand zustand = Zustand.ARBEITEN;

    private Pos fuesse;
    private List<Pos> pfad;
    private int pfadIndex;
    private Location bewegVon;
    private Location bewegNach;
    private int bewegTick;
    private int bewegDauer;
    private Pos turmBlock;

    private Pos abbauPos;
    private int abbauFortschritt;
    private int abbauDauer;

    private Pos zielBlock;
    private int planPause;
    private int richtung;
    private final Set<Long> gesperrt = new HashSet<>();
    private int gesperrtAlter;

    // X-Ray: die letzte Suche und wo sie gemacht wurde.
    private CompletableFuture<List<Pos>> xraySuche;
    private List<Pos> xrayTreffer = new ArrayList<>();
    private Pos xrayOrt;
    private int xrayAlter;

    private int zurueckTicks;
    private int wurfPause;

    // Startpunkt (Farm: Feldmitte, Tunnel: Tunnelanfang) und Arbeitsplatz vor dem Kistengang
    private Pos heimat;
    private Pos arbeitsPlatz;
    private World arbeitsWelt;
    private int kisteTicks;

    // Tunnel
    private int tunnelDx;
    private int tunnelDz;
    private int tunnelBreite = 3;
    private int tunnelHoehe = 3;
    private int tunnelSchritt;
    private int tunnelVersuche;

    private final Set<Long> chunks = new HashSet<>();
    private int ticks;
    private int abgebaut;
    private int geliefert;
    private boolean voll;

    private Agent(GlowCubeAgentPlugin plugin, UUID besitzer, Auftrag auftrag, int nummer, String art, Werte werte) {
        this.plugin = plugin;
        this.besitzer = besitzer;
        this.auftrag = auftrag;
        this.nummer = nummer;
        this.art = art;
        this.werte = werte;
    }

    static Agent erschaffen(GlowCubeAgentPlugin plugin, Player spieler, Auftrag auftrag, int nummer, String art,
                            Werte werte) {
        Agent agent = new Agent(plugin, spieler.getUniqueId(), auftrag, nummer, art, werte);
        int blick = Math.floorMod(Math.round(spieler.getLocation().getYaw() / 90f), 4);
        // Mehrere Agenten schwaermen in verschiedene Richtungen aus.
        agent.richtung = auftrag == Auftrag.TUNNEL ? blick : (blick + nummer - 1) % 4;
        World welt = spieler.getWorld();
        Pos start = Pos.von(spieler.getLocation());
        // Einsatzort: dort arbeiten, statt beim Spieler.
        Location ort = plugin.einsatzort(spieler.getUniqueId());
        if (ort != null && ort.getWorld() != null) {
            welt = ort.getWorld();
            start = Pos.von(ort);
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
            agent.lager.addItem(new ItemStack(Material.COBBLESTONE, 64));
        }
        Pos platz = sichererPlatzBei(welt, start, nummer);
        if (!agent.koerperBauen(welt, platz)) {
            return null;
        }
        agent.effekt(Particle.PORTAL, 40);
        return agent;
    }

    private boolean koerperBauen(World neueWelt, Pos platz) {
        Location ort = platz.fuesse(neueWelt);
        ort.setYaw(richtung * 90f);
        LivingEntity neu;
        try {
            neu = (LivingEntity) neueWelt.spawnEntity(ort, EntityType.MANNEQUIN);
        } catch (RuntimeException fehler) {
            plugin.getLogger().warning("Agent konnte nicht erscheinen: " + fehler);
            return false;
        }
        neu.setGravity(false);
        neu.setInvulnerable(true);
        neu.setPersistent(false);
        neu.setCustomNameVisible(true);
        neu.setCollidable(false);
        neu.setGlowing(werte.leuchten());
        if (neu.getEquipment() != null) {
            neu.getEquipment().setItemInMainHand((auftrag == Auftrag.HOLZ ? Bloecke.AXT
                    : auftrag == Auftrag.BAUER ? Bloecke.HACKE : Bloecke.SPITZHACKE).clone());
        }
        if (koerper != null && koerper.isValid()) {
            koerper.remove();
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
    public org.bukkit.entity.Entity koerper() {
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
        return Kiste.anzahl(lager);
    }

    @Override
    public long reservierung() {
        return zielBlock != null ? zielBlock.schluessel() : abbauPos != null ? abbauPos.schluessel() : Long.MIN_VALUE;
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
        String name = auftrag.anzeigename + " #" + nummer;
        if (auftrag == Auftrag.ERZ && !art.isEmpty() && !art.equals("Alle") || auftrag == Auftrag.TUNNEL) {
            name += " (" + art + ")";
        }
        return name;
    }

    @Override
    public void einstellen(Werte neu) {
        werte = neu;
        if (koerper != null) {
            koerper.setGlowing(neu.leuchten());
        }
        if (abbauPos != null) {
            abbauDauer = abbauZeit(abbauPos.block(welt));
        }
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

    private int schrittTicks(boolean stufe) {
        int basis = (int) Math.max(1, Math.round(SCHRITT_TICKS / Math.max(1.0, werte.tempo())));
        return stufe ? basis + 1 : basis;
    }

    private int abbauZeit(Block b) {
        return Math.max(1, (int) Math.round(Bloecke.abbauTicks(b) / Math.max(1.0, werte.abbauTempo())));
    }

    // ---------------------------------------------------------------- Tick

    @Override
    public void tick() {
        if (zustand == Zustand.FERTIG) {
            return;
        }
        ticks++;
        if (koerper == null || !koerper.isValid()) {
            notfallUebergabe();
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
        Player spieler = Bukkit.getPlayer(besitzer);

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
        Location mitte = fuesse.fuesse(welt);
        if (koerper.getLocation().distanceSquared(mitte) > 0.01) {
            mitte.setYaw(koerper.getLocation().getYaw());
            mitte.setPitch(koerper.getLocation().getPitch());
            koerper.teleport(mitte);
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
            if (Bloecke.istZiel(zielBlock.block(welt), auftrag, art) && erreichbar(fuesse, zielBlock)) {
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
        if (werte.xray()) {
            Boolean ergebnis = xrayPlanen();
            if (ergebnis == null) {
                return; // Suche laeuft noch
            }
            if (ergebnis) {
                return;
            }
        } else if (nahPlanen()) {
            return;
        }
        erkunden();
    }

    /**
     * Mehrere Agenten derselben Art teilen sich die Umgebung wie eine Torte
     * um die Heimat; jeder nimmt zuerst die Ziele in seinem Stueck. Ist es
     * leer, hilft er bei den anderen aus.
     */
    private List<Pos> eigenerSektor(List<Pos> kandidaten) {
        int[] rang = plugin.rang(this);
        if (rang[1] <= 1) {
            return kandidaten;
        }
        double breite = 2 * Math.PI / rang[1];
        double von = rang[0] * breite;
        List<Pos> meine = new ArrayList<>();
        for (Pos p : kandidaten) {
            double winkel = Math.atan2(p.z() - heimat.z(), p.x() - heimat.x());
            winkel = (winkel + 2 * Math.PI) % (2 * Math.PI);
            if (winkel >= von && winkel < von + breite) {
                meine.add(p);
            }
        }
        return meine.isEmpty() ? kandidaten : meine;
    }

    private boolean nahPlanen() {
        List<Pos> kandidaten = new ArrayList<>();
        Set<Long> belegt = plugin.reserviertVonAnderen(this);
        boolean feld = auftrag == Auftrag.BAUER;
        Pos mitte = feld ? heimat : fuesse;
        int weite = feld ? Math.max(4, werte.zahl()) : SUCH_WEITE;
        int hoehe = feld ? 6 : SUCH_HOEHE;
        for (int dy = -hoehe; dy <= hoehe; dy++) {
            for (int dx = -weite; dx <= weite; dx++) {
                for (int dz = -weite; dz <= weite; dz++) {
                    Pos p = mitte.plus(dx, dy, dz);
                    if (!Bloecke.geladen(welt, p) || gesperrt.contains(p.schluessel()) || belegt.contains(p.schluessel())) {
                        continue;
                    }
                    if (feld && dx * dx + dz * dz > weite * weite) {
                        continue;
                    }
                    if (Bloecke.istZiel(p.block(welt), auftrag, art)) {
                        kandidaten.add(p);
                    }
                }
            }
        }
        kandidaten = eigenerSektor(kandidaten);
        kandidaten.sort((a, b) -> Double.compare(a.abstandQ(fuesse), b.abstandQ(fuesse)));
        return aufZiele(kandidaten, 4, 6000, 48);
    }

    /** X-Ray: null = Suche laeuft noch, true = Weg geplant, false = nichts gefunden. */
    private Boolean xrayPlanen() {
        if (xraySuche != null) {
            if (!xraySuche.isDone()) {
                return null;
            }
            try {
                xrayTreffer = new ArrayList<>(xraySuche.join());
            } catch (RuntimeException fehler) {
                xrayTreffer = new ArrayList<>();
            }
            xraySuche = null;
            xrayOrt = fuesse;
            xrayAlter = 0;
        }
        // Veraltet (weit gegangen, lange her) oder leer: neu suchen.
        Set<Long> belegt = plugin.reserviertVonAnderen(this);
        xrayTreffer.removeIf(p -> gesperrt.contains(p.schluessel()) || belegt.contains(p.schluessel())
                || Bloecke.geladen(welt, p) && !Bloecke.istZiel(p.block(welt).getType(), auftrag, art));
        xrayAlter++;
        if (xrayOrt == null || xrayOrt.manhattan(fuesse) > 24 || xrayAlter > 400
                || xrayTreffer.isEmpty() && xrayAlter > 40) {
            xraySuche = Xray.suchen(welt, fuesse, werte.xrayChunks(), auftrag, art);
            return null;
        }
        if (xrayTreffer.isEmpty()) {
            return false;
        }
        List<Pos> sortiert = eigenerSektor(new ArrayList<>(xrayTreffer));
        sortiert.sort((a, b) -> Double.compare(a.abstandQ(fuesse), b.abstandQ(fuesse)));
        if (aufZiele(sortiert, 3, 10000, 64)) {
            return true;
        }
        // Weit weg: in Etappen von 12 Bloecken direkt drauf zu.
        Pos ziel = sortiert.get(0);
        double abstand = Math.sqrt(ziel.abstandQ(fuesse));
        if (abstand <= 12) {
            gesperrt.add(ziel.schluessel());
            return false;
        }
        double f = 12 / abstand;
        Pos etappe = new Pos(
                fuesse.x() + (int) Math.round((ziel.x() - fuesse.x()) * f),
                fuesse.y() + (int) Math.round((ziel.y() - fuesse.y()) * f),
                fuesse.z() + (int) Math.round((ziel.z() - fuesse.z()) * f));
        List<Pos> weg = new Pfad(welt, bausteine() > 0).suchen(fuesse, n -> n.manhattan(etappe) <= 2, etappe, 8000, 40);
        if (weg != null && !weg.isEmpty()) {
            pfadSetzen(weg);
            return true;
        }
        gesperrt.add(ziel.schluessel());
        return false;
    }

    /** Die naechsten Ziele der Reihe nach - der erste, zu dem ein Weg fuehrt, gewinnt. */
    private boolean aufZiele(List<Pos> kandidaten, int anzahl, int maxKnoten, int maxAbstand) {
        Pfad suche = auftrag == Auftrag.BAUER ? new Pfad(welt, false, false) : new Pfad(welt, bausteine() > 0);
        for (int i = 0; i < Math.min(anzahl, kandidaten.size()); i++) {
            Pos ziel = kandidaten.get(i);
            if (erreichbar(fuesse, ziel)) {
                zielBlock = ziel;
                return true;
            }
            if (ziel.manhattan(fuesse) > 40) {
                break;
            }
            List<Pos> weg = suche.suchen(fuesse, n -> erreichbar(n, ziel), ziel, maxKnoten, maxAbstand);
            if (weg != null) {
                pfadSetzen(weg);
                zielBlock = ziel;
                return true;
            }
            gesperrt.add(ziel.schluessel());
        }
        return false;
    }

    private void erkunden() {
        if (auftrag == Auftrag.BAUER) {
            // Nichts reif: zurueck zur Feldmitte und warten, bis etwas nachwaechst.
            if (fuesse.manhattan(heimat) > 3) {
                Pos h = heimat;
                List<Pos> weg = new Pfad(welt, false, false).suchen(fuesse, n -> n.manhattan(h) <= 2, h, 6000, 48);
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
        Pos wegpunkt;
        if (auftrag == Auftrag.HOLZ) {
            int x = fuesse.x() + dx * 12;
            int z = fuesse.z() + dz * 12;
            wegpunkt = new Pos(x, welt.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, z);
        } else {
            int zielY = auftrag == Auftrag.STEIN ? fuesse.y() - 4 : Bloecke.besteHoehe(auftrag, art, welt);
            int y = fuesse.y() + Math.max(-6, Math.min(6, zielY - fuesse.y()));
            wegpunkt = new Pos(fuesse.x() + dx * 8, y, fuesse.z() + dz * 8);
        }
        Pos wp = wegpunkt;
        List<Pos> weg = new Pfad(welt, bausteine() > 0).suchen(fuesse, n -> n.manhattan(wp) <= 2, wp, 6000, 40);
        if (weg != null && !weg.isEmpty()) {
            pfadSetzen(weg);
        } else {
            richtung = (richtung + 1) % 4;
            planPause = 10;
        }
    }

    private static boolean erreichbar(Pos f, Pos ziel) {
        int dx = Math.abs(ziel.x() - f.x());
        int dz = Math.abs(ziel.z() - f.z());
        int dy = ziel.y() - f.y();
        if (dx > 1 || dz > 1 || dy < -1 || dy > 4) {
            return false;
        }
        return !(dx == 0 && dz == 0 && (dy == 0 || dy == 1));
    }

    // ------------------------------------------------------------ Bewegung

    private void pfadSetzen(List<Pos> weg) {
        pfad = weg;
        pfadIndex = 0;
    }

    private void schritt() {
        Pos nach = pfad.get(pfadIndex);
        int dy = nach.y() - fuesse.y();
        boolean seitlich = nach.x() != fuesse.x() || nach.z() != fuesse.z();

        List<Pos> frei = new ArrayList<>(3);
        if (seitlich && dy == 1) {
            frei.add(fuesse.hoch(2));
            frei.add(nach.hoch());
            frei.add(nach);
        } else if (seitlich && dy == -1) {
            frei.add(nach.hoch(2));
            frei.add(nach.hoch());
            frei.add(nach);
        } else if (seitlich) {
            frei.add(nach.hoch());
            frei.add(nach);
        } else if (dy == -1) {
            frei.add(nach);
        } else {
            frei.add(nach.hoch());
        }
        for (Pos zelle : frei) {
            Block b = zelle.block(welt);
            if (Bloecke.frei(b)) {
                continue;
            }
            if (Bloecke.abbaubar(b)) {
                abbauStarten(zelle);
            } else {
                pfad = null;
            }
            return;
        }

        turmBlock = null;
        if (!seitlich && dy == 1) {
            if (bausteine() == 0) {
                pfad = null;
                return;
            }
            turmBlock = fuesse;
        } else if (!Bloecke.traegt(nach.runter().block(welt)) && !bausteinSetzen(nach.runter())) {
            pfad = null;
            return;
        }
        bewegungStarten(nach, schrittTicks(seitlich && dy != 0));
        pfadIndex++;
    }

    private void bewegungStarten(Pos nach, int dauer) {
        bewegVon = koerper.getLocation();
        bewegNach = nach.fuesse(welt);
        bewegTick = 0;
        bewegDauer = dauer;
        Vector d = bewegNach.toVector().subtract(bewegVon.toVector());
        float gier = bewegVon.getYaw();
        if (d.getX() * d.getX() + d.getZ() * d.getZ() > 0.01) {
            gier = (float) Math.toDegrees(Math.atan2(d.getZ(), d.getX())) - 90f;
        }
        bewegNach.setYaw(gier);
        bewegNach.setPitch(0);
        fuesse = nach;
    }

    private void bewegen() {
        bewegTick++;
        double t = Math.min(1.0, bewegTick / (double) bewegDauer);
        Location p = new Location(welt,
                bewegVon.getX() + (bewegNach.getX() - bewegVon.getX()) * t,
                bewegVon.getY() + (bewegNach.getY() - bewegVon.getY()) * t,
                bewegVon.getZ() + (bewegNach.getZ() - bewegVon.getZ()) * t,
                bewegNach.getYaw(), bewegNach.getPitch());
        koerper.teleport(p);
        if (t >= 1.0) {
            bewegNach = null;
            if (turmBlock != null) {
                bausteinSetzen(turmBlock);
                turmBlock = null;
            }
        }
    }

    private boolean fallen() {
        Pos unten = fuesse.runter();
        if (!Bloecke.geladen(welt, unten)) {
            return false;
        }
        Block b = unten.block(welt);
        if (Bloecke.traegt(b)) {
            return false;
        }
        if (!Bloecke.frei(b)) {
            bausteinSetzen(unten);
            return false;
        }
        pfad = null;
        bewegungStarten(unten, 2);
        return true;
    }

    private void anschauen(Location punkt) {
        Location auge = koerper.getEyeLocation();
        Vector d = punkt.toVector().subtract(auge.toVector());
        float gier = (float) Math.toDegrees(Math.atan2(d.getZ(), d.getX())) - 90f;
        float neigung = (float) -Math.toDegrees(Math.atan2(d.getY(), Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ())));
        koerper.setRotation(gier, neigung);
    }

    // -------------------------------------------------------------- Abbauen

    private void abbauStarten(Pos pos) {
        Block b = pos.block(welt);
        abbauPos = pos;
        abbauFortschritt = 0;
        abbauDauer = abbauZeit(b);
        if (koerper.getEquipment() != null) {
            koerper.getEquipment().setItemInMainHand(
                    (auftrag == Auftrag.BAUER ? Bloecke.HACKE : Bloecke.werkzeug(b)).clone());
        }
        anschauen(pos.mitte(welt));
        koerper.swingMainHand();
    }

    private void abbauen() {
        Block b = abbauPos.block(welt);
        if (Bloecke.frei(b)) {
            abbauAbbrechen();
            return;
        }
        abbauFortschritt++;
        anschauen(abbauPos.mitte(welt));
        if (abbauFortschritt % 4 == 0) {
            koerper.swingMainHand();
        }
        risse(Math.min(0.9f, abbauFortschritt / (float) Math.max(1, abbauDauer)));
        if (abbauFortschritt < abbauDauer) {
            return;
        }
        boolean ziel = Bloecke.istZiel(b, auftrag, art);
        Material vorher = b.getType();
        ItemStack werkzeug = Bloecke.werkzeug(b);
        Collection<ItemStack> beute = b.getDrops(werkzeug, koerper);
        risse(0);
        welt.playEffect(b.getLocation(), Effect.STEP_SOUND, b.getBlockData());
        b.setType(Material.AIR, true);
        koerper.swingMainHand();
        if (ziel) {
            abgebaut++;
        }
        for (ItemStack stapel : beute) {
            einlagern(stapel, ziel);
        }
        if (ziel && auftrag == Auftrag.BAUER) {
            neuPflanzen(abbauPos, vorher);
        }
        abbauPos = null;
    }

    /** Risse im Block fuer alle Spieler in der Naehe. */
    private void risse(float fortschritt) {
        if (abbauPos == null) {
            return;
        }
        Location ort = abbauPos.mitte(welt);
        for (Player p : welt.getPlayers()) {
            if (p.getLocation().distanceSquared(ort) < 64 * 64) {
                p.sendBlockDamage(ort, fortschritt, koerper.getEntityId());
            }
        }
    }

    private void abbauAbbrechen() {
        if (abbauPos != null && koerper != null) {
            risse(0);
        }
        abbauPos = null;
    }

    private void einlagern(ItemStack stapel, boolean vomZiel) {
        if (stapel == null || stapel.getType().isAir()) {
            return;
        }
        boolean behalten = vomZiel || auftrag == Auftrag.STEIN || auftrag == Auftrag.BAUER
                || Bloecke.istBaustein(stapel) && bausteine() < MAX_BAUSTEINE;
        if (!behalten) {
            return;
        }
        Map<Integer, ItemStack> rest = lager.addItem(stapel);
        if (!rest.isEmpty() && !voll) {
            voll = true;
            Player spieler = Bukkit.getPlayer(besitzer);
            if (spieler != null) {
                plugin.melden(spieler, NamedTextColor.YELLOW, titel() + (plugin.kiste(besitzer) != null
                        ? ": Inventar voll - ich bringe alles in die Sammelkiste."
                        : ": Inventar voll - ich komme zurueck."));
            }
        }
    }

    private int bausteine() {
        int n = 0;
        for (ItemStack s : lager.getContents()) {
            if (Bloecke.istBaustein(s)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private boolean bausteinSetzen(Pos pos) {
        Block dort = pos.block(welt);
        if (!dort.isReplaceable()) {
            return false;
        }
        ItemStack[] inhalt = lager.getContents();
        for (int i = 0; i < inhalt.length; i++) {
            ItemStack s = inhalt[i];
            if (Bloecke.istBaustein(s) && s.getType().isBlock()) {
                dort.setType(s.getType(), true);
                s.setAmount(s.getAmount() - 1);
                lager.setItem(i, s.getAmount() > 0 ? s : null);
                anschauen(pos.mitte(welt));
                koerper.swingMainHand();
                welt.playSound(pos.mitte(welt), Sound.BLOCK_STONE_PLACE, 1f, 1f);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------- Sammelkiste

    private void vollGeworden() {
        Location kiste = plugin.kiste(besitzer);
        if (kiste == null || kiste.getWorld() == null || !Kiste.istKiste(kiste.getBlock())) {
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

    /** Beim Abladen behalten: Bausteine fuer Bruecken (ausser beim Stein-Agenten - der sammelt sie). */
    private boolean behaeltBeimAbladen(ItemStack stapel) {
        return auftrag != Auftrag.STEIN && Bloecke.istBaustein(stapel);
    }

    private void zurKiste() {
        Location kiste = plugin.kiste(besitzer);
        if (kiste == null || kiste.getWorld() == null) {
            zustand = Zustand.ZURUECK;
            zurueckTicks = 0;
            return;
        }
        kisteTicks++;
        Pos ziel = Pos.von(kiste);
        boolean andereWelt = !kiste.getWorld().equals(welt);
        double abstand = andereWelt ? Double.MAX_VALUE : Math.sqrt(fuesse.abstandQ(ziel));
        Player spieler = Bukkit.getPlayer(besitzer);
        if (!andereWelt && abstand <= 2.5) {
            anschauen(ziel.mitte(welt));
            koerper.swingMainHand();
            int bewegt = Kiste.einlagern(kiste.getBlock(), lager, this::behaeltBeimAbladen);
            if (bewegt < 0) {
                if (spieler != null) {
                    plugin.melden(spieler, NamedTextColor.YELLOW, titel() + ": Die Sammelkiste ist weg - ich komme zu dir.");
                }
                zustand = Zustand.ZURUECK;
                zurueckTicks = 0;
                return;
            }
            geliefert += bewegt;
            welt.playSound(kiste, Sound.BLOCK_CHEST_CLOSE, 0.6f, 1f);
            for (ItemStack rest : lager.getContents()) {
                if (rest != null && !rest.getType().isAir() && !behaeltBeimAbladen(rest)) {
                    if (spieler != null) {
                        plugin.melden(spieler, NamedTextColor.YELLOW, titel() + ": Die Sammelkiste ist voll - ich komme zu dir.");
                    }
                    zustand = Zustand.ZURUECK;
                    zurueckTicks = 0;
                    return;
                }
            }
            voll = false;
            zustand = Zustand.ARBEITEN;
            pfad = null;
            planPause = 0;
            if (arbeitsPlatz != null && arbeitsWelt != null) {
                teleportierenNach(arbeitsWelt, arbeitsPlatz);
            }
            return;
        }
        if (andereWelt || abstand > NAH_GENUG || kisteTicks > 600) {
            teleportierenNach(kiste.getWorld(), ziel);
            return;
        }
        if (pfad == null || pfadIndex >= pfad.size() || kisteTicks % 40 == 0) {
            List<Pos> weg = new Pfad(welt, bausteine() > 0).suchen(fuesse, n -> n.manhattan(ziel) <= 2, ziel, 8000, 64);
            if (weg == null) {
                teleportierenNach(kiste.getWorld(), ziel);
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
    private void neuPflanzen(Pos pos, Material pflanze) {
        Material saat = switch (pflanze) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
        Block b = pos.block(welt);
        Material boden = pos.runter().block(welt).getType();
        boolean passt = pflanze == Material.NETHER_WART ? boden == Material.SOUL_SAND : boden == Material.FARMLAND;
        if (saat == null || !b.getType().isAir() || !passt || !lager.containsAtLeast(new ItemStack(saat), 1)) {
            return;
        }
        lager.removeItem(new ItemStack(saat, 1));
        b.setType(pflanze, true);
        welt.playSound(pos.mitte(welt), Sound.ITEM_CROP_PLANT, 0.8f, 1f);
    }

    // --------------------------------------------------------------- Tunnel

    private Pos tunnelZelle(int schritt, int seite, int hoch) {
        return heimat.plus(tunnelDx * schritt - tunnelDz * seite, hoch, tunnelDz * schritt + tunnelDx * seite);
    }

    private int[] seiten() {
        return tunnelBreite == 1 ? new int[] {0} : tunnelBreite == 2 ? new int[] {0, 1} : new int[] {-1, 0, 1};
    }

    private List<Pos> scheibe(int schritt) {
        List<Pos> zellen = new ArrayList<>();
        for (int hoch = 0; hoch < tunnelHoehe; hoch++) {
            for (int seite : seiten()) {
                zellen.add(tunnelZelle(schritt, seite, hoch));
            }
        }
        return zellen;
    }

    private static final org.bukkit.block.BlockFace[] ALLE_SEITEN = {org.bukkit.block.BlockFace.UP,
            org.bukkit.block.BlockFace.DOWN, org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST};

    private static Pos daneben(Pos p, org.bukkit.block.BlockFace f) {
        return p.plus(f.getModX(), f.getModY(), f.getModZ());
    }

    private static boolean fluessig(Block b) {
        return b.isLiquid();
    }

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
        Pos stand = tunnelZelle(tunnelSchritt, 0, 0);
        if (!fuesse.equals(stand)) {
            List<Pos> weg = new Pfad(welt, bausteine() > 0).suchen(fuesse, n -> n.equals(stand), stand, 6000, 64);
            if (weg != null && !weg.isEmpty()) {
                pfadSetzen(weg);
                return;
            }
            for (Pos zelle : new Pos[] {stand, stand.hoch()}) {
                Block zb = zelle.block(welt);
                if (!Bloecke.frei(zb) && Bloecke.abbaubar(zb) && fuesse.manhattan(zelle) <= 4) {
                    abbauStarten(zelle);
                    return;
                }
            }
            Location l = stand.fuesse(welt);
            l.setYaw(koerper.getLocation().getYaw());
            koerper.teleport(l);
            fuesse = stand;
            return;
        }
        if (tunnelSchritt >= Math.max(1, werte.zahl())) {
            tunnelFertig(null);
            return;
        }
        int naechste = tunnelSchritt + 1;
        List<Pos> vorn = scheibe(naechste);
        Set<Pos> offen = new HashSet<>(vorn);
        offen.addAll(scheibe(tunnelSchritt));
        for (Pos zelle : vorn) {
            for (org.bukkit.block.BlockFace f : ALLE_SEITEN) {
                Pos n = daneben(zelle, f);
                if (offen.contains(n)) {
                    continue;
                }
                if (fluessig(n.block(welt))) {
                    if (!bausteinSetzen(n)) {
                        tunnelFertig("Mir sind die Bausteine zum Abdichten ausgegangen");
                    }
                    return;
                }
            }
            if (fluessig(zelle.block(welt))) {
                if (!bausteinSetzen(zelle)) {
                    tunnelFertig("Mir sind die Bausteine zum Abdichten ausgegangen");
                }
                return;
            }
        }
        for (Pos zelle : vorn) {
            Block zb = zelle.block(welt);
            if (Bloecke.frei(zb)) {
                continue;
            }
            if (++tunnelVersuche > 400) {
                tunnelFertig("Hier komme ich nicht weiter");
                return;
            }
            if (Bloecke.abbaubar(zb)) {
                abbauStarten(zelle);
                return;
            }
            tunnelFertig("Ein Block, den ich nicht abbauen kann (z. B. Grundgestein)");
            return;
        }
        Set<Pos> hier = new HashSet<>(scheibe(tunnelSchritt));
        for (Pos zelle : hier) {
            for (org.bukkit.block.BlockFace f : ALLE_SEITEN) {
                Pos n = daneben(zelle, f);
                if (hier.contains(n) || offen.contains(n)) {
                    continue;
                }
                Block nb = n.block(welt);
                if (Bloecke.istZiel(nb, auftrag, art) && Bloecke.abbaubar(nb)) {
                    abbauStarten(n);
                    return;
                }
            }
        }
        Pos ziel = tunnelZelle(naechste, 0, 0);
        if (!Bloecke.traegt(ziel.runter().block(welt)) && !bausteinSetzen(ziel.runter())) {
            tunnelFertig("Mir sind die Bausteine fuer den Boden ausgegangen");
            return;
        }
        tunnelVersuche = 0;
        bewegungStarten(ziel, schrittTicks(false));
        tunnelSchritt = naechste;
        if (tunnelSchritt % 8 == 0) {
            int[] seiten = seiten();
            Pos p = tunnelBreite >= 2 ? tunnelZelle(tunnelSchritt, seiten[seiten.length - 1], 0)
                    : tunnelZelle(tunnelSchritt - 1, 0, 0);
            Block fb = p.block(welt);
            if (fb.getType().isAir() && Bloecke.traegt(p.runter().block(welt))) {
                fb.setType(Material.TORCH, true);
                welt.playSound(p.mitte(welt), Sound.BLOCK_WOOD_PLACE, 0.8f, 1.2f);
            }
        }
    }

    private void tunnelFertig(String grund) {
        Player spieler = Bukkit.getPlayer(besitzer);
        if (spieler != null) {
            plugin.melden(spieler, grund == null ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                    titel() + (grund == null ? ": Tunnel fertig - " : ": " + grund + " - Tunnel endet nach ")
                            + tunnelSchritt + " Bloecken, " + abgebaut + " Erze gefunden.");
        }
        zurueckrufen();
    }

    // ------------------------------------------------------------ Rueckweg

    private void zurueckkehren(Player spieler) {
        if (spieler == null) {
            return;
        }
        zurueckTicks++;
        Pos ziel = Pos.von(spieler.getLocation());
        boolean andereWelt = !spieler.getWorld().equals(welt);
        double abstand = andereWelt ? Double.MAX_VALUE : Math.sqrt(fuesse.abstandQ(ziel));
        if (!andereWelt && abstand <= 2.5) {
            zustand = Zustand.ABLIEFERN;
            pfad = null;
            return;
        }
        if (andereWelt || abstand > NAH_GENUG || zurueckTicks > 600) {
            teleportieren(spieler);
            zustand = Zustand.ABLIEFERN;
            return;
        }
        if (pfad == null || pfadIndex >= pfad.size() || zurueckTicks % 40 == 0) {
            List<Pos> weg = new Pfad(welt, bausteine() > 0).suchen(fuesse, n -> n.manhattan(ziel) <= 2, ziel, 8000, 64);
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

    private void teleportieren(Player spieler) {
        teleportierenNach(spieler.getWorld(), Pos.von(spieler.getLocation()));
    }

    private void teleportierenNach(World zielWelt, Pos um) {
        Pos platz = sichererPlatzBei(zielWelt, um, nummer);
        effekt(Particle.PORTAL, 40);
        welt.playSound(koerper.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        pfad = null;
        abbauAbbrechen();
        if (zielWelt.equals(welt)) {
            Location ort = platz.fuesse(welt);
            ort.setYaw(koerper.getLocation().getYaw());
            koerper.teleport(ort);
            fuesse = platz;
            chunksHalten();
        } else if (!koerperBauen(zielWelt, platz)) {
            return;
        }
        effekt(Particle.PORTAL, 40);
        welt.playSound(platz.fuesse(welt), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }

    static Pos sichererPlatzBei(World welt, Pos mitte) {
        return sichererPlatzBei(welt, mitte, 1);
    }

    /** Jede Nummer faengt an einer anderen Stelle des Rings an - sonst stehen mehrere Agenten uebereinander. */
    static Pos sichererPlatzBei(World welt, Pos mitte, int nummer) {
        int[][] ring = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}, {1, 1}, {-1, -1}};
        int[][] versuche = new int[ring.length][];
        int start = Math.floorMod(nummer - 1, ring.length);
        for (int i = 0; i < ring.length; i++) {
            versuche[i] = ring[(start + i) % ring.length];
        }
        for (int[] v : versuche) {
            for (int dy = 0; dy >= -2; dy--) {
                Pos p = mitte.plus(v[0], dy, v[1]);
                for (int hoch = 0; hoch <= 2; hoch++) {
                    Pos q = p.hoch(hoch);
                    if (Bloecke.frei(q.block(welt)) && Bloecke.frei(q.hoch().block(welt))
                            && Bloecke.traegt(q.runter().block(welt))) {
                        return q;
                    }
                }
            }
        }
        return mitte;
    }

    // ------------------------------------------------------------ Abliefern

    private void abliefern(Player spieler) {
        if (spieler == null) {
            return;
        }
        if (!spieler.getWorld().equals(welt) || Math.sqrt(fuesse.abstandQ(Pos.von(spieler.getLocation()))) > 5) {
            zustand = Zustand.ZURUECK;
            return;
        }
        anschauen(spieler.getEyeLocation());
        if (wurfPause-- > 0) {
            return;
        }
        wurfPause = 3;
        for (int i = 0; i < lager.getSize(); i++) {
            ItemStack stapel = lager.getItem(i);
            if (stapel == null || stapel.getType().isAir()) {
                continue;
            }
            lager.setItem(i, null);
            werfen(stapel, spieler);
            geliefert += stapel.getAmount();
            return;
        }
        plugin.melden(spieler, NamedTextColor.GREEN, titel() + " hat dir " + geliefert + " Items gebracht ("
                + abgebaut + " Bloecke abgebaut).");
        effekt(Particle.POOF, 20);
        welt.playSound(koerper.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.8f);
        koerper.remove();
        zustand = Zustand.FERTIG;
    }

    private void werfen(ItemStack stapel, Player spieler) {
        Location von = koerper.getEyeLocation().subtract(0, 0.3, 0);
        Vector zum = spieler.getEyeLocation().toVector().subtract(von.toVector());
        Vector schwung = zum.clone().normalize().multiply(Math.min(0.45, 0.12 * zum.length())).add(new Vector(0, 0.18, 0));
        welt.dropItem(von, stapel, (Item wurf) -> {
            wurf.setVelocity(schwung);
            wurf.setPickupDelay(8);
        });
        koerper.swingMainHand();
    }

    @Override
    public void notfallUebergabe() {
        Player spieler = Bukkit.getPlayer(besitzer);
        for (int i = 0; i < lager.getSize(); i++) {
            ItemStack stapel = lager.getItem(i);
            if (stapel == null || stapel.getType().isAir()) {
                continue;
            }
            lager.setItem(i, null);
            if (spieler != null) {
                for (ItemStack rest : spieler.getInventory().addItem(stapel).values()) {
                    spieler.getWorld().dropItem(spieler.getLocation(), rest);
                }
            } else if (welt != null && fuesse != null) {
                welt.dropItem(fuesse.fuesse(welt), stapel);
            }
        }
        zustand = Zustand.FERTIG;
    }

    @Override
    public void aufraeumen() {
        abbauAbbrechen();
        chunksFreigeben();
        if (koerper != null && koerper.isValid()) {
            koerper.remove();
        }
    }

    // ------------------------------------------------------------- Kleinkram

    private void chunksHalten() {
        int cx = fuesse.x() >> 4;
        int cz = fuesse.z() >> 4;
        Set<Long> gewuenscht = new HashSet<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                gewuenscht.add(((long) (cx + dx) << 32) | ((cz + dz) & 0xFFFFFFFFL));
            }
        }
        for (long c : new ArrayList<>(chunks)) {
            if (!gewuenscht.contains(c)) {
                welt.removePluginChunkTicket((int) (c >> 32), (int) c, plugin);
                chunks.remove(c);
            }
        }
        for (long c : gewuenscht) {
            if (chunks.add(c)) {
                welt.addPluginChunkTicket((int) (c >> 32), (int) c, plugin);
            }
        }
    }

    private void chunksFreigeben() {
        if (welt == null) {
            return;
        }
        for (long c : chunks) {
            welt.removePluginChunkTicket((int) (c >> 32), (int) c, plugin);
        }
        chunks.clear();
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
        koerper.customName(Component.text(text, NamedTextColor.AQUA));
    }

    private void effekt(Particle art, int anzahl) {
        if (koerper == null) {
            return;
        }
        Location l = koerper.getLocation().add(0, 1, 0);
        welt.spawnParticle(art, l, anzahl, 0.3, 0.6, 0.3, 0.05);
    }
}
