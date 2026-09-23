package net.glowcube.plugin;

import net.kyori.adventure.text.format.NamedTextColor;
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
 * Orbital Strike auf dem Server - wie in der Einzelspielerwelt: oben auf der
 * Orbital Strike Cannon sitzt ein Hebel auf einem Leitstein (der Builder
 * setzt ihn mit). Das Ziel schickt der GlowCube-Client mit {@code /strike x y z}
 * als {@code ziel;x;y;z}. Legt man den Hebel um, schiesst die Kanone eine
 * Salve TNT in den Himmel, und kurz darauf fallen am Ziel Ringe aus TNT herab.
 *
 * <p>Abschalten mit {@code orbital-strike-erlaubt: false} in der config.yml;
 * wer ihn benutzen darf, regelt {@code glowcube.orbital} (Standard: jeder).
 */
final class OrbitalStrike implements Listener {
    private final GlowCubeAgentPlugin plugin;
    private final Map<UUID, Location> ziele = new HashMap<>();
    private final List<Einschlag> laufend = new ArrayList<>();

    private static final class Einschlag {
        final Location ziel;
        final Location kanone;
        final int ankunft;
        final List<TNTPrimed> salve = new ArrayList<>();
        final List<long[]> tickets = new ArrayList<>();
        int tick;

        Einschlag(Location ziel, Location kanone) {
            this.ziel = ziel;
            this.kanone = kanone;
            double weg = ziel.getWorld().equals(kanone.getWorld()) ? ziel.distance(kanone) : 0;
            this.ankunft = 40 + (int) Math.min(100, weg / 20);
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

    @EventHandler(ignoreCancelled = false)
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
        Location ziel = ziele.get(spieler.getUniqueId());
        if (ziel == null) {
            plugin.melden(spieler, NamedTextColor.YELLOW,
                    "Orbital Strike: erst ein Ziel setzen - /strike x y z (Koordinaten mit /coordinates).");
            return;
        }
        Einschlag e = new Einschlag(ziel.clone(), block.getLocation());
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
                + ziel.getBlockY() + " " + ziel.getBlockZ() + " - Einschlag in wenigen Sekunden!");
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1f, 0.6f);
    }

    void tick() {
        for (Iterator<Einschlag> it = laufend.iterator(); it.hasNext(); ) {
            Einschlag e = it.next();
            e.tick++;
            World kw = e.kanone.getWorld();
            Location mund = e.kanone.clone().add(0.5, 1, 0.5);
            // Abschuss: eine Salve TNT senkrecht aus der Kanone nach oben.
            if (e.tick <= 20 && e.tick % 4 == 1) {
                TNTPrimed tnt = kw.spawn(mund, TNTPrimed.class, neu -> {
                    neu.setFuseTicks(60);
                    neu.setGravity(false);
                    neu.setVelocity(new Vector(0, 2.5, 0));
                });
                e.salve.add(tnt);
                kw.spawnParticle(Particle.EXPLOSION, mund.clone().add(0, 0.5, 0), 2, 0.3, 0.3, 0.3, 0);
                kw.spawnParticle(Particle.FLAME, mund.clone().add(0, 0.5, 0), 20, 0.2, 0.5, 0.2, 0.08);
                kw.playSound(mund, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.BLOCKS, 4f, 0.5f);
                kw.playSound(mund, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.5f, 1.4f);
            }
            // Oben im Himmel verschwindet die Salve, bevor sie zuendet.
            for (Iterator<TNTPrimed> s = e.salve.iterator(); s.hasNext(); ) {
                TNTPrimed tnt = s.next();
                if (tnt.isValid()) {
                    kw.spawnParticle(Particle.FIREWORK, tnt.getLocation().add(0, -1, 0), 2, 0.1, 0.3, 0.1, 0);
                }
                if (!tnt.isValid() || tnt.getFuseTicks() <= 40) {
                    tnt.remove();
                    s.remove();
                }
            }
            if (e.tick < e.ankunft) {
                continue;
            }
            int t = e.tick - e.ankunft + 1;
            World welt = e.ziel.getWorld();
            double zx = e.ziel.getBlockX() + 0.5;
            double zy = e.ziel.getBlockY();
            double zz = e.ziel.getBlockZ() + 0.5;
            if (t == 1) {
                // Der Leitstrahl: ein Lichtband vom Himmel auf das Ziel.
                for (int y = 0; y < 60; y += 2) {
                    welt.spawnParticle(Particle.END_ROD, zx, zy + 1 + y, zz, 3, 0.1, 0.5, 0.1, 0.01);
                }
                welt.playSound(e.ziel, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 4f, 0.5f);
            }
            // Ring 0 (Mitte) bis Ring 4 (Radius 16), alle 8 Ticks ein Ring.
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
            if (t > 200) {
                for (TNTPrimed tnt : e.salve) {
                    tnt.remove();
                }
                for (long[] c : e.tickets) {
                    welt.removePluginChunkTicket((int) c[0], (int) c[1], plugin);
                }
                it.remove();
            }
        }
    }

    /** Beim Abschalten: offene Chunk-Tickets freigeben. */
    void aufraeumen() {
        for (Einschlag e : laufend) {
            for (TNTPrimed tnt : e.salve) {
                tnt.remove();
            }
            for (long[] c : e.tickets) {
                e.ziel.getWorld().removePluginChunkTicket((int) c[0], (int) c[1], plugin);
            }
        }
        laufend.clear();
    }
}
