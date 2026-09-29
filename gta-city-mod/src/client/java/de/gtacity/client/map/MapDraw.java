package de.gtacity.client.map;

import de.gtacity.registry.ModAttachments;
import de.gtacity.world.CityMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Drawing helpers shared by the minimap and the full screen map. */
public final class MapDraw {
    private MapDraw() {
    }

    public static final int ICON = 9;
    public static final int WAYPOINT_COLOR = 0xFFC050F0;
    public static final int MISSION_COLOR = 0xFFFFD020;
    public static final int VILLA_OWNED = 0xFF30B060;

    /** Where the GPS leads: the job goal if there is one, else the player's own waypoint. */
    public static double[] target(LocalPlayer player) {
        ModAttachments.Mission mission = player.getAttached(ModAttachments.MISSION);
        if (mission != null) {
            return new double[]{mission.x() + 0.5, mission.z() + 0.5};
        }
        return Waypoint.get();
    }

    public static int targetColor(LocalPlayer player) {
        return player.getAttached(ModAttachments.MISSION) != null ? MISSION_COLOR : WAYPOINT_COLOR;
    }

    /**
     * Route polyline in the north-up frame: world (x, z) maps to (cx + (x - ox) / scale, cy + (z - oz) / scale).
     * Street segments are axis aligned and drawn as single rectangles.
     */
    public static void route(GuiGraphicsExtractor g, List<double[]> points, double ox, double oz, double scale,
                             float cx, float cy, int color, int thickness) {
        for (int i = 0; i + 1 < points.size(); i++) {
            double[] a = points.get(i), b = points.get(i + 1);
            float ax = (float) (cx + (a[0] - ox) / scale), ay = (float) (cy + (a[1] - oz) / scale);
            float bx = (float) (cx + (b[0] - ox) / scale), by = (float) (cy + (b[1] - oz) / scale);
            int h = thickness / 2;
            if (Math.abs(ax - bx) < 0.5F || Math.abs(ay - by) < 0.5F) {
                int x0 = Math.round(Math.min(ax, bx)) - h, x1 = Math.round(Math.max(ax, bx)) + thickness - h;
                int y0 = Math.round(Math.min(ay, by)) - h, y1 = Math.round(Math.max(ay, by)) + thickness - h;
                g.fill(x0, y0, x1, y1, color);
            } else {
                float len = Mth.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
                int steps = Math.min(400, Math.max(1, Math.round(len)));
                for (int s = 0; s <= steps; s++) {
                    int x = Math.round(ax + (bx - ax) * s / steps), y = Math.round(ay + (by - ay) * s / steps);
                    g.fill(x - h, y - h, x - h + thickness, y - h + thickness, color);
                }
            }
        }
    }

    /** A map symbol: coloured square with a letter, like the blips on the radar. */
    public static void icon(GuiGraphicsExtractor g, Font font, int x, int y, int color, int textColor, String symbol) {
        int h = ICON / 2;
        g.fill(x - h - 1, y - h - 1, x + h + 2, y + h + 2, 0xE0101010);
        g.fill(x - h, y - h, x + h + 1, y + h + 1, color);
        g.text(font, symbol, x - font.width(symbol) / 2 + 1, y - 3, textColor, false);
    }

    public static void icon(GuiGraphicsExtractor g, Font font, int x, int y, CityMap.Place place, boolean owned) {
        if (place.kind() == CityMap.Kind.VILLA && owned) {
            icon(g, font, x, y, VILLA_OWNED, 0xFFFFFFFF, "H");
        } else {
            icon(g, font, x, y, place.kind().color, place.kind().textColor, place.kind().symbol);
        }
    }

    /** Destination marker (waypoint or job). */
    public static void flag(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x - 4, y - 4, x + 5, y + 5, 0xE0101010);
        g.fill(x - 3, y - 3, x + 4, y + 4, color);
        g.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
    }

    /** Player arrow pointing up (rotate the pose before calling to point it elsewhere). */
    public static void arrow(GuiGraphicsExtractor g, int color) {
        for (int row = 0; row < 7; row++) {
            int half = row / 2;
            g.fill(-half - 1, row - 4, half + 2, row - 3, 0xFF101010);
        }
        for (int row = 0; row < 6; row++) {
            int half = row / 2;
            g.fill(-half, row - 3, half + 1, row - 2, color);
        }
    }

    /** Remembers placed icons so no two overlap - lower priority icons are left out instead. */
    public static final class Layout {
        private final List<int[]> taken = new ArrayList<>();

        public boolean place(int x, int y, int size) {
            int h = size / 2 + 1;
            for (int[] r : taken) {
                if (x - h < r[2] && x + h > r[0] && y - h < r[3] && y + h > r[1]) {
                    return false;
                }
            }
            taken.add(new int[]{x - h, y - h, x + h, y + h});
            return true;
        }

        public void reserve(int x, int y, int size) {
            int h = size / 2 + 1;
            taken.add(new int[]{x - h, y - h, x + h, y + h});
        }
    }

    public static boolean owned(Minecraft mc, CityMap.Place place) {
        if (mc.player == null || place.kind() != CityMap.Kind.VILLA) {
            return false;
        }
        List<Long> villas = mc.player.getAttached(ModAttachments.VILLAS);
        return villas != null && villas.contains(place.id());
    }
}
