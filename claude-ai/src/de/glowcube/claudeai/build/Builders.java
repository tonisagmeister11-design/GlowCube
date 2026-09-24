package de.glowcube.claudeai.build;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import de.glowcube.claudeai.world.Blocks;

/**
 * Die Bauplaene. Alle bekommen die Position und Blickrichtung des Spielers und bauen vor ihm,
 * die Tuer zu ihm gewandt. Groesse: 0 = klein, 1 = mittel, 2 = gross.
 */
public final class Builders {

    private Builders() {}

    /** Boden-Ursprung: der Block, auf dem der Spieler steht, "ahead" Bloecke vor ihm. */
    private static Location ahead(Location feet, int ahead) {
        double yaw = Math.toRadians(Math.round(feet.getYaw() / 90f) * 90f);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        Location o = feet.clone().add(Math.round(fx * ahead), 0, Math.round(fz * ahead));
        o.setYaw(feet.getYaw());
        return new Location(o.getWorld(), o.getBlockX(), feet.getBlockY() - 1, o.getBlockZ(), feet.getYaw(), 0f);
    }

    // ================================================================== Haus

    public static Blueprint house(Location feet, int size, Style s) {
        int half = size == 0 ? 3 : size == 1 ? 4 : 5; // Breite 7 / 9 / 11
        int depth = size == 0 ? 7 : size == 1 ? 9 : 11;
        int height = size == 2 ? 5 : 4;
        Blueprint b = new Blueprint(size == 0 ? "kleines Haus" : size == 1 ? "Haus" : "grosses Haus", ahead(feet, 3), feet.getYaw());
        int roofLayers = half + 2;

        // Platz schaffen
        for (int x = -half - 1; x <= half + 1; x++)
            for (int z = -2; z <= depth; z++)
                for (int y = 1; y <= height + roofLayers + 1; y++) b.clear(x, y, z);
        // Fundament und Boden
        for (int x = -half; x <= half; x++) {
            for (int z = 0; z < depth; z++) {
                foundation(b, x, z, "cobblestone");
                boolean edge = x == -half || x == half || z == 0 || z == depth - 1;
                b.set(x, 0, z, edge ? s.wall() : s.floor());
            }
        }
        // Waende
        for (int y = 1; y <= height; y++) {
            for (int x = -half; x <= half; x++) {
                for (int z = 0; z < depth; z++) {
                    boolean sideWall = x == -half || x == half;
                    boolean frontBack = z == 0 || z == depth - 1;
                    if (!sideWall && !frontBack) continue;
                    boolean corner = sideWall && frontBack;
                    String block = corner ? pillar(s.corner()) : s.wall();
                    // Fenster
                    boolean windowRow = y == 2 || y == 3 && height >= 5;
                    if (!corner && windowRow) {
                        if (sideWall && z >= 2 && z <= depth - 3 && z % 2 == 0) block = s.window();
                        if (frontBack && Math.abs(x) >= 2 && Math.abs(x) <= half - 1 && x % 2 == 0) block = s.window();
                    }
                    b.set(x, y, z, block);
                }
            }
        }
        // Tuer vorne in der Mitte, dazu ein Weg
        b.set(0, 1, 0, s.door() + "[facing=%back%,half=lower,hinge=left]");
        b.set(0, 2, 0, s.door() + "[facing=%back%,half=upper,hinge=left]");
        b.set(0, 0, -1, "dirt_path");
        b.set(0, 0, -2, "dirt_path");
        b.set(-1, 2, -1, "wall_torch[facing=%front%]");
        b.set(1, 2, -1, "wall_torch[facing=%front%]");
        // Satteldach mit Ueberstand
        for (int k = 0; k < roofLayers; k++) {
            int y = height + 1 + k;
            int left = -half - 1 + k;
            int right = half + 1 - k;
            if (left > right) break;
            for (int z = -1; z <= depth; z++) {
                if (left == right) {
                    b.set(left, y, z, s.roofSlab() + "[type=bottom]");
                } else {
                    b.set(left, y, z, s.roofStairs() + "[facing=%right%,half=bottom]");
                    b.set(right, y, z, s.roofStairs() + "[facing=%left%,half=bottom]");
                }
            }
            // Giebel
            for (int x = left + 1; x <= right - 1; x++) {
                b.set(x, y, 0, s.wall());
                b.set(x, y, depth - 1, s.wall());
            }
        }
        // Einrichtung
        int back = depth - 2;
        b.set(-half + 1, 1, back, "white_bed[facing=%back%,part=head]");
        b.set(-half + 1, 1, back - 1, "white_bed[facing=%back%,part=foot]");
        b.set(half - 1, 1, back, "crafting_table");
        b.set(half - 2, 1, back, "furnace[facing=%front%]");
        b.set(half - 1, 1, back - 1, "chest[facing=%left%]");
        b.set(-half + 1, 3, depth / 2, "wall_torch[facing=%right%]");
        b.set(half - 1, 3, depth / 2, "wall_torch[facing=%left%]");
        b.set(0, 3, depth - 2, "wall_torch[facing=%front%]");
        if (size >= 1) {
            b.set(-half + 1, 1, 1, "barrel[facing=up]");
            b.set(-half + 2, 1, back, "flower_pot");
            b.set(0, 1, depth / 2, "red_carpet");
        }
        b.finish();
        return b;
    }

