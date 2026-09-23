package net.glowcube.plugin;

import net.glowcube.plugin.bauplan.Kanone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orbital Strike auf dem Server - wie in der Einzelspielerwelt: gefeuert wird
 * mit dem Hebel auf dem Leitstein ganz oben auf der Orbital Strike Cannon (der
 * Builder setzt ihn mit), und nur, wenn rund um ihn wirklich die Kanone steht.
 * Das Ziel schickt der GlowCube-Client mit {@code /strike x y z} als
 * {@code ziel;x;y;z}.
 *
 * <p>Die Kanone laeuft sichtbar ab: Signal durch die Redstone-Leitungen, TNT
 * in den Ladearmen zuendet von aussen nach innen, die Portale leuchten, Knall
 * im Kern, und die Salve schiesst senkrecht aus der Kanone in den Himmel. Nach
 * der Flugzeit fallen am Ziel Ringe aus TNT herab.
 *
 * <p>Abschalten mit {@code orbital-strike-erlaubt: false} in der config.yml;
 * wer ihn benutzen darf, regelt {@code glowcube.orbital} (Standard: jeder).
 */
final class OrbitalStrike implements Listener {
    private static final int SIGNAL_ENDE = 30;
    private static final int LADEN_ENDE = 65;
    private static final int ZUENDUNG = 80;
    private static final int SALVE_ENDE = 100;
    private static final Particle.DustOptions SIGNAL = new Particle.DustOptions(Color.fromRGB(0xFF2020), 1.4f);

    private final GlowCubeAgentPlugin plugin;
    private final Map<UUID, Location> ziele = new HashMap<>();
    private final List<Einschlag> laufend = new ArrayList<>();
    private Kanone kanonePlan;

    private static final class Einschlag {
        final Player schuetze;
        final Location ziel;
        final Block hebel;
        final Kanone kanone;
        final int drehung;
        final int ankunft;
        final List<TNTPrimed> salve = new ArrayList<>();
        final List<long[]> tickets = new ArrayList<>();
        int tick;

        Einschlag(Player schuetze, Location ziel, Block hebel, Kanone kanone, int drehung) {
            this.schuetze = schuetze;
            this.ziel = ziel;
            this.hebel = hebel;
            this.kanone = kanone;
            this.drehung = drehung;
            double weg = ziel.getWorld().equals(hebel.getWorld()) ? ziel.distance(hebel.getLocation()) : 0;
            this.ankunft = SALVE_ENDE + 20 + (int) Math.min(100, weg / 20);
        }

        Location ort(int[] rel) {
            int[] w = Kanone.drehen(rel, drehung);
            return new Location(hebel.getWorld(), hebel.getX() + w[0] + 0.5, hebel.getY() + w[1] + 0.5,
                    hebel.getZ() + w[2] + 0.5);
        }
    }

    OrbitalStrike(GlowCubeAgentPlugin plugin) {
        this.plugin = plugin;
    }

    boolean erlaubt(Player spieler) {
        return plugin.getConfig().getBoolean("orbital-strike-erlaubt", true)
                && spieler.hasPermission("glowcube.orbital");
    }

    /** Vom Client: {@code ziel;x;y;z}. */
    void zielSetzen(Player spieler, String[] t) {
        if (!erlaubt(spieler)) {
            plugin.melden(spieler, NamedTextColor.RED, "Den Orbital Strike darfst du hier nicht benutzen.");
            return;
        }
        try {
            int x = Integer.parseInt(t[1]);
            int y = Integer.parseInt(t[2]);
            int z = Integer.parseInt(t[3]);
            ziele.put(spieler.getUniqueId(), new Location(spieler.getWorld(), x, y, z));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException falsch) {
            plugin.melden(spieler, NamedTextColor.RED, "Orbital Strike: ungueltige Koordinaten.");
        }
    }

    private Kanone plan() {
        if (kanonePlan == null) {
            kanonePlan = Kanone.aus(Bauplaene.laden(plugin, Kanone.NAME));
        }
        return kanonePlan;
    }

