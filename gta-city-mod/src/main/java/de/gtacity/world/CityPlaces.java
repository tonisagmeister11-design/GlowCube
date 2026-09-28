package de.gtacity.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Finds important places (hospitals, police stations, spawn points) using the city layout. */
public final class CityPlaces {
    private CityPlaces() {
    }

    /** Spawn point: the sidewalk corner next to the 24/7 at the central crossing. */
    public static BlockPos spawn() {
        return new BlockPos(CityLayout.CORRIDOR - 2, CityLayout.GROUND + 2, CityLayout.CORRIDOR - 2);
    }

    /** Sidewalk in front of the nearest lot of the given type, or the spawn if none is close. */
    public static BlockPos nearest(CityLayout.LotType type, int x, int z) {
        int cx = CityLayout.cell(Math.max(CityLayout.CITY_MIN, Math.min(CityLayout.CITY_MAX - 1, x)));
        int cz = CityLayout.cell(Math.max(CityLayout.CITY_MIN, Math.min(CityLayout.CITY_MAX - 1, z)));
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int r = 0; r <= 10 && best == null; r++) {
            for (int gx = cx - r; gx <= cx + r; gx++) {
                for (int gz = cz - r; gz <= cz + r; gz++) {
                    if (Math.max(Math.abs(gx - cx), Math.abs(gz - cz)) != r) {
                        continue;
                    }
                    if (gx < -CityLayout.HALF_CELLS || gx >= CityLayout.HALF_CELLS || gz < -CityLayout.HALF_CELLS
                            || gz >= CityLayout.HALF_CELLS || CityLayout.isParkCell(gx, gz)) {
                        continue;
                    }
                    int n = CityLayout.lotsPerSide(gx, gz);
                    for (int qx = 0; qx < n; qx++) {
                        for (int qz = 0; qz < n; qz++) {
                            if (CityLayout.lotType(gx, gz, qx, qz) != type) {
                                continue;
                            }
                            Lot lot = new Lot(gx, gz, qx, qz, n);
                            BlockPos p = entrance(lot);
                            double d = p.distToCenterSqr(x, p.getY(), z);
                            if (d < bestDist) {
                                bestDist = d;
                                best = p;
                            }
                        }
                    }
                }
            }
        }
        return best != null ? best : spawn();
    }

    /** The sidewalk block right in front of the lot's entrance. */
    public static BlockPos entrance(Lot lot) {
        Direction front = lot.front == null ? Direction.NORTH : lot.front;
        Lot.Frame f = lot.frame();
        int u = f.width / 2;
        int v = -2;
        return new BlockPos(f.x(u, v), CityLayout.GROUND + 2, f.z(u, v));
    }
}