    private static String pillar(String block) {
        return block.endsWith("_log") || block.endsWith("_pillar") ? block + "[axis=y]" : block;
    }

    /** Fuellt unter dem Boden Luft und Wasser auf, damit nichts in der Luft haengt. */
    private static void foundation(Blueprint b, int x, int z, String block) {
        for (int y = -1; y >= -6; y--) {
            Block below = b.block(x, y, z);
            if (below == null || Blocks.floor(below) && !below.getType().isAir()) break;
            b.set(x, y, z, block);
        }
    }

    // ================================================================== Turm

    public static Blueprint tower(Location feet, int size, Style s) {
        int half = size == 2 ? 3 : 2;
        int width = half * 2 + 1;
        int height = size == 0 ? 10 : size == 1 ? 14 : 20;
        String wall = s == Style.WOOD ? "stone_bricks" : s.wall();
        Blueprint b = new Blueprint("Turm", ahead(feet, 3), feet.getYaw());
        for (int x = -half - 1; x <= half + 1; x++)
            for (int z = -1; z <= width; z++)
                for (int y = 1; y <= height + 3; y++) b.clear(x, y, z);
        for (int x = -half; x <= half; x++) {
            for (int z = 0; z < width; z++) {
                foundation(b, x, z, "cobblestone");
                b.set(x, 0, z, wall);
                for (int y = 1; y <= height; y++) {
                    boolean edge = x == -half || x == half || z == 0 || z == width - 1;
                    if (edge) {
                        boolean slit = y % 4 == 2 && (x == 0 || z == half) && !(z == 0 && x == 0);
                        b.set(x, y, z, slit ? "glass_pane" : wall);
                    }
                }
                // Plattform oben, mit Loch fuer die Leiter
                if (!(x == 0 && z == width - 2)) b.set(x, height + 1, z, wall);
            }
        }
        // Zinnen
        for (int x = -half; x <= half; x++) {
            for (int z = 0; z < width; z++) {
                boolean edge = x == -half || x == half || z == 0 || z == width - 1;
                if (edge && (x + z) % 2 == 0) b.set(x, height + 2, z, wall);
            }
        }
        b.set(0, 1, 0, s.door() + "[facing=%back%,half=lower,hinge=left]");
        b.set(0, 2, 0, s.door() + "[facing=%back%,half=upper,hinge=left]");
        for (int y = 1; y <= height + 1; y++) b.set(0, y, width - 2, "ladder[facing=%front%]");
        b.set(-half + 1, 3, 1, "wall_torch[facing=%back%]");
        b.set(half - 1, height + 2, 1, "torch");
        b.set(-half + 1, height + 2, width - 2, "torch");
        b.finish();
        return b;
    }

    // ================================================================== Zaun und Mauer

