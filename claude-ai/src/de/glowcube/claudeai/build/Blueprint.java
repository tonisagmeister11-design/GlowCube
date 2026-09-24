package de.glowcube.claudeai.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import de.glowcube.claudeai.world.Blocks;
import de.glowcube.claudeai.world.Mats;

/**
 * Ein Bauplan in Weltkoordinaten. Entsteht ueber einen Rahmen, der lokale Koordinaten
 * (x = nach rechts, z = vom Spieler weg, y = hoch) in die Blickrichtung des Spielers dreht.
 */
public final class Blueprint {

    /** Ein Block im Plan. data == null bedeutet: freiraeumen (nur Natur). item = was es kostet. */
    public record Placement(int x, int y, int z, BlockData data, Material item) {}

    public final String name;
    public final World world;
    public final Location center;
    public final List<Placement> placements = new ArrayList<>();
    private final Map<Long, Integer> index = new HashMap<>();

    // Rahmen
    private final int ox, oy, oz;
    private final int fx, fz, rx, rz;

    public Blueprint(String name, Location origin, float yaw) {
        this.name = name;
        this.world = origin.getWorld();
        this.ox = origin.getBlockX();
        this.oy = origin.getBlockY();
        this.oz = origin.getBlockZ();
        int quarter = Math.floorMod(Math.round(yaw / 90f), 4);
        int[][] forward = { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } };
        this.fx = forward[quarter][0];
        this.fz = forward[quarter][1];
        this.rx = -fz;
        this.rz = fx;
        this.center = origin.clone();
    }

    public int wx(int lx, int lz) {
        return ox + rx * lx + fx * lz;
    }

    public int wz(int lx, int lz) {
        return oz + rz * lx + fz * lz;
    }

    public int wy(int ly) {
        return oy + ly;
    }

    public Block block(int lx, int ly, int lz) {
        return Blocks.at(world, wx(lx, lz), wy(ly), wz(lx, lz));
    }

    /** Himmelsrichtung fuer eine lokale Richtung: front (zum Spieler), back, left, right. */
    public String dir(String local) {
        int dx, dz;
        switch (local) {
            case "front" -> { dx = -fx; dz = -fz; }
            case "back" -> { dx = fx; dz = fz; }
            case "left" -> { dx = -rx; dz = -rz; }
            default -> { dx = rx; dz = rz; }
        }
        if (dx == 1) return "east";
        if (dx == -1) return "west";
        if (dz == 1) return "south";
        return "north";
    }

    private long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /**
     * Setzt einen Block. spec ist ein Blockdaten-Text wie "oak_stairs[facing=%right%,half=bottom]";
     * %front% %back% %left% %right% werden in Himmelsrichtungen uebersetzt. Spaetere Aufrufe
     * ueberschreiben fruehere an derselben Stelle.
     */
    public void set(int lx, int ly, int lz, String spec) {
        String resolved = spec.replace("%front%", dir("front")).replace("%back%", dir("back"))
                .replace("%left%", dir("left")).replace("%right%", dir("right"));
        BlockData data = parse(resolved);
        if (data == null) return;
        put(wx(lx, lz), wy(ly), wz(lx, lz), data, itemFor(data, resolved));
    }

    /** Freiraeumen (nur natuerliche Bloecke, Bauwerke bleiben stehen). */
    public void clear(int lx, int ly, int lz) {
        long k = key(wx(lx, lz), wy(ly), wz(lx, lz));
        if (index.containsKey(k)) return;
        put(wx(lx, lz), wy(ly), wz(lx, lz), null, null);
    }

    public void setWorld(int x, int y, int z, String spec) {
        BlockData data = parse(spec);
        if (data != null) put(x, y, z, data, itemFor(data, spec));
    }

    private void put(int x, int y, int z, BlockData data, Material item) {
        long k = key(x, y, z);
        Placement p = new Placement(x, y, z, data, item);
        Integer at = index.get(k);
        if (at != null) {
            placements.set(at, p);
        } else {
            index.put(k, placements.size());
            placements.add(p);
        }
    }

    private static BlockData parse(String spec) {
        String s = spec.contains(":") ? spec : "minecraft:" + spec;
        try {
            return Bukkit.createBlockData(s);
        } catch (Throwable t) {
            // Block gibt es in dieser Version nicht: ohne Zustand versuchen, sonst weglassen
            String plain = spec.replaceAll("\\[.*]", "");
            Material m = Mats.get(plain);
            return m == null ? null : m.createBlockData();
        }
    }

    /** Was kostet dieser Block? Obere Tuerhaelften, Bettkoepfe, Wasser usw. kosten nichts. */
    private static Material itemFor(BlockData data, String spec) {
        Material m = data.getMaterial();
        String n = m.name();
        if (spec.contains("half=upper") || spec.contains("part=head")) return null;
        if (n.equals("WATER") || n.equals("LAVA") || n.equals("FARMLAND") || n.equals("DIRT_PATH")) return null;
        if (n.equals("WALL_TORCH")) return Mats.get("TORCH");
        if (n.equals("WHEAT")) return Mats.get("WHEAT_SEEDS");
        if (n.equals("CARROTS")) return Mats.get("CARROT");
        if (n.equals("POTATOES")) return Mats.get("POTATO");
        return m.isItem() ? m : null;
    }

    /** Stueckliste (fuer "brauche Material"-Modus). */
    public Map<Material, Integer> bill() {
        Map<Material, Integer> out = new LinkedHashMap<>();
        for (Placement p : placements) if (p.item() != null) out.merge(p.item(), 1, Integer::sum);
        return out;
    }

    /** Sortiert: erst Freiraeumen von oben nach unten, dann Bauen von unten nach oben. */
    public void finish() {
        List<Placement> clears = new ArrayList<>();
        List<Placement> builds = new ArrayList<>();
        for (Placement p : placements) (p.data() == null ? clears : builds).add(p);
        clears.sort((a, b) -> Integer.compare(b.y(), a.y()));
        builds.sort((a, b) -> Integer.compare(a.y(), b.y()));
        placements.clear();
        placements.addAll(clears);
        placements.addAll(builds);
    }
}
