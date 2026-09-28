import de.gtacity.world.CityLayout;
import de.gtacity.world.Column;
import net.minecraft.world.level.block.state.BlockState;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders the city generator output without Minecraft.
 * Usage: Preview iso <cx> <cz> <radius> <out.png>   (isometric 3D view)
 *        Preview map <cx> <cz> <radius> <out.png>   (top-down map, 1 px per block)
 */
public class Preview {
    static final int MIN_Y = -64, HEIGHT = 384;
    static final Map<String, Color> COLORS = new HashMap<>();

    static void c(String name, int rgb) {
        COLORS.put(name, new Color(rgb));
    }

    static {
        c("stone", 0x7F7F7F); c("deepslate", 0x505055); c("bedrock", 0x333333); c("dirt", 0x866043);
        c("grass_block", 0x6A9F3F); c("sand", 0xDCCF9E); c("sandstone", 0xD8CB9A); c("water", 0x3F76E4);
        c("gray_concrete", 0x37393E); c("yellow_concrete", 0xF1AF15); c("white_concrete", 0xCFD5D6);
        c("smooth_stone_slab", 0xA0A0A0); c("polished_andesite_slab", 0x848786); c("polished_andesite", 0x848786);
        c("smooth_stone", 0x9E9E9E); c("polished_blackstone_wall", 0x35303A); c("polished_blackstone_slab", 0x35303A);
        c("ochre_froglight", 0xF7E8A0); c("red_nether_brick_wall", 0x8B1A1A); c("cauldron", 0x3B3B3B);
        c("red_concrete", 0x8E2121); c("lime_concrete", 0x5EA918); c("black_concrete", 0x080A0F);
        c("oak_log", 0x6D5532); c("birch_log", 0xD8D7D2); c("jungle_log", 0x564419);
        c("oak_leaves", 0x3E7A22); c("birch_leaves", 0x5E8A3A); c("jungle_leaves", 0x2E8A1E);
        c("azalea_leaves", 0x5A7A2A); c("flowering_azalea_leaves", 0x7A8A4A);
        c("short_grass", 0x6A9F3F); c("poppy", 0xC02020); c("dandelion", 0xF0E020); c("azure_bluet", 0xE0E0F0);
        c("glass", 0xB0D8E8); c("sea_lantern", 0xD8E8E0); c("glowstone", 0xF0C060); c("end_rod", 0xF0F0F0);
        c("light_blue_stained_glass", 0x6699D8); c("cyan_stained_glass", 0x4C7F99); c("blue_stained_glass", 0x334CB2);
        c("light_gray_stained_glass", 0x999999); c("black_stained_glass", 0x191919); c("brown_stained_glass", 0x664C33);
        c("gray_stained_glass", 0x4C4C4C); c("green_stained_glass", 0x667F33);
        c("quartz_block", 0xECE6DF); c("smooth_quartz", 0xECE6DF); c("gold_block", 0xF6D03D);
        c("polished_deepslate", 0x48484E); c("deepslate_tiles", 0x363639); c("smooth_sandstone", 0xDFD4A0);
        c("cut_sandstone", 0xDAD09E); c("andesite", 0x888888); c("cyan_concrete", 0x157788);
        c("light_gray_concrete", 0x7D7D73); c("orange_concrete", 0xE06100); c("blue_concrete", 0x2C2E8F);
        c("light_blue_concrete", 0x2389C6); c("waxed_oxidized_cut_copper", 0x4FAB90); c("waxed_oxidized_copper", 0x52A284);
        c("bricks", 0x966455); c("stone_bricks", 0x7A7A7A); c("chiseled_stone_bricks", 0x777777);
        c("white_terracotta", 0xD1B2A1); c("orange_terracotta", 0xA15325); c("terracotta", 0x985E43);
        c("mud_bricks", 0x89684F); c("packed_mud", 0x8E6B50); c("granite", 0x956755); c("polished_granite", 0x9A6A59);
        c("pink_terracotta", 0xA14E4E); c("spruce_planks", 0x725431); c("birch_planks", 0xC0AF79);
        c("oak_planks", 0xA2834F); c("dark_oak_planks", 0x42301A); c("stripped_dark_oak_log", 0x604833);
        c("light_blue_terracotta", 0x706C8A); c("yellow_terracotta", 0xBA8523); c("brown_terracotta", 0x4D3324);
        c("dark_oak_stairs", 0x42301A); c("spruce_stairs", 0x725431); c("brick_stairs", 0x966455);
        c("deepslate_tile_stairs", 0x363639); c("mud_brick_stairs", 0x89684F); c("stone_brick_stairs", 0x7A7A7A);
        c("dark_oak_door", 0x4A3520); c("ladder", 0x8A6A3A); c("smooth_quartz_slab", 0xECE6DF);
        c("iron_trapdoor", 0xC0C0C0); c("iron_block", 0xDCDCDC); c("iron_bars", 0x9A9A9A); c("target", 0xE0B0A0);
        c("bookshelf", 0x6B5334); c("redstone_block", 0xAF1A05); c("green_concrete", 0x495B24);
        c("polished_diorite", 0xC0C0C2); c("quartz_pillar", 0xEAE4DC); c("lantern", 0xD8A040);
        c("stone_brick_slab", 0x7A7A7A); c("dirt_path", 0x94793F); c("gravel", 0x837E7E);
        c("red_terracotta", 0x8F3D2E); c("blue_terracotta", 0x4A3B5B); c("green_terracotta", 0x4C532A);
        c("cyan_terracotta", 0x575B5B); c("brown_concrete", 0x603B1F);
        c("gtacity_elevator", 0xA0A6AE); c("gtacity_bank_vault", 0x8A9098); c("gtacity_weapon_counter", 0x3A3A40);
        c("gtacity_store_counter", 0xE0E0E0); c("gtacity_car_counter", 0x202024); c("gtacity_gas_pump", 0xC03030);
        c("gtacity_atm", 0x5A6070);
    }