    @EventHandler
    public void hebel(PlayerInteractEvent ereignis) {
        if (ereignis.getAction() != Action.RIGHT_CLICK_BLOCK || ereignis.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = ereignis.getClickedBlock();
        if (block == null || block.getType() != Material.LEVER
                || block.getRelative(0, -1, 0).getType() != Material.LODESTONE
                || !(block.getBlockData() instanceof Powerable hebel) || hebel.isPowered()) {
            return;
        }
        Player spieler = ereignis.getPlayer();
        if (!erlaubt(spieler)) {
            return;
        }
        Kanone kanone = plan();
        if (kanone == null) {
            plugin.melden(spieler, NamedTextColor.RED, "Orbital Strike: der Bauplan der Kanone fehlt.");
            return;
        }
        World kw = block.getWorld();
        int drehung = kanone.erkennen(block.getX(), block.getY(), block.getZ(),
                (x, y, z) -> kw.getBlockAt(x, y, z).getType().getKey().toString());
        if (drehung < 0) {
            plugin.melden(spieler, NamedTextColor.YELLOW, "Orbital Strike: hier steht keine Orbital Strike "
                    + "Cannon. Erst die Kanone bauen (Builder, Schematic Orbital-Strike-Cannon) - der Hebel sitzt "
                    + "ganz oben.");
            return;
        }
        Location ziel = ziele.get(spieler.getUniqueId());
        if (ziel == null) {
            plugin.melden(spieler, NamedTextColor.YELLOW,
                    "Orbital Strike: erst ein Ziel setzen - /strike x y z (Koordinaten mit /coordinates).");
            return;
        }
        Einschlag e = new Einschlag(spieler, ziel.clone(), block, kanone, drehung);
        // Das Ziel geladen halten, auch wenn es weit weg ist.
        World welt = ziel.getWorld();
        int cx = ziel.getBlockX() >> 4;
        int cz = ziel.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                welt.addPluginChunkTicket(cx + dx, cz + dz, plugin);
                e.tickets.add(new long[] {cx + dx, cz + dz});
            }
        }
        laufend.add(e);
        plugin.melden(spieler, NamedTextColor.RED, "Orbital Strike auf " + ziel.getBlockX() + " "
                + ziel.getBlockY() + " " + ziel.getBlockZ() + " - die Kanone laeuft an!");
        kw.playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 2f, 0.6f);
    }

    private static void anzeige(Einschlag e, String text) {
        if (e.schuetze.isOnline()) {
            e.schuetze.sendActionBar(Component.text(text, NamedTextColor.GOLD));
        }
    }

    void tick() {
        for (Iterator<Einschlag> it = laufend.iterator(); it.hasNext(); ) {
            Einschlag e = it.next();
            e.tick++;
            kanoneTick(e);
            if (e.tick >= e.ankunft) {
                einschlagTick(e, e.tick - e.ankunft + 1);
            }
            if (e.tick > e.ankunft + 200) {
                freigeben(e);
                it.remove();
            }
        }
    }

    /** Die Vorfuehrung an der Kanone selbst. */
    private void kanoneTick(Einschlag e) {
        World w = e.hebel.getWorld();
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
                w.spawnParticle(Particle.DUST, e.ort(k.leitung.get(i)).add(0, -0.3, 0), 2, 0.15, 0.05, 0.15, 0,
                        SIGNAL);
            }
            if (t % 5 == 1 && !k.leitung.isEmpty()) {
                w.playSound(e.ort(k.leitung.get(Math.min(bis, k.leitung.size() - 1))),
                        Sound.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 1f, 1.2f);
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
                Location o = e.ort(k.ladung.get(i));
                w.spawnParticle(Particle.FLAME, o.clone().add(0, 0.4, 0), 12, 0.3, 0.3, 0.3, 0.02);
                w.spawnParticle(Particle.LARGE_SMOKE, o.clone().add(0, 0.6, 0), 4, 0.2, 0.2, 0.2, 0.01);
                w.playSound(o, Sound.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 0.7f, 1.3f);
            }
            if (t % 4 == 0) {
                w.playSound(e.ort(k.kern), Sound.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 1.5f, 0.8f);
            }
        }
        // 3. Zuendung: die Portale leuchten, im Kern baut sich Druck auf.
        if (t > LADEN_ENDE && t <= ZUENDUNG) {
            if (t == LADEN_ENDE + 1) {
                anzeige(e, "3/4  Zuendung!");
                w.playSound(e.ort(k.kern), Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 1.5f, 1.6f);
            }
            for (int[] p : k.portale) {
                w.spawnParticle(Particle.REVERSE_PORTAL, e.ort(p), 6, 0.3, 0.4, 0.3, 0.05);
            }
            Location kern = e.ort(k.kern);
            w.spawnParticle(Particle.PORTAL, kern, 30, 1.5, 0.5, 1.5, 0.8);
            w.spawnParticle(Particle.FLAME, kern, 10, 1.0, 0.3, 1.0, 0.05);
        }
        // 4. Abschuss: Knall im Kern, die Salve schiesst senkrecht aus der Kanone.
        if (t == ZUENDUNG + 1) {
            anzeige(e, "4/4  Abschuss!");
            Location kern = e.ort(k.kern);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, kern, 1);
            w.playSound(kern, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 4f, 0.7f);
            Location mund = e.ort(k.muendung);
            for (double y = kern.getY(); y <= mund.getY(); y += 0.7) {
                Location l = new Location(w, kern.getX(), y, kern.getZ());
                w.spawnParticle(Particle.FLAME, l, 3, 0.2, 0.2, 0.2, 0.02);
                w.spawnParticle(Particle.CLOUD, l, 1, 0.3, 0.2, 0.3, 0.01);
            }
        }
        if (t > ZUENDUNG && t <= SALVE_ENDE && (t - ZUENDUNG) % 4 == 1) {
            Location mund = e.ort(k.muendung);
            TNTPrimed tnt = w.spawn(mund, TNTPrimed.class, neu -> {
                neu.setFuseTicks(60);
                neu.setGravity(false);
                neu.setVelocity(new Vector(0, 2.5, 0));
            });
            e.salve.add(tnt);
            w.spawnParticle(Particle.EXPLOSION, mund, 2, 0.3, 0.3, 0.3, 0);
            w.spawnParticle(Particle.FLAME, mund, 20, 0.2, 0.5, 0.2, 0.08);
            w.playSound(mund, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.BLOCKS, 4f, 0.5f);
            w.playSound(mund, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.5f, 1.4f);
        }
        // Oben im Himmel verschwindet die Salve, bevor sie zuendet.
        for (Iterator<TNTPrimed> s = e.salve.iterator(); s.hasNext(); ) {
            TNTPrimed tnt = s.next();
            if (tnt.isValid()) {
                w.spawnParticle(Particle.FIREWORK, tnt.getLocation().add(0, -1, 0), 2, 0.1, 0.3, 0.1, 0);
            }
            if (!tnt.isValid() || tnt.getFuseTicks() <= 40) {
                tnt.remove();
                s.remove();
            }
        }
    }

    /** Am Ziel: Leitstrahl, dann Ring 0 (Mitte) bis Ring 4 (Radius 16), alle 8 Ticks einer. */
    private static void einschlagTick(Einschlag e, int t) {
        World welt = e.ziel.getWorld();
        double zx = e.ziel.getBlockX() + 0.5;
        double zy = e.ziel.getBlockY();
        double zz = e.ziel.getBlockZ() + 0.5;
        if (t == 1) {
            for (int y = 0; y < 60; y += 2) {
                welt.spawnParticle(Particle.END_ROD, zx, zy + 1 + y, zz, 3, 0.1, 0.5, 0.1, 0.01);
            }
            welt.playSound(e.ziel, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 4f, 0.5f);
        }
        if (t % 8 == 0 && t <= 40) {
            int ring = t / 8 - 1;
            int radius = ring * 4;
            int anzahl = ring == 0 ? 3 : (int) Math.round(2 * Math.PI * radius / 2.5);
            for (int i = 0; i < anzahl; i++) {
                double winkel = 2 * Math.PI * i / anzahl + ring * 0.3;
                Location ort = new Location(welt, zx + Math.cos(winkel) * radius, zy + 40,
                        zz + Math.sin(winkel) * radius);
                int zuendung = 58 + (int) (Math.random() * 6);
                welt.spawn(ort, TNTPrimed.class, neu -> neu.setFuseTicks(zuendung));
            }
        }
    }

    private void freigeben(Einschlag e) {
        for (TNTPrimed tnt : e.salve) {
            tnt.remove();
        }
        for (long[] c : e.tickets) {
            e.ziel.getWorld().removePluginChunkTicket((int) c[0], (int) c[1], plugin);
        }
    }

    /** Beim Abschalten: offene Chunk-Tickets freigeben. */
    void aufraeumen() {
        for (Einschlag e : laufend) {
            freigeben(e);
        }
        laufend.clear();
    }
}
