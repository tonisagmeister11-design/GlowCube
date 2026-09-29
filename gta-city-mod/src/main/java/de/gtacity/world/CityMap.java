package de.gtacity.world;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the map shows: the important places of the city with their map symbols. The city is a pure function
 * of the coordinates, so client and server compute the same list without any syncing.
 */
public final class CityMap {
    private CityMap() {
    }

    /** Map symbols. Lower priority numbers win when two symbols would overlap. */
    public enum Kind {
        HOSPITAL("+", "Krankenhaus", 0xFFE84040, 0xFFFFFFFF, 1),
        POLICE("P", "Polizei", 0xFF3A6BE0, 0xFFFFFFFF, 2),
        AMMU_NATION("W", "Waffenladen", 0xFFB02020, 0xFFFFFFFF, 3),
        STORE("M", "Supermarkt 24/7", 0xFF2FA84F, 0xFFFFFFFF, 4),
        BANK("$", "Bank", 0xFFE0B020, 0xFF202020, 5),
        CAR_DEALER("A", "Autohaus", 0xFF9050D0, 0xFFFFFFFF, 6),
        GAS_STATION("T", "Tankstelle", 0xFFE08020, 0xFFFFFFFF, 7),
        DEPOT("L", "Lieferdienst (Job)", 0xFF8A6A3A, 0xFFFFFFFF, 8),
        VILLA("V", "Villa (zu verkaufen)", 0xFF707070, 0xFFFFFFFF, 9);

        public final String symbol;
        public final String label;
        public final int color;
        public final int textColor;
        public final int priority;

        Kind(String symbol, String label, int color, int textColor, int priority) {
            this.symbol = symbol;
            this.label = label;
            this.color = color;
            this.textColor = textColor;
            this.priority = priority;
        }
    }

    /** One place on the map. {@code entrance} is the sidewalk in front of it (where GPS routes end). */
    public record Place(Kind kind, int x, int z, BlockPos entrance, Lot lot) {
        /** Stable id (packed entrance position), used for villas. */
        public long id() {
            return entrance.asLong();
        }
    }

    private static List<Place> places;

    public static synchronized List<Place> places() {
        if (places == null) {
            List<Place> list = new ArrayList<>();
            for (int gx = -CityLayout.HALF_CELLS; gx < CityLayout.HALF_CELLS; gx++) {
                for (int gz = -CityLayout.HALF_CELLS; gz < CityLayout.HALF_CELLS; gz++) {
                    if (CityLayout.isParkCell(gx, gz)) {
                        continue;
                    }
                    int n = CityLayout.lotsPerSide(gx, gz);
                    for (int qx = 0; qx < n; qx++) {
                        for (int qz = 0; qz < n; qz++) {
                            Lot lot = new Lot(gx, gz, qx, qz, n);
                            Kind kind = kindOf(lot);
                            if (kind != null && lot.front != null) {
                                list.add(new Place(kind, lot.centerX(), lot.centerZ(), CityPlaces.entrance(lot), lot));
                            }
                        }
                    }
                }
            }
            list.sort((a, b) -> Integer.compare(a.kind().priority, b.kind().priority));
            places = Collections.unmodifiableList(list);
        }
        return places;
    }

    private static Kind kindOf(Lot lot) {
        return switch (lot.type) {
            case HOSPITAL -> Kind.HOSPITAL;
            case POLICE -> Kind.POLICE;
            case AMMU_NATION -> Kind.AMMU_NATION;
            case STORE -> Kind.STORE;
            case BANK -> Kind.BANK;
            case CAR_DEALER -> Kind.CAR_DEALER;
            case GAS_STATION -> Kind.GAS_STATION;
            case VILLA -> Kind.VILLA;
            case WAREHOUSE -> isDepot(lot) ? Kind.DEPOT : null;
            default -> null;
        };
    }

    /** A few warehouses in the harbour serve as depots for the delivery job. */
    private static boolean isDepot(Lot lot) {
        return lot.qx == 0 && lot.qz == 0 && Math.floorMod(lot.gx + lot.gz, 3) == 0;
    }

    public static List<Place> of(Kind kind) {
        List<Place> list = new ArrayList<>();
        for (Place place : places()) {
            if (place.kind() == kind) {
                list.add(place);
            }
        }
        return list;
    }