    static boolean seeThrough(BlockState s) {
        if (s == null) return true;
        String n = s.block.name;
        return n.contains("glass") || n.contains("leaves") || n.equals("water") || n.contains("slab") || n.contains("wall")
                || n.contains("stairs") || n.contains("door") || n.equals("ladder") || n.contains("trapdoor")
                || n.equals("end_rod") || n.equals("lantern") || n.equals("iron_bars") || n.equals("short_grass")
                || n.equals("poppy") || n.equals("dandelion") || n.equals("azure_bluet") || n.equals("cauldron");
    }

    static Color color(BlockState s) {
        Color c = COLORS.get(s.block.name);
        if (c == null) {
            System.err.println("no color: " + s.block.name);
            COLORS.put(s.block.name, c = Color.MAGENTA);
        }
        return c;
    }

    static Column[][] columns(int x0, int z0, int size) {
        Column[][] cols = new Column[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Column c = new Column(MIN_Y, HEIGHT);
                CityLayout.fillColumn(x0 + i, z0 + j, c);
                cols[i][j] = c;
            }
        }
        return cols;
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int cx = Integer.parseInt(args[1]), cz = Integer.parseInt(args[2]), r = Integer.parseInt(args[3]);
        File out = new File(args[4]);
        int size = 2 * r, x0 = cx - r, z0 = cz - r;
        long t = System.nanoTime();
        Column[][] cols = columns(x0, z0, size);
        System.out.printf("generated %d columns in %d ms%n", size * size, (System.nanoTime() - t) / 1_000_000);
        if (mode.equals("map")) {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    Column c = cols[i][j];
                    for (int k = c.states.length - 1; k >= 0; k--) {
                        if (c.states[k] != null) {
                            Color col = color(c.states[k]);
                            int y = MIN_Y + k;
                            float shade = Math.min(1.3f, 0.75f + (y - 64) / 250f);
                            img.setRGB(i, j, new Color(Math.min(255, (int) (col.getRed() * shade)),
                                    Math.min(255, (int) (col.getGreen() * shade)),
                                    Math.min(255, (int) (col.getBlue() * shade))).getRGB());
                            break;
                        }
                    }
                }
            }
            ImageIO.write(img, "png", out);
            return;
        }
        int s = Integer.parseInt(args.length > 5 ? args[5] : "4");
        int yMin = 40, yMax = args.length > 6 ? Integer.parseInt(args[6]) : 330;
        int w = size * 2 * s + 4 * s, h = size * s + (yMax - yMin) * s + 4 * s;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x9CC7F0));
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int ox = size * s, oy = (yMax - yMin) * s;
        for (int sum = 0; sum <= 2 * (size - 1); sum++) {
            for (int i = Math.max(0, sum - size + 1); i <= Math.min(size - 1, sum); i++) {
                int j = sum - i;
                Column c = cols[i][j];
                for (int y = yMin; y < yMax; y++) {
                    BlockState st = c.get(y);
                    if (st == null) continue;
                    boolean top = seeThrough(c.get(y + 1));
                    boolean east = i == size - 1 || seeThrough(cols[i + 1][j].get(y));
                    boolean south = j == size - 1 || seeThrough(cols[i][j + 1].get(y));
                    if (!top && !east && !south) continue;
                    Color col = color(st);
                    boolean glass = st.block.name.contains("glass") || st.block.name.equals("water");
                    g.setComposite(glass ? AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f)
                            : AlphaComposite.SrcOver);
                    int sx = ox + (i - j) * s, sy = oy + (i + j) * s / 2 - (y - yMin) * s;
                    // top face
                    Polygon p = new Polygon(new int[]{sx, sx + s, sx, sx - s}, new int[]{sy - s / 2, sy, sy + s / 2, sy}, 4);
                    g.setColor(col);
                    if (top) g.fillPolygon(p);
                    // south face (left)
                    if (south) {
                        g.setColor(col.darker());
                        g.fillPolygon(new int[]{sx - s, sx, sx, sx - s}, new int[]{sy, sy + s / 2, sy + s / 2 + s, sy + s}, 4);
                    }
                    // east face (right)
                    if (east) {
                        g.setColor(col.darker().darker());
                        g.fillPolygon(new int[]{sx, sx + s, sx + s, sx}, new int[]{sy + s / 2, sy, sy + s, sy + s / 2 + s}, 4);
                    }
                }
            }
        }
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }
}
