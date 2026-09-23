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
    private static final double UMKREIS = 16;
    private static final double REICHWEITE = 3.0;
    private static final int ANGRIFF_PAUSE = 12;

    private final GlowCubeAgentPlugin plugin;
    private final UUID besitzer;
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

    private List<Pos> pfad;
    private int pfadIndex;
    private int planAlter;
    private Location bewegVon;
    private Location bewegNach;
    private int bewegTick;
    private int bewegDauer;
    private int ticks;

    private Waechter(GlowCubeAgentPlugin plugin, UUID besitzer, String ruestung, Werte werte) {
        this.plugin = plugin;
        this.besitzer = besitzer;
        this.ruestung = ruestung;
        this.werte = werte;
        this.schaden = switch (ruestung) {
            case "Eisen" -> 8;
            case "Netherite" -> 12;
            default -> 10;
        };
    }

    static Waechter erschaffen(GlowCubeAgentPlugin plugin, Player spieler, String ruestung, Werte werte) {
        Waechter w = new Waechter(plugin, spieler.getUniqueId(), ruestung, werte);
        if (!w.koerperBauen(spieler.getWorld(), Agent.sichererPlatzBei(spieler.getWorld(), Pos.von(spieler.getLocation())))) {
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
        return Auftrag.WAECHTER;
    }

    @Override
    public String titel() {
        return "Guardian-Agent (" + ruestung + ")";
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
        effekt(Particle.POOF, 20);
        fertig = true;
    }

    @Override
    public void einstellen(Werte neu) {
        werte = neu;
    }

    @Override
    public void notfallUebergabe() {
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
        if (!schuetzen && leben <= 2.0) {
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
        if (--suchPause <= 0 || ziel == null || ziel.isDead() || !ziel.isValid()) {
            suchPause = 5;
            ziel = zielWaehlen(spieler);
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

    private void kaempfen(Player spieler) {
        if (!ziel.getWorld().equals(welt)
                || ziel.getLocation().distanceSquared(spieler.getLocation()) > (UMKREIS + 6) * (UMKREIS + 6)) {
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

    private void folgen(Player spieler) {
        double abstand = Math.sqrt(fuesse.abstandQ(Pos.von(spieler.getLocation())));
        if (abstand <= (schuetzen ? 1.5 : 3)) {
            pfad = null;
            if (bewegNach == null) {
                anschauen(spieler.getEyeLocation());
            }
            return;
        }
        if (bewegNach == null) {
            laufenZu(Pos.von(spieler.getLocation()), schuetzen ? 1 : 2);
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
        Pos platz = Agent.sichererPlatzBei(zielWelt, Pos.von(spieler.getLocation()));
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
        String text = titel() + (kills > 0 ? " · " + kills + " besiegt" : "") + (schuetzen ? " ❤" : "");
        koerper.customName(Component.text(text, schuetzen ? NamedTextColor.RED : NamedTextColor.GOLD));
    }

    private void effekt(Particle art, int anzahl) {
        if (koerper != null && koerper.isValid()) {
            welt.spawnParticle(art, koerper.getLocation().add(0, 1, 0), anzahl, 0.3, 0.6, 0.3, 0.05);
        }
    }
}
