package de.gtacity.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import de.gtacity.GtaCity;
import de.gtacity.world.CityLayout;
import de.gtacity.world.Lot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Util;

import java.util.concurrent.CompletableFuture;

/**
 * The whole city painted once into one texture (2 blocks per pixel) in a dark "radar" style: light streets on dark
 * blocks, green parks, blue sea. The pixels are computed on a worker thread, the upload happens on the render
 * thread the first time the map is needed.
 */
public final class CityMapTexture {
    private CityMapTexture() {
    }

    public static final int BLOCKS_PER_PIXEL = 2;
    public static final int ORIGIN = CityLayout.CITY_MIN - CityLayout.BORDER_MARGIN;
    public static final int SIZE = (CityLayout.CITY_MAX + CityLayout.BORDER_MARGIN - ORIGIN) / BLOCKS_PER_PIXEL + 1;

    public static final int WATER = 0xFF2F5A78;
    private static final int BEACH = 0xFFCDBB8A;
    private static final int PROMENADE = 0xFF8C8C88;
    private static final int ROAD = 0xFF9AA0A8;
    private static final int ROAD_LINE = 0xFFB9BEC4;
    private static final int SIDEWALK = 0xFF4E545C;
    private static final int PARK = 0xFF3D6B3B;

    private static CompletableFuture<int[]> pixels;
    private static DynamicTexture texture;

    /** True once the texture can be drawn; starts building it on the first call. */
    public static boolean ready() {
        if (texture != null) {
            return true;
        }
        if (pixels == null) {
            pixels = CompletableFuture.supplyAsync(CityMapTexture::paint, Util.backgroundExecutor());
        }
        if (!pixels.isDone()) {
            return false;
        }
        int[] data = pixels.join();
        NativeImage image = new NativeImage(SIZE, SIZE, false);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setPixel(x, y, data[y * SIZE + x]);
            }
        }
        texture = new DynamicTexture(() -> GtaCity.MOD_ID + " city map", image);
        return true;
    }

    private static int[] paint() {
        int[] data = new int[SIZE * SIZE];
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                int x = ORIGIN + px * BLOCKS_PER_PIXEL, z = ORIGIN + py * BLOCKS_PER_PIXEL;
                data[py * SIZE + px] = colorAt(x, z);
            }
        }
        return data;
    }

    static int colorAt(int x, int z) {
        int d = CityLayout.outsideDistance(x, z);
        if (d > 0) {
            if (d <= CityLayout.PROMENADE) {
                return PROMENADE;
            }
            return d <= CityLayout.PROMENADE + 30 ? BEACH : WATER;
        }
        if (CityLayout.isRoad(x, z)) {
            // A lighter centre line makes the streets read as streets even when zoomed out.
            int lx = CityLayout.local(x), lz = CityLayout.local(z);
            int mid = CityLayout.CORRIDOR / 2;
            boolean centre = (Math.abs(lx - mid) <= 1 && lz >= CityLayout.CORRIDOR)
                    || (Math.abs(lz - mid) <= 1 && lx >= CityLayout.CORRIDOR);
            return centre ? ROAD_LINE : ROAD;
        }
        if (CityLayout.isSidewalk(x, z)) {
            return SIDEWALK;
        }
        int gx = CityLayout.cell(x), gz = CityLayout.cell(z);
        if (CityLayout.isParkCell(gx, gz)) {
            return PARK;
        }
        Lot lot = CityLayout.lotAt(x, z);
        if (lot == null) {
            return 0xFF33373D;
        }
        // Thin darker gap between neighbouring plots.
        boolean edge = x == lot.x0 || z == lot.z0 || x >= lot.x1 - 1 || z >= lot.z1 - 1;
        int base = switch (lot.type) {
            case SKYSCRAPER -> 0xFF4A5366;
            case OFFICE -> 0xFF4A4E57;
            case HOUSE -> 0xFF4D4A44;
            case VILLA -> 0xFF575244;
            case WAREHOUSE -> 0xFF4B463D;
            case CONTAINERS -> 0xFF4F4A3A;
            case PARKING -> 0xFF3C3F45;
            case POCKET_PARK, COURTYARD -> PARK;
            case BANK -> 0xFF5C5438;
            case AMMU_NATION -> 0xFF5A3C3C;
            case STORE -> 0xFF3C5A44;
            case POLICE -> 0xFF3C4A66;
            case HOSPITAL -> 0xFF645050;
            case GAS_STATION -> 0xFF5A4A38;
            case CAR_DEALER -> 0xFF4E4262;
        };
        return edge ? darker(base) : base;
    }

    private static int darker(int color) {
        int r = (color >> 16 & 0xFF) * 3 / 4, g = (color >> 8 & 0xFF) * 3 / 4, b = (color & 0xFF) * 3 / 4;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * Draws the world rectangle [wx0, wx1] x [wz0, wz1] into the screen rectangle (x0, y0)-(x1, y1).
     * Parts outside the texture show open sea.
     */
    public static void draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, double wx0, double wz0,
                            double wx1, double wz1) {
        g.fill(x0, y0, x1, y1, WATER);
        if (!ready()) {
            return;
        }
        double span = (double) SIZE * BLOCKS_PER_PIXEL;
        // Clip to the painted area (the sampler repeats, so outside it would show the city again).
        double cx0 = Math.max(wx0, ORIGIN), cz0 = Math.max(wz0, ORIGIN);
        double cx1 = Math.min(wx1, ORIGIN + span), cz1 = Math.min(wz1, ORIGIN + span);
        if (cx1 <= cx0 || cz1 <= cz0) {
            return;
        }
        double sx = (x1 - x0) / (wx1 - wx0), sz = (y1 - y0) / (wz1 - wz0);
        int sx0 = x0 + (int) Math.round((cx0 - wx0) * sx), sx1 = x0 + (int) Math.round((cx1 - wx0) * sx);
        int sy0 = y0 + (int) Math.round((cz0 - wz0) * sz), sy1 = y0 + (int) Math.round((cz1 - wz0) * sz);
        float u0 = (float) ((cx0 - ORIGIN) / span), u1 = (float) ((cx1 - ORIGIN) / span);
        float v0 = (float) ((cz0 - ORIGIN) / span), v1 = (float) ((cz1 - ORIGIN) / span);
        g.blit(texture.getTextureView(), texture.getSampler(), sx0, sy0, sx1, sy1, u0, u1, v0, v1);
    }
}
