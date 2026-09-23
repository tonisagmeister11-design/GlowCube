package net.glowcube.plugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Eine Blockposition - schlanker als Location, mit den Rechnungen, die der Agent braucht. */
record Pos(int x, int y, int z) {
    static Pos von(Location l) {
        return new Pos(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    Pos plus(int dx, int dy, int dz) {
        return new Pos(x + dx, y + dy, z + dz);
    }

    Pos hoch() {
        return new Pos(x, y + 1, z);
    }

    Pos hoch(int n) {
        return new Pos(x, y + n, z);
    }

    Pos runter() {
        return new Pos(x, y - 1, z);
    }

    int manhattan(Pos o) {
        return Math.abs(x - o.x) + Math.abs(y - o.y) + Math.abs(z - o.z);
    }

    double abstandQ(Pos o) {
        double dx = x - o.x;
        double dy = y - o.y;
        double dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    long schluessel() {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    Block block(World w) {
        return w.getBlockAt(x, y, z);
    }

    /** Mitte der Unterseite - dort stehen die Fuesse. */
    Location fuesse(World w) {
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    Location mitte(World w) {
        return new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }
}
