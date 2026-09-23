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
    private enum Zustand { ARBEITEN, ZURUECK, ABLIEFERN, FERTIG }

    private static final int SCHRITT_TICKS = 4;
    private static final int SUCH_WEITE = 20;
    private static final int SUCH_HOEHE = 12;
    private static final int NAH_GENUG = 48;
    private static final int MAX_BAUSTEINE = 64;

    private final GlowCubeAgentPlugin plugin;
    private final UUID besitzer;
    private final Auftrag auftrag;
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

    private final Set<Long> chunks = new HashSet<>();
    private int ticks;
    private int abgebaut;
    private int geliefert;
    private boolean voll;

    private Agent(GlowCubeAgentPlugin plugin, UUID besitzer, Auftrag auftrag, String art, Werte werte) {
        this.plugin = plugin;
        this.besitzer = besitzer;
        this.auftrag = auftrag;
        this.art = art;
        this.werte = werte;
    }

    static Agent erschaffen(GlowCubeAgentPlugin plugin, Player spieler, Auftrag auftrag, String art, Werte werte) {
        Agent agent = new Agent(plugin, spieler.getUniqueId(), auftrag, art, werte);
        agent.richtung = Math.floorMod(Math.round(spieler.getLocation().getYaw() / 90f), 4);
        Pos platz = sichererPlatzBei(spieler.getWorld(), Pos.von(spieler.getLocation()));
        if (!agent.koerperBauen(spieler.getWorld(), platz)) {
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
        if (neu.getEquipment() != null) {
            neu.getEquipment().setItemInMainHand(
                    (auftrag == Auftrag.HOLZ ? Bloecke.AXT : Bloecke.SPITZHACKE).clone());
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
    public boolean beimZurueckkehren() {
        return zustand == Zustand.ZURUECK || zustand == Zustand.ABLIEFERN;
    }

    @Override
    public boolean fertig() {
        return zustand == Zustand.FERTIG;
    }

    @Override
    public String titel() {
        return auftrag == Auftrag.ERZ && !art.isEmpty() && !art.equals("Alle")
                ? auftrag.anzeigename + " (" + art + ")" : auftrag.anzeigename;
    }

    @Override
    public void einstellen(Werte neu) {
        werte = neu;
        if (abbauPos != null) {
            abbauDauer = abbauZeit(abbauPos.block(welt));
        }
    }

    @Override
    public void zurueckrufen() {
        if (zustand == Zustand.ARBEITEN) {
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
            Material m = zielBlock.block(welt).getType();
            if (Bloecke.istZiel(m, auftrag, art) && erreichbar(fuesse, zielBlock)) {
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

    private boolean nahPlanen() {
        List<Pos> kandidaten = new ArrayList<>();
        for (int dy = -SUCH_HOEHE; dy <= SUCH_HOEHE; dy++) {
            for (int dx = -SUCH_WEITE; dx <= SUCH_WEITE; dx++) {
                for (int dz = -SUCH_WEITE; dz <= SUCH_WEITE; dz++) {
                    Pos p = fuesse.plus(dx, dy, dz);
                    if (!Bloecke.geladen(welt, p) || gesperrt.contains(p.schluessel())) {
                        continue;
                    }
                    if (Bloecke.istZiel(p.block(welt).getType(), auftrag, art)) {
                        kandidaten.add(p);
                    }
                }
            }
        }
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
        xrayTreffer.removeIf(p -> gesperrt.contains(p.schluessel())
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
        List<Pos> sortiert = new ArrayList<>(xrayTreffer);
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
        Pfad suche = new Pfad(welt, bausteine() > 0);
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
            koerper.getEquipment().setItemInMainHand(Bloecke.werkzeug(b).clone());
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
        boolean ziel = Bloecke.istZiel(b.getType(), auftrag, art);
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
        boolean behalten = vomZiel || auftrag == Auftrag.STEIN
                || Bloecke.istBaustein(stapel) && bausteine() < MAX_BAUSTEINE;
        if (!behalten) {
            return;
        }
        Map<Integer, ItemStack> rest = lager.addItem(stapel);
        if (!rest.isEmpty() && !voll) {
            voll = true;
            Player spieler = Bukkit.getPlayer(besitzer);
            if (spieler != null) {
                plugin.melden(spieler, NamedTextColor.YELLOW, titel() + ": Inventar voll - ich komme zurueck.");
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
        World zielWelt = spieler.getWorld();
        Pos platz = sichererPlatzBei(zielWelt, Pos.von(spieler.getLocation()));
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
        int[][] versuche = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {2, 2}, {-2, -2}};
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
