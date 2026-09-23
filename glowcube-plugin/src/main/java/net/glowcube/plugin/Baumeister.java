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
    /** snbt: Inhalt (Truhe, Schild ...) oder null. */
    private record Auftragsblock(int x, int y, int z, BlockData daten, String snbt) {
    }

    private final GlowCubeAgentPlugin plugin;
    private final UUID besitzer;
    private final int nummer;
    private final String planName;
    private int versatz;
    private int rechterRand;
    private Werte werte;

    private World welt;
    private LivingEntity koerper;
    private List<Auftragsblock> liste;
    private int index;
    private double guthaben;
    private int gesetzt;
    private int abgeraeumt;
    private int unbekannt;
    private int befuellt;
    private int ticks;
    private int schwungPause;
    private boolean fertig;
    private boolean abgebrochen;
    private long ticketChunk = Long.MIN_VALUE;

    private Baumeister(GlowCubeAgentPlugin plugin, UUID besitzer, int nummer, String planName, Werte werte) {
        this.plugin = plugin;
        this.besitzer = besitzer;
        this.nummer = nummer;
        this.planName = planName;
        this.werte = werte;
    }

    /** linkerRand: 0 fuer den ersten Builder, sonst ab wo seine Baustelle (seitlich) anfangen muss. */
    static Baumeister erschaffen(GlowCubeAgentPlugin plugin, Player spieler, int nummer, String planName, Werte werte,
                                 int linkerRand) {
        Bauplan plan = Bauplaene.laden(plugin, planName);
        if (plan == null) {
            plugin.melden(spieler, NamedTextColor.RED, "Schematic \"" + planName + "\" gibt es auf diesem Server nicht. "
                    + "Der Server-Admin kann es nach plugins/GlowCubeAgent/schematics legen.");
            return null;
        }
        Baumeister b = new Baumeister(plugin, spieler.getUniqueId(), nummer, planName, werte);
        Location ursprung = spieler.getLocation();
        Location ort = plugin.einsatzort(spieler.getUniqueId());
        if (ort != null && ort.getWorld() != null) {
            ursprung = ort;
        }
        b.welt = ursprung.getWorld();
        b.versatz = linkerRand > 0 ? linkerRand + plan.breite / 2 : 0;
        b.rechterRand = b.versatz + plan.breite - 1 - plan.breite / 2;
        b.liste = b.planen(plan, spieler, ursprung);
        Pos platz = Agent.sichererPlatzBei(b.welt, Pos.von(ursprung));
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
        b.koerper.setGlowing(werte.leuchten());
        if (b.koerper.getEquipment() != null) {
            b.koerper.getEquipment().setItemInMainHand(new ItemStack(Material.BRICKS));
        }
        b.namenAktualisieren();
        b.effekt(Particle.PORTAL, 40);
        plugin.melden(spieler, NamedTextColor.AQUA, "Builder: baue \"" + planName + "\" (" + plan.breite + "x"
                + plan.hoehe + "x" + plan.laenge + ", " + plan.festeBloecke() + " Bloecke).");
        return b;
    }

    private List<Auftragsblock> planen(Bauplan plan, Player spieler, Location ursprung) {
        BlockFace blick = spieler.getFacing();
        StructureRotation drehung = switch (blick) {
            case WEST -> StructureRotation.CLOCKWISE_90;
            case NORTH -> StructureRotation.CLOCKWISE_180;
            case EAST -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
        int ox = ursprung.getBlockX();
        int oy = ursprung.getBlockY();
        int oz = ursprung.getBlockZ();
        Map<String, BlockData> zwischenspeicher = new HashMap<>();
        List<Auftragsblock> ergebnis = new ArrayList<>(plan.bloecke.size());
        int halbeBreite = plan.breite / 2;
        Map<String, String> inhalte = new HashMap<>();
        for (Bauplan.Daten d : plan.daten) {
            inhalte.put(d.x() + "," + d.y() + "," + d.z(), d.snbt());
        }
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
            int lx = b.x() - halbeBreite + versatz;
            int lz = b.z() + 2;
            int wx;
            int wz;
            switch (drehung) {
                case CLOCKWISE_90 -> { wx = -lz; wz = lx; }
                case CLOCKWISE_180 -> { wx = -lx; wz = -lz; }
                case COUNTERCLOCKWISE_90 -> { wx = lz; wz = -lx; }
                default -> { wx = lx; wz = lz; }
            }
            ergebnis.add(new Auftragsblock(ox + wx, oy + b.y(), oz + wz, daten,
                    inhalte.get(b.x() + "," + b.y() + "," + b.z())));
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
    public int nummer() {
        return nummer;
    }

    int rechterRand() {
        return rechterRand;
    }

    @Override
    public org.bukkit.entity.Entity koerper() {
        return koerper;
    }

    @Override
    public String zustandText() {
        if (liste == null || liste.isEmpty()) {
            return "baut";
        }
        return abgebrochen ? "hoert auf" : "baut " + Math.min(100, index * 100 / liste.size()) + "%";
    }

    @Override
    public int beute() {
        return gesetzt;
    }

    @Override
    public Auftrag auftrag() {
        return Auftrag.BAUMEISTER;
    }

    @Override
    public String titel() {
        return "Builder-Agent #" + nummer + " (" + planName + ")";
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
        if (koerper != null) {
            koerper.setGlowing(neu.leuchten());
        }
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
            if ((jetzt.equals(a.daten) || jetzt.getMaterial().isAir() && a.daten.getMaterial().isAir()) && a.snbt == null) {
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

    /**
     * Genau wie im Schematic setzen - ohne Physik, beim Abraeumen wie beim
     * Bauen: kein Wasser fliesst nach, kein Sand faellt, nichts bricht weg,
     * Redstone behaelt seinen Zustand. Danach bekommt ein Block mit Inhalt
     * (Truhe, Trichter, Schild ...) seine Daten.
     */
    private void setzen(Block block, Auftragsblock a) {
        BlockData jetzt = block.getBlockData();
        boolean abraeumen = !jetzt.getMaterial().isAir() && !jetzt.equals(a.daten) && !block.isReplaceable();
        if (abraeumen) {
            welt.playEffect(block.getLocation(), Effect.STEP_SOUND, jetzt);
            abgeraeumt++;
        }
        if (!jetzt.equals(a.daten)) {
            block.setBlockData(a.daten, false);
            if (!a.daten.getMaterial().isAir()) {
                gesetzt++;
                if (gesetzt % 3 == 0) {
                    welt.playSound(block.getLocation(), a.daten.getSoundGroup().getPlaceSound(), 0.7f, 1f);
                }
            }
        }
        if (a.snbt != null) {
            inhaltSetzen(a);
        }
        if (schwungPause == 0 && koerper.getEquipment() != null) {
            Material m = a.daten.getMaterial();
            ItemStack hand;
            if (abraeumen || m.isAir()) {
                hand = Bloecke.werkzeug(block).clone();
            } else {
                hand = m.isItem() ? new ItemStack(m) : Bloecke.SPITZHACKE.clone();
            }
            koerper.getEquipment().setItemInMainHand(hand);
            anschauen(new Location(welt, a.x + 0.5, a.y + 0.5, a.z + 0.5));
            koerper.swingMainHand();
            schwungPause = 4;
        }
    }

    /** Die Daten aus dem Schematic in den Block schreiben - per /data merge block, ohne Ausgabe. */
    private void inhaltSetzen(Auftragsblock a) {
        String befehl = "execute in " + welt.getKey().asString() + " run data merge block "
                + a.x + " " + a.y + " " + a.z + " " + a.snbt;
        try {
            Bukkit.dispatchCommand(plugin.stillerBefehlsgeber(), befehl);
            befuellt++;
        } catch (RuntimeException fehler) {
            plugin.getLogger().warning("Inhalt bei " + a.x + "," + a.y + "," + a.z + " nicht gesetzt: " + fehler);
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
                            + " abgeraeumt" + (befuellt > 0 ? ", " + befuellt + " Truhen/Schilder befuellt" : "") + (unbekannt > 0 ? ", " + unbekannt + " unbekannte Bloecke ausgelassen" : "") + ".");
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
