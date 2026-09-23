package net.glowcube.plugin;

import net.glowcube.plugin.bauplan.Bauplan;
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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builder-Agent auf dem Server - dieselbe Logik wie im Client
 * (AgentBaumeister): das Schematic auf der Hoehe des Spielers, zwei Bloecke
 * vor ihm, in Blickrichtung gedreht; abraeumen, was im Weg ist; Schicht fuer
 * Schicht; Meldung im Chat, wenn fertig.
 */
final class Baumeister implements AgentArbeiter {
    private record Auftragsblock(int x, int y, int z, BlockData daten) {
    }

    private final GlowCubeAgentPlugin plugin;
    private final UUID besitzer;
    private final String planName;
    private Werte werte;

    private World welt;
    private LivingEntity koerper;
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
    private long ticketChunk = Long.MIN_VALUE;

    private Baumeister(GlowCubeAgentPlugin plugin, UUID besitzer, String planName, Werte werte) {
        this.plugin = plugin;
        this.besitzer = besitzer;
        this.planName = planName;
        this.werte = werte;
    }

    static Baumeister erschaffen(GlowCubeAgentPlugin plugin, Player spieler, String planName, Werte werte) {
        Bauplan plan = Bauplaene.laden(plugin, planName);
        if (plan == null) {
            plugin.melden(spieler, NamedTextColor.RED, "Schematic \"" + planName + "\" gibt es auf diesem Server nicht. "
                    + "Der Server-Admin kann es nach plugins/GlowCubeAgent/schematics legen.");
            return null;
        }
        Baumeister b = new Baumeister(plugin, spieler.getUniqueId(), planName, werte);
        b.welt = spieler.getWorld();
        b.liste = b.planen(plan, spieler);
        Pos platz = Agent.sichererPlatzBei(b.welt, Pos.von(spieler.getLocation()));
        try {
            b.koerper = (LivingEntity) b.welt.spawnEntity(platz.fuesse(b.welt), EntityType.MANNEQUIN);
        } catch (RuntimeException fehler) {
            plugin.getLogger().warning("Builder konnte nicht erscheinen: " + fehler);
            return null;
        }
        b.koerper.setGravity(false);
        b.koerper.setInvulnerable(true);
        b.koerper.setPersistent(false);
        b.koerper.setCustomNameVisible(true);
        b.koerper.setCollidable(false);
        if (b.koerper.getEquipment() != null) {
            b.koerper.getEquipment().setItemInMainHand(new ItemStack(Material.BRICKS));
        }
        b.namenAktualisieren();
        b.effekt(Particle.PORTAL, 40);
        plugin.melden(spieler, NamedTextColor.AQUA, "Builder: baue \"" + planName + "\" (" + plan.breite + "x"
                + plan.hoehe + "x" + plan.laenge + ", " + plan.festeBloecke() + " Bloecke).");
        return b;
    }

