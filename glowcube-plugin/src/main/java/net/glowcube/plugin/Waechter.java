package net.glowcube.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * Guardian-Agent auf dem Server - dieselbe Logik wie im Client
 * (AgentWaechter): folgt dem Spieler, greift Monster im Umkreis, Mobs mit
 * dem Spieler als Ziel und den letzten Angreifer an; bei einem Herz nur noch
 * Schutz direkt am Spieler.
 */
final class Waechter implements AgentArbeiter {
    // Jaeger-Agent: derselbe Koerper und Kampf, aber Tiere statt Monster (siehe Client, AgentWaechter).
    private static final double JAGD_UMKREIS = 24;
    private static final double UMKREIS = 16;
    private static final double REICHWEITE = 3.0;
    private static final int ANGRIFF_PAUSE = 12;

    private final GlowCubeAgentPlugin plugin;
    private final UUID besitzer;
    private final Auftrag auftrag;
    private final int nummer;
    private final boolean jaeger;
    private final String ruestung;
    private final double schaden;
    private Werte werte;

    private World welt;
    private LivingEntity koerper;
    private Pos fuesse;

    private LivingEntity ziel;
    private int angriffPause;
    private int suchPause;
    private boolean schuetzen;
    private int kills;
    private boolean fertig;

    private final org.bukkit.inventory.Inventory lager = Bukkit.createInventory(null, 27);
    private Location beuteOrt;
    private int beuteTicks;
    private boolean abliefern;
    private int wurfPause;
    private int geliefert;

    private List<Pos> pfad;
    private int pfadIndex;
    private int planAlter;
    private Location bewegVon;
    private Location bewegNach;
    private int bewegTick;
    private int bewegDauer;
    private int ticks;

    private Waechter(GlowCubeAgentPlugin plugin, UUID besitzer, Auftrag auftrag, int nummer, String ruestung,
                     Werte werte) {
        this.plugin = plugin;
        this.besitzer = besitzer;
        this.auftrag = auftrag;
        this.nummer = nummer;
        this.jaeger = auftrag == Auftrag.JAEGER;
        this.ruestung = ruestung;
        this.werte = werte;
        this.schaden = jaeger ? 8 : switch (ruestung) {
            case "Eisen" -> 8;
            case "Netherite" -> 12;
            default -> 10;
        };
    }

    static Waechter erschaffen(GlowCubeAgentPlugin plugin, Player spieler, Auftrag auftrag, int nummer, String art,
                               Werte werte) {
        Waechter w = new Waechter(plugin, spieler.getUniqueId(), auftrag, nummer, art, werte);
        if (!w.koerperBauen(spieler.getWorld(), Agent.sichererPlatzBei(spieler.getWorld(), Pos.von(spieler.getLocation()), nummer))) {
            return null;
        }
        w.effekt(Particle.PORTAL, 40);
        return w;
    }

    private boolean koerperBauen(World neueWelt, Pos platz) {
        LivingEntity neu;
        try {
            neu = (LivingEntity) neueWelt.spawnEntity(platz.fuesse(neueWelt), EntityType.MANNEQUIN);
        } catch (RuntimeException fehler) {
            plugin.getLogger().warning("Guardian konnte nicht erscheinen: " + fehler);
            return false;
        }
        neu.setGravity(false);
        neu.setInvulnerable(true);
        neu.setPersistent(false);
        neu.setCustomNameVisible(true);
        neu.setCollidable(false);
        neu.setGlowing(werte.leuchten());
        ausruesten(neu.getEquipment());
        if (koerper != null && koerper.isValid()) {
            koerper.remove();
        }
        welt = neueWelt;
        koerper = neu;
        fuesse = platz;
        pfad = null;
        bewegNach = null;
        namenAktualisieren();
        return true;
    }

