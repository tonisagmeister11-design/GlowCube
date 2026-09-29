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
        JOB("J", "Jobcenter (Jobs, Story)", 0xFF2A8AE0, 0xFFFFFFFF, 0),
        DOCKS("D", "Hafenbüro (illegale Jobs)", 0xFF8A1E1E, 0xFFFFFFFF, 0),
        HOSPITAL("+", "Krankenhaus", 0xFFE84040, 0xFFFFFFFF, 2),
        POLICE("P", "Polizei", 0xFF3A6BE0, 0xFFFFFFFF, 3),
        AMMU_NATION("W", "Waffenladen", 0xFFB02020, 0xFFFFFFFF, 4),
        STORE("M", "Supermarkt 24/7", 0xFF2FA84F, 0xFFFFFFFF, 5),
        BANK("$", "Bank", 0xFFE0B020, 0xFF202020, 6),
        CAR_DEALER("A", "Autohaus", 0xFF9050D0, 0xFFFFFFFF, 7),
        GAS_STATION("T", "Tankstelle", 0xFFE08020, 0xFFFFFFFF, 8),
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
            case JOB_CENTER -> Kind.JOB;
            case HARBOR_OFFICE -> Kind.DOCKS;
            default -> null;
        };
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

    private static final int N = CityLayout.HALF_CELLS;
    private static final int SIDE = 2 * N + 1;
    private static final double STREET_MID = CityLayout.CORRIDOR / 2.0;

    /** Centre line of street number {@code i} (the streets run at every cell border). */
    private static double line(int i) {
        return i * CityLayout.PITCH + STREET_MID;
    }

    /** A point on a street: {@code vertical} streets run along z at x = line(index), horizontal ones along x. */
    private record Snap(boolean vertical, int index, double along) {
        double x() {
            return vertical ? line(index) : along;
        }

        double z() {
            return vertical ? along : line(index);
        }

        int segment() {
            return Math.max(-N, Math.min(N - 1, (int) Math.floor((along - STREET_MID) / CityLayout.PITCH)));
        }

        int nodeA() {
            return vertical ? node(index, segment()) : node(segment(), index);
        }

        int nodeB() {
            return vertical ? node(index, segment() + 1) : node(segment() + 1, index);
        }

        double costA() {
            return along - line(segment());
        }

        double costB() {
            return line(segment() + 1) - along;
        }
    }

    private static int node(int i, int j) {
        return (i + N) * SIDE + (j + N);
    }

    private static Snap snap(double x, double z) {
        int i = Math.max(-N, Math.min(N, (int) Math.round((x - STREET_MID) / CityLayout.PITCH)));
        int j = Math.max(-N, Math.min(N, (int) Math.round((z - STREET_MID) / CityLayout.PITCH)));
        double dv = Math.abs(x - line(i)), dh = Math.abs(z - line(j));
        if (dv <= STREET_MID && dh <= STREET_MID) {
            return new Snap(true, i, line(j)); // on a crossing: exactly that crossing, never flip between streets
        }
        if (dv <= dh) {
            return new Snap(true, i, Math.max(line(-N), Math.min(line(N), z)));
        }
        return new Snap(false, j, Math.max(line(-N), Math.min(line(N), x)));
    }

    /**
     * Route along the street grid from {@code (x, z)} to {@code (tx, tz)}: shortest way over the crossings
     * (Dijkstra), so it never cuts through a block and turns only at crossings. Returned as corner points (x, z).
     */
    public static List<double[]> route(double x, double z, double tx, double tz) {
        Snap from = snap(x, z), to = snap(tx, tz);
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{x, z});
        points.add(new double[]{from.x(), from.z()});
        boolean sameSegment = from.vertical() == to.vertical() && from.index() == to.index()
                && from.segment() == to.segment();
        if (!sameSegment) {
            double[] dist = new double[SIDE * SIDE];
            int[] prev = new int[SIDE * SIDE];
            java.util.Arrays.fill(dist, Double.MAX_VALUE);
            java.util.Arrays.fill(prev, -1);
            java.util.PriorityQueue<double[]> queue = new java.util.PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
            dist[from.nodeA()] = from.costA();
            dist[from.nodeB()] = Math.min(dist[from.nodeB()], from.costB());
            queue.add(new double[]{dist[from.nodeA()], from.nodeA()});
            queue.add(new double[]{dist[from.nodeB()], from.nodeB()});
            while (!queue.isEmpty()) {
                double[] head = queue.poll();
                int at = (int) head[1];
                if (head[0] > dist[at]) {
                    continue;
                }
                int i = at / SIDE - N, j = at % SIDE - N;
                int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] s : steps) {
                    int ni = i + s[0], nj = j + s[1];
                    if (ni < -N || ni > N || nj < -N || nj > N) {
                        continue;
                    }
                    int next = node(ni, nj);
                    double d = dist[at] + CityLayout.PITCH;
                    if (d < dist[next]) {
                        dist[next] = d;
                        prev[next] = at;
                        queue.add(new double[]{d, next});
                    }
                }
            }
            double viaA = dist[to.nodeA()] + to.costA(), viaB = dist[to.nodeB()] + to.costB();
            int end = viaA <= viaB ? to.nodeA() : to.nodeB();
            List<double[]> nodes = new ArrayList<>();
            for (int at = end; at != -1; at = prev[at]) {
                nodes.add(0, new double[]{line(at / SIDE - N), line(at % SIDE - N)});
            }
            points.addAll(nodes);
        }
        points.add(new double[]{to.x(), to.z()});
        points.add(new double[]{tx, tz});
        return simplify(points);
    }

    /** Next turn on the route: {@code right} or left, and the distance to it in blocks (or -1 if there is none). */
    public record Turn(boolean right, double distance) {
    }

    public static Turn nextTurn(List<double[]> route) {
        // route[0] is the player, route[1] the street below them; the heading is that of the first street segment.
        double length = 0;
        double[] prevDir = null;
        for (int i = 1; i + 1 < route.size(); i++) {
            double[] a = route.get(i), b = route.get(i + 1);
            double dx = b[0] - a[0], dz = b[1] - a[1];
            double len = Math.hypot(dx, dz);
            if (len < 0.5) {
                continue;
            }
            double[] dir = {dx / len, dz / len};
            if (prevDir != null) {
                double cross = prevDir[0] * dir[1] - prevDir[1] * dir[0];
                if (Math.abs(cross) > 0.5) {
                    return new Turn(cross > 0, length);
                }
            }
            length += len;
            prevDir = dir;
        }
        return null;
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