    private List<Auftragsblock> planen(Bauplan plan, Player spieler) {
        BlockFace blick = spieler.getFacing();
        StructureRotation drehung = switch (blick) {
            case WEST -> StructureRotation.CLOCKWISE_90;
            case NORTH -> StructureRotation.CLOCKWISE_180;
            case EAST -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
        Location ursprung = spieler.getLocation();
        int ox = ursprung.getBlockX();
        int oy = ursprung.getBlockY();
        int oz = ursprung.getBlockZ();
        Map<String, BlockData> zwischenspeicher = new HashMap<>();
        List<Auftragsblock> ergebnis = new ArrayList<>(plan.bloecke.size());
        int halbeBreite = plan.breite / 2;
        for (Bauplan.Block b : plan.bloecke) {
            BlockData daten = zwischenspeicher.computeIfAbsent(b.zustand(), text -> {
                try {
                    BlockData d = Bukkit.createBlockData(text);
                    d.rotate(drehung);
                    return d;
                } catch (IllegalArgumentException kaputt) {
                    return null;
                }
            });
            if (daten == null) {
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
            ergebnis.add(new Auftragsblock(ox + wx, oy + b.y(), oz + wz, daten));
        }
        ergebnis.sort((a, c) -> {
            if (a.y != c.y) {
                return Integer.compare(a.y, c.y);
            }
            if (a.x != c.x) {
                return Integer.compare(a.x, c.x);
            }
            return (a.x & 1) == 0 ? Integer.compare(a.z, c.z) : Integer.compare(c.z, a.z);
        });
        return ergebnis;
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
    public void einstellen(Werte neu) {
        werte = neu;
    }

    @Override
    public void notfallUebergabe() {
        fertig = true;
    }

    @Override
    public void aufraeumen() {
        ticketFrei();
        if (koerper != null && koerper.isValid()) {
            koerper.remove();
        }
    }

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
        guthaben += Math.max(1, werte.abbauTempo()) / 20.0;
        int geprueft = 0;
        while (index < liste.size() && guthaben >= 1 && geprueft < 6000) {
            Auftragsblock a = liste.get(index);
            geprueft++;
            if (geprueft == 1) {
                ticketHalten(a.x >> 4, a.z >> 4);
            }
            Block block = welt.getBlockAt(a.x, a.y, a.z);
            BlockData jetzt = block.getBlockData();
            if (jetzt.equals(a.daten) || jetzt.getMaterial().isAir() && a.daten.getMaterial().isAir()) {
                index++;
                continue;
            }
            setzen(block, a);
            index++;
            guthaben -= 1;
        }
        if (index >= liste.size()) {
            abschliessen(spieler, "fertig");
            return;
        }
        Auftragsblock naechster = liste.get(index);
        schweben(naechster);
    }

    private void setzen(Block block, Auftragsblock a) {
        if (a.daten.getMaterial().isAir()) {
            welt.playEffect(block.getLocation(), Effect.STEP_SOUND, block.getBlockData());
            block.setType(Material.AIR, false);
            abgeraeumt++;
        } else {
            if (!block.getType().isAir() && !block.isReplaceable()) {
                abgeraeumt++;
            }
            block.setBlockData(a.daten, false);
            gesetzt++;
            if (gesetzt % 3 == 0) {
                welt.playSound(block.getLocation(), a.daten.getSoundGroup().getPlaceSound(), 0.7f, 1f);
            }
        }
        if (schwungPause == 0 && koerper.getEquipment() != null) {
            Material m = a.daten.getMaterial();
            koerper.getEquipment().setItemInMainHand(new ItemStack(m.isAir() || !m.isItem() ? Material.DIAMOND_PICKAXE : m));
            anschauen(new Location(welt, a.x + 0.5, a.y + 0.5, a.z + 0.5));
            koerper.swingMainHand();
            schwungPause = 4;
        }
    }

    private void schweben(Auftragsblock an) {
        Location ziel = new Location(welt, an.x + 0.5, an.y + 2.2, an.z - 1.5);
        Location jetzt = koerper.getLocation();
        Vector d = ziel.toVector().subtract(jetzt.toVector());
        double schritt = 0.35 * Math.max(1.0, werte.tempo());
        Vector neu = d.length() <= schritt ? ziel.toVector() : jetzt.toVector().add(d.normalize().multiply(schritt));
        koerper.teleport(new Location(welt, neu.getX(), neu.getY(), neu.getZ(), jetzt.getYaw(), jetzt.getPitch()));
    }

    private void abschliessen(Player spieler, String wie) {
        if (spieler != null) {
            plugin.melden(spieler, wie.equals("fertig") ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                    "Builder: \"" + planName + "\" " + wie + " - " + gesetzt + " Bloecke gesetzt, " + abgeraeumt
                            + " abgeraeumt" + (unbekannt > 0 ? ", " + unbekannt + " unbekannte Bloecke ausgelassen" : "") + ".");
        }
        effekt(Particle.POOF, 30);
        welt.playSound(koerper.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        fertig = true;
    }

    private void ticketHalten(int cx, int cz) {
        long c = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        if (c == ticketChunk) {
            return;
        }
        ticketFrei();
        welt.addPluginChunkTicket(cx, cz, plugin);
        ticketChunk = c;
    }

    private void ticketFrei() {
        if (ticketChunk != Long.MIN_VALUE && welt != null) {
            welt.removePluginChunkTicket((int) (ticketChunk >> 32), (int) ticketChunk, plugin);
            ticketChunk = Long.MIN_VALUE;
        }
    }

    private void anschauen(Location punkt) {
        Location auge = koerper.getEyeLocation();
        Vector d = punkt.toVector().subtract(auge.toVector());
        float gier = (float) Math.toDegrees(Math.atan2(d.getZ(), d.getX())) - 90f;
        float neigung = (float) -Math.toDegrees(Math.atan2(d.getY(), Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ())));
        koerper.setRotation(gier, neigung);
    }

    private void namenAktualisieren() {
        int prozent = liste.isEmpty() ? 100 : (int) (100L * index / liste.size());
        koerper.customName(Component.text(titel() + " · " + prozent + "%", NamedTextColor.GREEN));
    }

    private void effekt(Particle art, int anzahl) {
        if (koerper != null && koerper.isValid()) {
            welt.spawnParticle(art, koerper.getLocation().add(0, 1, 0), anzahl, 0.3, 0.6, 0.3, 0.05);
        }
    }
}