    private void ausruesten(EntityEquipment e) {
        if (e == null) {
            return;
        }
        if (jaeger) {
            e.setHelmet(new ItemStack(Material.LEATHER_HELMET));
            e.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            e.setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
            e.setBoots(new ItemStack(Material.LEATHER_BOOTS));
            e.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            return;
        }
        String m = switch (ruestung) {
            case "Eisen" -> "IRON";
            case "Netherite" -> "NETHERITE";
            default -> "DIAMOND";
        };
        e.setHelmet(new ItemStack(Material.valueOf(m + "_HELMET")));
        e.setChestplate(new ItemStack(Material.valueOf(m + "_CHESTPLATE")));
        e.setLeggings(new ItemStack(Material.valueOf(m + "_LEGGINGS")));
        e.setBoots(new ItemStack(Material.valueOf(m + "_BOOTS")));
        e.setItemInMainHand(new ItemStack(Material.valueOf(m + "_SWORD")));
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
    public String titel() {
        return auftrag.anzeigename + " #" + nummer + " (" + ruestung + ")";
    }

    @Override
    public Entity koerper() {
        return koerper;
    }

    @Override
    public String zustandText() {
        if (abliefern) {
            return "bringt die Beute";
        }
        if (schuetzen) {
            return "beschuetzt dich";
        }
        return ziel != null ? (jaeger ? "jagt" : "kaempft") : (jaeger ? "sucht Tiere" : "wacht");
    }

    @Override
    public int beute() {
        return jaeger ? Kiste.anzahl(lager) : kills;
    }

    @Override
    public boolean beimZurueckkehren() {
        return fertig || abliefern;
    }

    @Override
    public boolean fertig() {
        return fertig;
    }

    @Override
    public void zurueckrufen() {
        if (jaeger && Kiste.anzahl(lager) > 0 && koerper != null && koerper.isValid()) {
            abliefern = true;
            ziel = null;
            return;
        }
        effekt(Particle.POOF, 20);
        fertig = true;
    }

    @Override
    public void einstellen(Werte neu) {
        werte = neu;
        if (koerper != null) {
            koerper.setGlowing(neu.leuchten());
        }
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
            }
        }
        fertig = true;
    }

    @Override
    public void aufraeumen() {
        if (koerper != null && koerper.isValid()) {
            koerper.remove();
        }
    }

    // ---------------------------------------------------------------- Tick

    @Override
    public void tick() {
        if (fertig) {
            return;
        }
        ticks++;
        if (koerper == null || !koerper.isValid()) {
            fertig = true;
            return;
        }
        Player spieler = Bukkit.getPlayer(besitzer);
        if (spieler == null || spieler.isDead()) {
            return;
        }
        if (ticks % 20 == 0) {
            namenAktualisieren();
        }
        if (angriffPause > 0) {
            angriffPause--;
        }
        if (!spieler.getWorld().equals(welt) || koerper.getLocation().distanceSquared(spieler.getLocation()) > 32 * 32) {
            teleportieren(spieler);
            return;
        }
        double leben = spieler.getHealth();
        if (!jaeger && !schuetzen && leben <= 2.0) {
            schuetzen = true;
            ziel = null;
            pfad = null;
            plugin.melden(spieler, NamedTextColor.RED, "Guardian: Du bist fast tot - ich bleibe bei dir!");
        } else if (schuetzen && leben >= 6.0) {
            schuetzen = false;
        }
        if (bewegNach != null) {
            bewegen();
        }
        if (jaeger) {
            if (abliefern) {
                abliefern(spieler);
                return;
            }
            beuteEinsammeln();
            if (lager.firstEmpty() < 0) {
                lagerLeeren(spieler);
                return;
            }
        }
        if (--suchPause <= 0 || ziel == null || ziel.isDead() || !ziel.isValid()) {
            suchPause = 5;
            ziel = jaeger ? tierWaehlen(spieler) : zielWaehlen(spieler);
        }
        if (ziel != null) {
            kaempfen(spieler);
        } else {
            folgen(spieler);
        }
    }

    private LivingEntity zielWaehlen(Player spieler) {
        double umkreis = schuetzen ? 4 : UMKREIS;
        UUID angreifer = plugin.letzterAngreifer(spieler.getUniqueId());
        LivingEntity bester = null;
        double besterAbstand = Double.MAX_VALUE;
        for (Entity e : spieler.getNearbyEntities(umkreis, umkreis, umkreis)) {
            if (!(e instanceof LivingEntity le) || le.isDead() || e.equals(koerper)
                    || e.getType() == EntityType.MANNEQUIN) {
                continue;
            }
            boolean feind = e instanceof Enemy
                    || e instanceof Mob m && spieler.equals(m.getTarget())
                    || angreifer != null && angreifer.equals(e.getUniqueId())
                    && !(e instanceof Player p && p.getGameMode() == GameMode.CREATIVE);
            if (!feind) {
                continue;
            }
            double d = e.getLocation().distanceSquared(spieler.getLocation());
            if (d < besterAbstand) {
                besterAbstand = d;
                bester = le;
            }
        }
        return bester;
    }

    // ----------------------------------------------------------------- Jaeger

    private boolean passt(Entity e) {
        EntityType t = e.getType();
        return switch (ruestung) {
            case "Kuh" -> t == EntityType.COW;
            case "Schwein" -> t == EntityType.PIG;
            case "Schaf" -> t == EntityType.SHEEP;
            case "Huhn" -> t == EntityType.CHICKEN;
            case "Kaninchen" -> t == EntityType.RABBIT;
            default -> t == EntityType.COW || t == EntityType.PIG || t == EntityType.SHEEP
                    || t == EntityType.CHICKEN || t == EntityType.RABBIT;
        };
    }

    private LivingEntity tierWaehlen(Player spieler) {
        List<LivingEntity> tiere = new java.util.ArrayList<>();
        java.util.Map<EntityType, Integer> anzahl = new java.util.HashMap<>();
        for (Entity e : spieler.getNearbyEntities(JAGD_UMKREIS, JAGD_UMKREIS, JAGD_UMKREIS)) {
            if (e instanceof LivingEntity le && !le.isDead() && passt(e) && e.customName() == null
                    && !(e instanceof org.bukkit.entity.Ageable a && !a.isAdult())) {
                tiere.add(le);
                anzahl.merge(e.getType(), 1, Integer::sum);
            }
        }
        int uebrig = Math.max(0, werte.zahl());
        LivingEntity bester = null;
        double besterAbstand = Double.MAX_VALUE;
        for (LivingEntity e : tiere) {
            if (anzahl.get(e.getType()) <= uebrig) {
                continue;
            }
            double d = e.getLocation().distanceSquared(koerper.getLocation());
            if (d < besterAbstand) {
                besterAbstand = d;
                bester = e;
            }
        }
        return bester;
    }

    private void beuteEinsammeln() {
        if (beuteOrt == null || --beuteTicks < 0 || beuteTicks % 5 != 0) {
            return;
        }
        for (Entity e : welt.getNearbyEntities(beuteOrt, 3, 3, 3, x -> x instanceof org.bukkit.entity.Item)) {
            org.bukkit.entity.Item item = (org.bukkit.entity.Item) e;
            java.util.Map<Integer, ItemStack> rest = lager.addItem(item.getItemStack());
            if (rest.isEmpty()) {
                item.remove();
            } else {
                item.setItemStack(rest.values().iterator().next());
            }
        }
        if (beuteTicks <= 0) {
            beuteOrt = null;
        }
    }

    private void lagerLeeren(Player spieler) {
        Location kiste = plugin.kiste(besitzer);
        if (kiste == null || kiste.getWorld() == null || !Kiste.istKiste(kiste.getBlock())) {
            plugin.melden(spieler, NamedTextColor.YELLOW, titel() + ": Mein Beutel ist voll - hier, fang!");
            abliefern = true;
            return;
        }
        Pos zurueck = fuesse;
        World zurueckWelt = welt;
        springen(kiste.getWorld(), Agent.sichererPlatzBei(kiste.getWorld(), Pos.von(kiste)));
        int bewegt = Kiste.einlagern(kiste.getBlock(), lager, x -> false);
        if (bewegt > 0) {
            geliefert += bewegt;
            welt.playSound(kiste, Sound.BLOCK_CHEST_CLOSE, 0.6f, 1f);
        }
        springen(zurueckWelt, zurueck);
        if (lager.firstEmpty() < 0) {
            plugin.melden(spieler, NamedTextColor.YELLOW, titel() + ": Die Sammelkiste ist voll - hier, fang!");
            abliefern = true;
        }
    }

    private void abliefern(Player spieler) {
        anschauen(spieler.getEyeLocation());
        if (!spieler.getWorld().equals(welt) || koerper.getLocation().distanceSquared(spieler.getLocation()) > 25) {
            if (bewegNach == null) {
                laufenZu(Pos.von(spieler.getLocation()), 2);
            }
            return;
        }
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
            Location von = koerper.getEyeLocation().subtract(0, 0.3, 0);
            Vector zum = spieler.getEyeLocation().toVector().subtract(von.toVector());
            Vector schwung = zum.clone().normalize().multiply(Math.min(0.45, 0.12 * zum.length())).add(new Vector(0, 0.18, 0));
            welt.dropItem(von, stapel, (org.bukkit.entity.Item wurf) -> {
                wurf.setVelocity(schwung);
                wurf.setPickupDelay(8);
            });
            koerper.swingMainHand();
            geliefert += stapel.getAmount();
            return;
        }
        abliefern = false;
        plugin.melden(spieler, NamedTextColor.GREEN, titel() + " hat dir " + geliefert + " Items gebracht ("
                + kills + " Tiere erlegt).");
        effekt(Particle.POOF, 20);
        fertig = true;
    }

    private void springen(World zielWelt, Pos platz) {
        effekt(Particle.PORTAL, 20);
        if (zielWelt.equals(welt)) {
            koerper.teleport(platz.fuesse(welt));
            fuesse = platz;
            pfad = null;
            bewegNach = null;
        } else {
            koerperBauen(zielWelt, platz);
        }
        effekt(Particle.PORTAL, 20);
    }

    // ------------------------------------------------------------- Kampf

    private void kaempfen(Player spieler) {
        double grenze = (jaeger ? JAGD_UMKREIS : UMKREIS) + 6;
        if (!ziel.getWorld().equals(welt)
                || ziel.getLocation().distanceSquared(spieler.getLocation()) > grenze * grenze) {
            ziel = null;
            return;
        }
        anschauen(ziel.getEyeLocation());
        if (koerper.getLocation().distance(ziel.getLocation()) <= REICHWEITE) {
            if (angriffPause == 0) {
                koerper.swingMainHand();
                ziel.damage(schaden, koerper);
                Vector weg = ziel.getLocation().toVector().subtract(koerper.getLocation().toVector());
                ziel.knockback(0.5, -weg.getX(), -weg.getZ());
                welt.playSound(ziel.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1f);
                if (ziel.isDead()) {
                    kills++;
                    if (jaeger) {
                        beuteOrt = ziel.getLocation();
                        beuteTicks = 40;
                    }
                    ziel = null;
                }
                angriffPause = ANGRIFF_PAUSE;
            }
            return;
        }
        if (bewegNach == null) {
            laufenZu(Pos.von(ziel.getLocation()), 1);
        }
    }

    /** Mehrere Waechter stehen im Kreis um den Spieler, jeder an seinem Platz. */
    private Pos platzBei(Player spieler) {
        Pos mitte = Pos.von(spieler.getLocation());
        int[] rang = plugin.rang(this);
        if (rang[1] <= 1) {
            return mitte;
        }
        double winkel = 2 * Math.PI * rang[0] / rang[1];
        int r = schuetzen ? 2 : 3;
        return mitte.plus((int) Math.round(Math.cos(winkel) * r), 0, (int) Math.round(Math.sin(winkel) * r));
    }

    private void folgen(Player spieler) {
        boolean formation = plugin.rang(this)[1] > 1;
        Pos platz = platzBei(spieler);
        double abstand = Math.sqrt(fuesse.abstandQ(platz));
        if (abstand <= (formation ? 1.2 : schuetzen ? 1.5 : 3)) {
            pfad = null;
            if (bewegNach == null) {
                anschauen(spieler.getEyeLocation());
            }
            return;
        }
        if (bewegNach == null) {
            laufenZu(platz, formation ? 1 : schuetzen ? 1 : 2);
        }
    }

    private void laufenZu(Pos zielPos, int nah) {
        if (pfad == null || pfadIndex >= pfad.size() || ++planAlter > 10) {
            planAlter = 0;
            pfad = new Pfad(welt, false, false).suchen(fuesse, n -> n.manhattan(zielPos) <= nah, zielPos, 2500, 40);
            pfadIndex = 0;
            if (pfad == null || pfad.isEmpty()) {
                pfad = null;
                return;
            }
        }
        Pos nach = pfad.get(pfadIndex);
        if (!Bloecke.frei(nach.block(welt)) || !Bloecke.frei(nach.hoch().block(welt))) {
            pfad = null;
            return;
        }
        pfadIndex++;
        bewegVon = koerper.getLocation();
        bewegNach = nach.fuesse(welt);
        Vector d = bewegNach.toVector().subtract(bewegVon.toVector());
        float gier = bewegVon.getYaw();
        if (ziel == null && d.getX() * d.getX() + d.getZ() * d.getZ() > 0.01) {
            gier = (float) Math.toDegrees(Math.atan2(d.getZ(), d.getX())) - 90f;
        }
        bewegNach.setYaw(gier);
        bewegNach.setPitch(bewegVon.getPitch());
        bewegTick = 0;
        bewegDauer = (int) Math.max(1, Math.round(3 / Math.max(1.0, werte.tempo())));
        fuesse = nach;
    }

    private void bewegen() {
        bewegTick++;
        double t = Math.min(1.0, bewegTick / (double) bewegDauer);
        Location jetzt = koerper.getLocation();
        Location p = new Location(welt,
                bewegVon.getX() + (bewegNach.getX() - bewegVon.getX()) * t,
                bewegVon.getY() + (bewegNach.getY() - bewegVon.getY()) * t,
                bewegVon.getZ() + (bewegNach.getZ() - bewegVon.getZ()) * t,
                ziel != null ? jetzt.getYaw() : bewegNach.getYaw(), jetzt.getPitch());
        koerper.teleport(p);
        if (t >= 1.0) {
            bewegNach = null;
        }
    }

    private void teleportieren(Player spieler) {
        World zielWelt = spieler.getWorld();
        Pos platz = Agent.sichererPlatzBei(zielWelt, Pos.von(spieler.getLocation()), nummer);
        effekt(Particle.PORTAL, 30);
        ziel = null;
        if (zielWelt.equals(welt)) {
            koerper.teleport(platz.fuesse(welt));
            fuesse = platz;
            pfad = null;
            bewegNach = null;
        } else if (!koerperBauen(zielWelt, platz)) {
            return;
        }
        effekt(Particle.PORTAL, 30);
        welt.playSound(platz.fuesse(welt), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    private void anschauen(Location punkt) {
        Location auge = koerper.getEyeLocation();
        Vector d = punkt.toVector().subtract(auge.toVector());
        float gier = (float) Math.toDegrees(Math.atan2(d.getZ(), d.getX())) - 90f;
        float neigung = (float) -Math.toDegrees(Math.atan2(d.getY(), Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ())));
        koerper.setRotation(gier, neigung);
    }

    private void namenAktualisieren() {
        String text = titel() + (kills > 0 ? " · " + kills + (jaeger ? " erlegt" : " besiegt") : "")
                + (schuetzen ? " ❤" : "");
        koerper.customName(Component.text(text, schuetzen ? NamedTextColor.RED : NamedTextColor.GOLD));
    }

    private void effekt(Particle art, int anzahl) {
        if (koerper != null && koerper.isValid()) {
            welt.spawnParticle(art, koerper.getLocation().add(0, 1, 0), anzahl, 0.3, 0.6, 0.3, 0.05);
        }
    }
}