    /** Zaun (hoch = 1) oder Mauer (hoch = 3) im Quadrat um den Spieler, Tor in Blickrichtung. */
    public static Blueprint ring(Location feet, int radius, boolean wall, Style s) {
        Location o = new Location(feet.getWorld(), feet.getBlockX(), feet.getBlockY() - 1, feet.getBlockZ(), feet.getYaw(), 0);
        Blueprint b = new Blueprint(wall ? "Mauer" : "Zaun", o, feet.getYaw());
        String fence = s.fence();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                boolean edge = Math.abs(x) == radius || Math.abs(z) == radius;
                if (!edge) continue;
                int ground = groundY(b, x, z);
                boolean gate = z == radius && Math.abs(x) <= (wall ? 1 : 0);
                if (wall) {
                    for (int y = ground + 1; y <= ground + 3; y++) {
                        if (gate && y <= ground + 2) b.clear(x, y, z);
                        else b.set(x, y, z, s == Style.WOOD ? "stone_bricks" : s.wall());
                    }
                    if ((x + z) % 2 == 0 && !gate) b.set(x, ground + 4, z, s == Style.WOOD ? "stone_bricks" : s.wall());
                } else if (gate) {
                    b.set(x, ground + 1, z, fence + "_gate[facing=%back%]");
                } else {
                    b.set(x, ground + 1, z, fence);
                    if (Math.abs(x) == radius && Math.abs(z) == radius) b.set(x, ground + 2, z, "torch");
                }
            }
        }
        b.finish();
        return b;
    }

    /** Lokale Hoehe des Bodens an einer Stelle (erste feste Oberflaeche von oben). */
    private static int groundY(Blueprint b, int x, int z) {
        for (int y = 6; y >= -6; y--) {
            Block here = b.block(x, y, z);
            if (here != null && Blocks.floor(here) && !Mats_isLeaves(here)) {
                Block above = b.block(x, y + 1, z);
                if (above != null && (above.isPassable() || above.isLiquid())) return y;
            }
        }
        return 0;
    }

    private static boolean Mats_isLeaves(Block b) {
        return de.glowcube.claudeai.world.Mats.isLeaves(b.getType());
    }

    // ================================================================== Farm

    public static Blueprint farm(Location feet, int size) {
        int half = size == 0 ? 3 : size == 1 ? 4 : 6;
        int depth = half * 2 + 1;
        Blueprint b = new Blueprint("Farm", ahead(feet, 2), feet.getYaw());
        for (int x = -half - 1; x <= half + 1; x++) {
            for (int z = 0; z <= depth + 1; z++) {
                for (int y = 1; y <= 3; y++) b.clear(x, y, z);
                foundation(b, x, z, "dirt");
                boolean fence = Math.abs(x) == half + 1 || z == 0 || z == depth + 1;
                if (fence) {
                    b.set(x, 0, z, "grass_block");
                    boolean gate = z == 0 && x == 0;
                    b.set(x, 1, z, gate ? "oak_fence_gate[facing=%back%]" : "oak_fence");
                    if (Math.abs(x) == half + 1 && (z == 0 || z == depth + 1)) b.set(x, 2, z, "torch");
                    continue;
                }
                boolean water = x == 0 && z == half + 1;
                if (water) {
                    b.set(x, 0, z, "water");
                    b.set(x, -1, z, "dirt");
                } else {
                    b.set(x, 0, z, "farmland[moisture=7]");
                    b.set(x, 1, z, "wheat[age=0]");
                }
            }
        }
        b.finish();
        return b;
    }

    // ================================================================== Bruecke

    public static Blueprint bridge(Location feet, int length, Style s) {
        Location o = new Location(feet.getWorld(), feet.getBlockX(), feet.getBlockY() - 1, feet.getBlockZ(), feet.getYaw(), 0);
        Blueprint b = new Blueprint("Bruecke", o, feet.getYaw());
        World w = feet.getWorld();
        for (int z = 1; z <= length; z++) {
            for (int x = -2; x <= 2; x++) {
                Block deck = b.block(x, 0, z);
                if (deck == null) continue;
                if (deck.isPassable() || deck.isLiquid()) b.set(x, 0, z, s.floor());
                if (Math.abs(x) == 2) b.set(x, 1, z, s.fence());
                else b.clear(x, 1, z);
            }
            // Pfeiler alle 4 Bloecke
            if (z % 4 == 0) {
                for (int side : new int[] { -2, 2 }) {
                    for (int y = -1; y >= -20; y--) {
                        Block p = b.block(side, y, z);
                        if (p == null || Blocks.floor(p)) break;
                        b.set(side, y, z, pillar(s.corner()));
                    }
                }
                b.set(-2, 2, z, "lantern");
                b.set(2, 2, z, "lantern");
            }
        }
        b.finish();
        return b;
    }

    // ================================================================== Pool, Plattform, Saeule

    public static Blueprint pool(Location feet, int size) {
        int half = size == 0 ? 2 : size == 1 ? 3 : 4;
        int depth = half * 2 + 3;
        Blueprint b = new Blueprint("Pool", ahead(feet, 2), feet.getYaw());
        for (int x = -half - 1; x <= half + 1; x++) {
            for (int z = 0; z <= depth + 1; z++) {
                for (int y = 1; y <= 2; y++) b.clear(x, y, z);
                boolean rim = Math.abs(x) == half + 1 || z == 0 || z == depth + 1;
                if (rim) {
                    b.set(x, 0, z, "smooth_quartz");
                    for (int y = -1; y >= -3; y--) b.set(x, y, z, "smooth_quartz");
                } else {
                    for (int y = 0; y >= -3; y--) b.set(x, y, z, "water");
                    b.set(x, -4, z, "prismarine_bricks");
                    if ((x + z) % 4 == 0) b.set(x, -4, z, "sea_lantern");
                }
            }
        }
        b.finish();
        return b;
    }

    public static Blueprint platform(Location feet, int size, Style s) {
        int half = size == 0 ? 3 : size == 1 ? 5 : 8;
        Location o = new Location(feet.getWorld(), feet.getBlockX(), feet.getBlockY() - 1, feet.getBlockZ(), feet.getYaw(), 0);
        Blueprint b = new Blueprint("Plattform", o, feet.getYaw());
        String block = s == Style.WOOD ? "stone_bricks" : s.wall();
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = 1; y <= 3; y++) b.clear(x, y, z);
                b.set(x, 0, z, block);
            }
        }
        b.finish();
        return b;
    }

    public static Blueprint pillar(Location feet, int height, Style s) {
        Blueprint b = new Blueprint("Saeule", ahead(feet, 2), feet.getYaw());
        String block = s == Style.WOOD ? "quartz_pillar[axis=y]" : pillar(s.corner());
        for (int y = 1; y <= Math.max(2, Math.min(height, 64)); y++) b.set(0, y, 0, block);
        b.set(0, Math.max(2, Math.min(height, 64)) + 1, 0, "lantern");
        b.finish();
        return b;
    }
}
