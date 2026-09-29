package de.gtacity.client.hud;

import de.gtacity.client.map.CityMapTexture;
import de.gtacity.client.map.MapDraw;
import de.gtacity.client.map.Waypoint;
import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.entity.HelicopterEntity;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.world.CityLayout;
import de.gtacity.world.CityMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * GTA style radar in the bottom left corner: the pre-painted city map, turned so that "ahead" is always up,
 * with the GPS route, map symbols (never on top of each other), police blips and the district name. It zooms out
 * when driving fast.
 */
public final class Minimap {
    private Minimap() {
    }

    public static final int WIDTH = 132;
    public static final int HEIGHT = 92;

    private static float zoom = 1.6F;

    public static void render(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer player, int left, int top,
                              float partial) {
        double px = Mth.lerp(partial, player.xo, player.getX());
        double pz = Mth.lerp(partial, player.zo, player.getZ());
        // Turns with the camera, like the GTA radar.
        float yaw = player.getViewYRot(partial);
        double angle = Math.PI - yaw * Mth.DEG_TO_RAD;
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);

        // Zoom out with speed: 1.6 blocks per pixel on foot, up to 4.5 at top speed.
        float speed = player.getVehicle() instanceof CarEntity car ? Math.abs(car.measuredSpeed) : 0.0F;
        float wantZoom = 1.6F + Math.min(2.9F, speed * 1.4F);
        zoom += (wantZoom - zoom) * 0.03F;
        double scale = zoom;

        int cx = left + WIDTH / 2, cy = top + HEIGHT * 3 / 5;
        g.fill(left - 2, top - 2, left + WIDTH + 2, top + HEIGHT + 2, 0xE0000000);
        g.enableScissor(left, top, left + WIDTH, top + HEIGHT);

        // Map and route in the rotated frame.
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate((float) angle);
        int r = (int) Math.ceil(Math.hypot(WIDTH, HEIGHT) / 2.0) + 2;
        double wr = r * scale;
        CityMapTexture.draw(g, -r, -r, r, r, px - wr, pz - wr, px + wr, pz + wr);
        double[] target = MapDraw.target(player);
        if (target != null) {
            List<double[]> route = CityMap.route(px, pz, target[0], target[1]);
            MapDraw.route(g, route, px, pz, scale, 0, 0, MapDraw.targetColor(player), 3);
        }
        g.pose().popMatrix();

        // Symbols stay upright: only their positions are turned.
        MapDraw.Layout layout = new MapDraw.Layout();
        layout.reserve(cx, cy, 9); // keep the player arrow free
        int halfW = WIDTH / 2 - 5;
        if (target != null) {
            float[] s = project(target[0] - px, target[1] - pz, scale, cos, sin);
            // Off the radar: pin the marker to the edge, like GTA does.
            float fx = s[0], fy = s[1];
            float k = Math.max(Math.abs(fx) / halfW, Math.abs(fy + (cy - top - HEIGHT / 2)) / (HEIGHT / 2 - 5));
            if (k > 1) {
                fx /= k;
                fy = (fy + (cy - top - HEIGHT / 2)) / k - (cy - top - HEIGHT / 2);
            }
            int mx = Math.round(cx + fx), my = Math.round(cy + fy);
            MapDraw.flag(g, mx, my, MapDraw.targetColor(player));
            layout.reserve(mx, my, 9);
            double dist = Math.hypot(target[0] - px, target[1] - pz);
            if (player.getAttached(de.gtacity.registry.ModAttachments.MISSION) == null && dist < 15) {
                Waypoint.clear();
            }
        }
        for (CityMap.Place place : CityMap.places()) {
            double dx = place.x() - px, dz = place.z() - pz;
            if (Math.abs(dx) > wr || Math.abs(dz) > wr) {
                continue;
            }
            float[] s = project(dx, dz, scale, cos, sin);
            int x = Math.round(cx + s[0]), y = Math.round(cy + s[1]);
            if (x < left + 5 || x > left + WIDTH - 5 || y < top + 5 || y > top + HEIGHT - 5) {
                continue;
            }
            if (layout.place(x, y, MapDraw.ICON)) {
                MapDraw.icon(g, mc.font, x, y, place, MapDraw.owned(mc, place));
            }
        }
        if (mc.level != null) {
            boolean blink = (player.tickCount / 5) % 2 == 0;
            for (Entity e : mc.level.entitiesForRendering()) {
                boolean cop = e instanceof PoliceEntity && !(e.getVehicle() instanceof HelicopterEntity);
                boolean copCar = e instanceof CarEntity car && car.getVariant() == CarVariant.POLICE;
                boolean heli = e instanceof HelicopterEntity;
                if (!cop && !copCar && !heli) {
                    continue;
                }
                float[] s = project(e.getX() - px, e.getZ() - pz, scale, cos, sin);
                int x = Math.round(cx + s[0]), y = Math.round(cy + s[1]);
                if (x < left + 2 || x > left + WIDTH - 3 || y < top + 2 || y > top + HEIGHT - 3) {
                    continue;
                }
                int color = blink ? 0xFFFF3030 : 0xFF3060FF;
                int size = heli ? 3 : copCar ? 2 : 1;
                g.fill(x - size, y - size, x + size + 1, y + size + 1, 0xFF000000);
                g.fill(x - size + 1, y - size + 1, x + size, y + size, color);
            }
        }

        // North marker on the edge.
        float[] n = project(0, -1000, 1.0, cos, sin);
        float k = Math.max(Math.abs(n[0]) / (WIDTH / 2.0F - 6), Math.abs(n[1]) / (HEIGHT / 2.0F - 6));
        int nx = Math.round(left + WIDTH / 2.0F + n[0] / k), ny = Math.round(top + HEIGHT / 2.0F + n[1] / k);
        g.fill(nx - 4, ny - 5, nx + 5, ny + 5, 0xC0000000);
        g.text(mc.font, "N", nx - 2, ny - 3, 0xFFFFFFFF, false);

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        MapDraw.arrow(g, 0xFFFFFFFF);
        g.pose().popMatrix();
        g.disableScissor();

        // District name under the radar.
        String where = CityLayout.insideCity(Mth.floor(px), Mth.floor(pz))
                ? CityLayout.districtAt(Mth.floor(px), Mth.floor(pz)).label
                : CityLayout.outsideDistance(Mth.floor(px), Mth.floor(pz)) <= CityLayout.PROMENADE + 30 ? "Strand"
                : "Pazifik";
        g.text(mc.font, where, left, top - 11, 0xFFFFFFFF, true);
        if (target != null) {
            int meters = (int) Math.round(Math.hypot(target[0] - px, target[1] - pz));
            String label = meters >= 1000 ? String.format("%.1f km", meters / 1000.0) : meters + " m";
            g.text(mc.font, label, left + WIDTH - mc.font.width(label), top - 11, MapDraw.targetColor(player), true);
        }
    }

    private static float[] project(double dx, double dz, double scale, float cos, float sin) {
        float x = (float) (dx / scale), y = (float) (dz / scale);
        return new float[]{x * cos - y * sin, x * sin + y * cos};
    }
}
