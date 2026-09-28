package de.gtacity.client.hud;

import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.world.CityLayout;
import de.gtacity.world.Lot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Radar in the bottom left corner. The city is a pure function of the coordinates, so the map is computed from
 * {@link CityLayout} directly (no chunk scanning). A slightly larger area is cached and only recomputed after the
 * player moved a few blocks.
 */
public final class Minimap {
    private Minimap() {
    }

    public static final int SIZE = 96;
    private static final int SCALE = 2;   // blocks per pixel
    private static final int MARGIN = 12; // cached pixels around the visible area
    private static final int CACHE = SIZE + 2 * MARGIN;
    private static final int[] PIXELS = new int[CACHE * CACHE];
    private static int cachedX = Integer.MIN_VALUE;
    private static int cachedZ = Integer.MIN_VALUE;

    private static int colorAt(int x, int z) {
        int d = CityLayout.outsideDistance(x, z);
        if (d > 0) {
            if (d <= CityLayout.PROMENADE) {
                return 0xFFB8B8B8;
            }
            return d <= CityLayout.PROMENADE + 18 ? 0xFFE3D7A0 : 0xFF2C5E9E;
        }
        if (CityLayout.isRoad(x, z)) {
            return 0xFF3A3A40;
        }
        if (CityLayout.isSidewalk(x, z)) {
            return 0xFF9A9AA0;
        }
        int gx = CityLayout.cell(x), gz = CityLayout.cell(z);
        if (CityLayout.isParkCell(gx, gz)) {
            return 0xFF3E8E41;
        }
        Lot lot = CityLayout.lotAt(x, z);
        if (lot == null) {
            return 0xFF707070;
        }
        return switch (lot.type) {
            case SKYSCRAPER -> 0xFF5F7187;
            case OFFICE -> 0xFF7E7A74;
            case HOUSE -> 0xFFB98E62;
            case VILLA -> 0xFFE0D8C8;
            case WAREHOUSE, CONTAINERS -> 0xFF8A7F6A;
            case PARKING, GAS_STATION -> 0xFF55555C;
            case POCKET_PARK, COURTYARD -> 0xFF4E9A4E;
            case BANK -> 0xFFE8C040;
            case AMMU_NATION -> 0xFFE04040;
            case STORE -> 0xFF40D060;
            case POLICE -> 0xFF4070F0;
            case HOSPITAL -> 0xFFFFFFFF;
            case CAR_DEALER -> 0xFFB060E0;
        };
    }

    private static void rebuild(int cx, int cz) {
        int half = CACHE / 2;
        for (int py = 0; py < CACHE; py++) {
            for (int px = 0; px < CACHE; px++) {
                PIXELS[py * CACHE + px] = colorAt(cx + (px - half) * SCALE, cz + (py - half) * SCALE);
            }
        }
        cachedX = cx;
        cachedZ = cz;
    }

    public static void render(GuiGraphics g, Minecraft mc, LocalPlayer player, int left, int top) {
        int px = Mth.floor(player.getX()), pz = Mth.floor(player.getZ());
        int limit = (MARGIN - 1) * SCALE;
        if (Math.abs(px - cachedX) > limit || Math.abs(pz - cachedZ) > limit) {
            rebuild(px - Math.floorMod(px, SCALE), pz - Math.floorMod(pz, SCALE));
        }
        int ox = MARGIN + Math.floorDiv(px - cachedX, SCALE);
        int oz = MARGIN + Math.floorDiv(pz - cachedZ, SCALE);

        g.fill(left - 2, top - 2, left + SIZE + 2, top + SIZE + 2, 0xC0000000);
        for (int y = 0; y < SIZE; y++) {
            int row = (y + oz) * CACHE + ox;
            int start = 0;
            int color = PIXELS[row];
            for (int x = 1; x <= SIZE; x++) {
                int c = x < SIZE ? PIXELS[row + x] : 0;
                if (x == SIZE || c != color) {
                    g.fill(left + start, top + y, left + x, top + y + 1, color);
                    start = x;
                    color = c;
                }
            }
        }

        int half = SIZE / 2;
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                int dx = (Mth.floor(e.getX()) - px) / SCALE, dz = (Mth.floor(e.getZ()) - pz) / SCALE;
                if (e == player || Math.abs(dx) >= half - 1 || Math.abs(dz) >= half - 1) {
                    continue;
                }
                int color;
                if (e instanceof PoliceEntity) {
                    color = (player.tickCount / 5) % 2 == 0 ? 0xFFFF3030 : 0xFF3060FF;
                } else if (e instanceof CarEntity car && car.getVariant() == CarVariant.POLICE) {
                    color = 0xFF3060FF;
                } else {
                    continue;
                }
                g.fill(left + half + dx - 1, top + half + dz - 1, left + half + dx + 2, top + half + dz + 2, color);
            }
        }

        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        for (int i = 0; i <= 4; i++) {
            int ax = Math.round(fx * i), az = Math.round(fz * i);
            g.fill(left + half + ax - 1, top + half + az - 1, left + half + ax + 1, top + half + az + 1, 0xFFFFFFFF);
        }
        g.fill(left + half - 2, top + half - 2, left + half + 2, top + half + 2, 0xFFFFE040);
        g.drawString(mc.font, "N", left + half - 2, top + 1, 0xFFFFFFFF, true);
    }
}
