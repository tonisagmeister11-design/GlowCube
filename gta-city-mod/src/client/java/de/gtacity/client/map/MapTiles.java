package de.gtacity.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import de.gtacity.GtaCity;
import de.gtacity.world.CityLayout;
import de.gtacity.world.Column;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The exact map: every pixel is one block column, computed with the very same function that generates the city
 * ({@link CityLayout#fillColumn}), coloured by the top block (roofs, pools, trees, lane markings ...). Heights give it
 * a 3D look: tall buildings cast shadows towards the south-east and have dark outlines.
 * <p>
 * The city is cut into tiles of 128 x 128 blocks that are painted on two background threads, the ones around the
 * player first, then the rest of the city. A downscaled overview (4 blocks per pixel) is built from the finished
 * tiles for zoomed out views.
 */
public final class MapTiles {
    private MapTiles() {
    }

    public static final int TILE = 128;
    public static final int ORIGIN = CityMapTexture.ORIGIN;
    public static final int COUNT = (CityMapTexture.SIZE * CityMapTexture.BLOCKS_PER_PIXEL + TILE - 1) / TILE;
    private static final int SHADOW = 36;
    private static final int OVERVIEW_STEP = 4;
    private static final int OVERVIEW_SIZE = COUNT * TILE / OVERVIEW_STEP;
    /** Above this many blocks per screen pixel the overview is drawn instead of the tiles. */
    public static final double OVERVIEW_SCALE = 2.6;

    private static final TileTexture[] TEXTURES = new TileTexture[COUNT * COUNT];
    private static final boolean[] STARTED = new boolean[COUNT * COUNT];
    private static final Queue<Object[]> DONE = new ConcurrentLinkedQueue<>();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "GTA City map painter");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static int running;
    private static int finished;
    private static NativeImage overviewPixels;
    private static TileTexture overview;
    private static boolean overviewDirty;
    private static int frame;

    // ------------------------------------------------------------------ painting (worker threads)

    private static int[] paint(int tx, int tz) {
        int x0 = ORIGIN + tx * TILE, z0 = ORIGIN + tz * TILE;
        int size = TILE + SHADOW;
        int[] height = new int[size * size];
        int[] color = new int[size * size];
        Column column = new Column(-64, 384);
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int x = x0 - SHADOW + i, z = z0 - SHADOW + j;
                column.clear();
                CityLayout.fillColumn(x, z, column);
                int k = j * size + i;
                height[k] = CityLayout.GROUND;
                color[k] = CityMapTexture.WATER;
                for (int idx = column.states.length - 1; idx >= 0; idx--) {
                    BlockState state = column.states[idx];
                    if (state == null) {
                        continue;
                    }
                    MapColor map = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    if (map == MapColor.NONE) {
                        continue;
                    }
                    height[k] = column.minY + idx;
                    if (map == MapColor.WATER) {
                        int depth = 0;
                        while (idx - depth >= 0 && column.states[idx - depth] != null
                                && column.states[idx - depth].getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                                == MapColor.WATER) {
                            depth++;
                        }
                        color[k] = depth <= 1 ? 0xFF5FA8D8 : depth <= 3 ? 0xFF3F86C0 : 0xFF2F5A8A;
                    } else {
                        color[k] = 0xFF000000 | map.col;
                    }
                    break;
                }
            }
        }
        int[] out = new int[TILE * TILE];
        for (int j = 0; j < TILE; j++) {
            for (int i = 0; i < TILE; i++) {
                int k = (j + SHADOW) * size + (i + SHADOW);
                int h = height[k];
                float light = 1.0F;
                // Sun from the north-west: something taller in that direction throws a shadow onto this column.
                for (int d = 1; d < SHADOW; d++) {
                    if (height[k - d * size - d] - d * 0.8F > h + 0.5F) {
                        light = 0.6F;
                        break;
                    }
                }
                // Sunlit edges facing the sun, dark outlines where the height jumps.
                int west = height[k - 1], north = height[k - size];
                if (h > west + 1 || h > north + 1) {
                    light *= 1.15F;
                }
                int east = i + 1 < TILE ? height[k + 1] : h, south = j + 1 < TILE ? height[k + size] : h;
                if (Math.abs(h - east) > 2 || Math.abs(h - south) > 2) {
                    light *= 0.72F;
                }
                // Higher roofs a little brighter, so height reads at a glance.
                light *= 1.0F + Math.min(0.18F, Math.max(0, h - CityLayout.FLOOR) / 500.0F);
                out[j * TILE + i] = shade(color[k], light);
            }
        }
        return out;
    }

    private static int shade(int color, float light) {
        int r = Math.min(255, (int) ((color >> 16 & 0xFF) * light));
        int g = Math.min(255, (int) ((color >> 8 & 0xFF) * light));
        int b = Math.min(255, (int) ((color & 0xFF) * light));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------ scheduling (render thread)

    private static void start(int tx, int tz) {
        int index = tz * COUNT + tx;
        STARTED[index] = true;
        running++;
        WORKERS.execute(() -> {
            int[] pixels;
            try {
                pixels = paint(tx, tz);
            } catch (Throwable t) {
                GtaCity.LOG.error("Kartenkachel {}/{} konnte nicht gezeichnet werden", tx, tz, t);
                pixels = null;
            }
            DONE.add(new Object[]{tx, tz, pixels});
        });
    }

    /** Called every frame a map is shown: uploads finished tiles and starts the next ones, nearest first. */
    public static void update(double focusX, double focusZ) {
        frame++;
        for (int n = 0; n < 6; n++) {
            Object[] done = DONE.poll();
            if (done == null) {
                break;
            }
            running--;
            finished++;
            int tx = (int) done[0], tz = (int) done[1];
            if (done[2] instanceof int[] pixels) {
                upload(tx, tz, pixels);
            }
        }
        if (overviewDirty && (frame % 15 == 0 || finished == COUNT * COUNT)) {
            overview.write(overviewPixels);
            overviewDirty = false;
        }
        if (running >= 4 || finished + running >= COUNT * COUNT) {
            return;
        }
        int fx = Math.floorDiv((int) focusX - ORIGIN, TILE), fz = Math.floorDiv((int) focusZ - ORIGIN, TILE);
        for (int r = 0; r < COUNT && running < 4; r++) {
            for (int tz = fz - r; tz <= fz + r && running < 4; tz++) {
                for (int tx = fx - r; tx <= fx + r && running < 4; tx++) {
                    if (Math.max(Math.abs(tx - fx), Math.abs(tz - fz)) != r || tx < 0 || tz < 0 || tx >= COUNT
                            || tz >= COUNT || STARTED[tz * COUNT + tx]) {
                        continue;
                    }
                    start(tx, tz);
                }
            }
        }
    }

    private static void upload(int tx, int tz, int[] pixels) {
        NativeImage image = new NativeImage(TILE, TILE, false);
        for (int j = 0; j < TILE; j++) {
            for (int i = 0; i < TILE; i++) {
                image.setPixel(i, j, pixels[j * TILE + i]);
            }
        }
        TileTexture texture = new TileTexture("tile " + tx + "/" + tz, TILE);
        texture.write(image);
        image.close();
        TEXTURES[tz * COUNT + tx] = texture;

        if (overviewPixels == null) {
            overviewPixels = new NativeImage(OVERVIEW_SIZE, OVERVIEW_SIZE, true);
            overview = new TileTexture("overview", OVERVIEW_SIZE);
        }
        int cells = TILE / OVERVIEW_STEP;
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int r = 0, g = 0, b = 0;
                for (int dj = 0; dj < OVERVIEW_STEP; dj++) {
                    for (int di = 0; di < OVERVIEW_STEP; di++) {
                        int c = pixels[(j * OVERVIEW_STEP + dj) * TILE + i * OVERVIEW_STEP + di];
                        r += c >> 16 & 0xFF;
                        g += c >> 8 & 0xFF;
                        b += c & 0xFF;
                    }
                }
                int n = OVERVIEW_STEP * OVERVIEW_STEP;
                overviewPixels.setPixel(tx * cells + i, tz * cells + j, 0xFF000000 | (r / n) << 16 | (g / n) << 8
                        | (b / n));
            }
        }
        overviewDirty = true;
    }

    public static boolean ready(int tx, int tz) {
        return tx >= 0 && tz >= 0 && tx < COUNT && tz < COUNT && TEXTURES[tz * COUNT + tx] != null;
    }

    /** Share of the city already painted, 0..1 (for the "map is being drawn" hint). */
    public static float progress() {
        return (float) finished / (COUNT * COUNT);
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the world rectangle [wx0, wx1] x [wz0, wz1] into the screen rectangle (x0, y0)-(x1, y1). The coarse
     * lot map lies underneath, so parts not painted yet still show something.
     */
    public static void draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, double wx0, double wz0,
                            double wx1, double wz1) {
        CityMapTexture.draw(g, x0, y0, x1, y1, wx0, wz0, wx1, wz1);
        update((wx0 + wx1) / 2, (wz0 + wz1) / 2);
        double sx = (x1 - x0) / (wx1 - wx0), sz = (y1 - y0) / (wz1 - wz0);
        double blocksPerPixel = 1.0 / sx;
        if (blocksPerPixel >= OVERVIEW_SCALE && overview != null) {
            double span = OVERVIEW_SIZE * (double) OVERVIEW_STEP;
            blitClipped(g, overview, x0, y0, sx, sz, wx0, wz0, ORIGIN, ORIGIN, ORIGIN + span, ORIGIN + span,
                    wx0, wz0, wx1, wz1);
            return;
        }
        int tx0 = Math.max(0, Math.floorDiv((int) Math.floor(wx0) - ORIGIN, TILE));
        int tz0 = Math.max(0, Math.floorDiv((int) Math.floor(wz0) - ORIGIN, TILE));
        int tx1 = Math.min(COUNT - 1, Math.floorDiv((int) Math.ceil(wx1) - ORIGIN, TILE));
        int tz1 = Math.min(COUNT - 1, Math.floorDiv((int) Math.ceil(wz1) - ORIGIN, TILE));
        for (int tz = tz0; tz <= tz1; tz++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                TileTexture texture = TEXTURES[tz * COUNT + tx];
                if (texture == null) {
                    continue;
                }
                double ax = ORIGIN + tx * TILE, az = ORIGIN + tz * TILE;
                blitClipped(g, texture, x0, y0, sx, sz, wx0, wz0, ax, az, ax + TILE, az + TILE, wx0, wz0, wx1, wz1);
            }
        }
    }

    /** Blits the part of a texture covering world [ax0, ax1] x [az0, az1] that lies inside the view. */
    private static void blitClipped(GuiGraphicsExtractor g, TileTexture texture, int x0, int y0, double sx,
                                    double sz, double wx0, double wz0, double ax0, double az0, double ax1,
                                    double az1, double vx0, double vz0, double vx1, double vz1) {
        double cx0 = Math.max(ax0, vx0), cz0 = Math.max(az0, vz0);
        double cx1 = Math.min(ax1, vx1), cz1 = Math.min(az1, vz1);
        if (cx1 <= cx0 || cz1 <= cz0) {
            return;
        }
        // Screen edges are rounded from world coordinates, so neighbouring tiles meet without gaps.
        int sx0 = x0 + (int) Math.round((cx0 - wx0) * sx), sx1 = x0 + (int) Math.round((cx1 - wx0) * sx);
        int sy0 = y0 + (int) Math.round((cz0 - wz0) * sz), sy1 = y0 + (int) Math.round((cz1 - wz0) * sz);
        if (sx1 <= sx0 || sy1 <= sy0) {
            return;
        }
        float u0 = (float) ((cx0 - ax0) / (ax1 - ax0)), u1 = (float) ((cx1 - ax0) / (ax1 - ax0));
        float v0 = (float) ((cz0 - az0) / (az1 - az0)), v1 = (float) ((cz1 - az0) / (az1 - az0));
        g.blit(texture.getTextureView(), texture.getSampler(), sx0, sy0, sx1, sy1, u0, u1, v0, v1);
    }

    /** A GPU texture without a CPU copy (the whole city would otherwise sit in memory twice). */
    private static final class TileTexture extends AbstractTexture {
        TileTexture(String label, int size) {
            var device = RenderSystem.getDevice();
            this.texture = device.createTexture(() -> GtaCity.MOD_ID + " map " + label, 5, GpuFormat.RGBA8_UNORM,
                    size, size, 1, 1);
            this.textureView = device.createTextureView(this.texture);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }

        void write(NativeImage image) {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, image);
        }
    }
}