    public static Place nearest(Kind kind, double x, double z) {
        Place best = null;
        double bestDist = Double.MAX_VALUE;
        for (Place place : places()) {
            if (place.kind() != kind) {
                continue;
            }
            double dx = place.x() - x, dz = place.z() - z;
            double d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = place;
            }
        }
        return best;
    }

    public static Place villa(long id) {
        for (Place place : places()) {
            if (place.kind() == Kind.VILLA && place.id() == id) {
                return place;
            }
        }
        return null;
    }

    /** Villa price: $150,000 to $400,000 depending on the plot. */
    public static int villaPrice(Place villa) {
        return 150_000 + Hash.range(villa.lot().seed(3), 26) * 10_000;
    }

    public static String villaName(Place villa) {
        return "Hills-Villa Nr. " + (Math.floorMod(villa.lot().seed(4), 90) + 10);
    }

    // ------------------------------------------------------------------ GPS

    /**
     * Route along the street grid from {@code (x, z)} to {@code (tx, tz)}. Every cell border is a street, so the
     * route drives to the next crossing, then along one street and around one corner to the crossing next to the
     * goal. Returned as corner points (x, z pairs) including start and goal.
     */
    public static List<double[]> route(double x, double z, double tx, double tz) {
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{x, z});
        double[] start = nearestRoadPoint(x, z);
        double[] goal = nearestRoadPoint(tx, tz);
        double sx = crossing(start[0]), sz = crossing(start[1]);
        double gx = crossing(goal[0]), gz = crossing(goal[1]);
        points.add(start);
        boolean startAlongX = onStreetAlongX(start[0], start[1]);
        // Leave the current street in its own direction first (no U-turn through a building).
        if (startAlongX) {
            points.add(new double[]{sx, start[1]});
        } else {
            points.add(new double[]{start[0], sz});
        }
        double[] corner = startAlongX ? new double[]{sx, gz} : new double[]{gx, sz};
        points.add(corner);
        points.add(new double[]{gx, gz});
        boolean goalAlongX = onStreetAlongX(goal[0], goal[1]);
        if (goalAlongX) {
            points.add(new double[]{goal[0], gz});
        } else {
            points.add(new double[]{gx, goal[1]});
        }
        points.add(goal);
        points.add(new double[]{tx, tz});
        return simplify(points);
    }

    /** Street centre coordinate of the grid line closest to {@code c}. */
    private static double crossing(double c) {
        double center = CityLayout.CORRIDOR / 2.0;
        double cell = Math.floor((c - center) / CityLayout.PITCH + 0.5);
        cell = Math.max(-CityLayout.HALF_CELLS, Math.min(CityLayout.HALF_CELLS, cell));
        return cell * CityLayout.PITCH + center;
    }

    private static boolean onStreetAlongX(double x, double z) {
        return CityLayout.local((int) Math.floor(z)) < CityLayout.CORRIDOR;
    }

    /** Closest point on a street centre line. */
    private static double[] nearestRoadPoint(double x, double z) {
        double cx = crossing(x), cz = crossing(z);
        double min = CityLayout.CITY_MIN + CityLayout.CORRIDOR / 2.0;
        double max = CityLayout.CITY_MAX - CityLayout.CORRIDOR / 2.0;
        double px = Math.max(min, Math.min(max, x));
        double pz = Math.max(min, Math.min(max, z));
        // Either on the vertical street at cx or on the horizontal street at cz - whichever is closer.
        return Math.abs(px - cx) < Math.abs(pz - cz) ? new double[]{cx, pz} : new double[]{px, cz};
    }

    private static List<double[]> simplify(List<double[]> points) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : points) {
            if (!out.isEmpty()) {
                double[] last = out.get(out.size() - 1);
                if (Math.abs(last[0] - p[0]) < 0.5 && Math.abs(last[1] - p[1]) < 0.5) {
                    continue;
                }
            }
            out.add(p);
        }
        // Drop points in the middle of straight segments.
        for (int i = out.size() - 2; i >= 1; i--) {
            double[] a = out.get(i - 1), b = out.get(i), c = out.get(i + 1);
            boolean straight = (Math.abs(a[0] - b[0]) < 0.5 && Math.abs(b[0] - c[0]) < 0.5)
                    || (Math.abs(a[1] - b[1]) < 0.5 && Math.abs(b[1] - c[1]) < 0.5);
            if (straight) {
                out.remove(i);
            }
        }
        return out;
    }
}
