package net.glowcube.client.karte;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Was die Karte zeigt: je Blocksaeule Farbe und Hoehe, zwischengespeichert.
 *
 * <p>Jede Saeule wird hoechstens alle paar Sekunden neu gelesen, und je Bild
 * nur eine begrenzte Zahl ({@link #BUDGET}) - so baut sich eine grosse Karte
 * in wenigen Bildern auf, statt einen Ruckler zu machen. Gelesen wird nur aus
 * geladenen Chunks; was der Client nicht kennt, bleibt dunkel.
 *
 * <p>Die Farben sind dieselben wie auf der Vanilla-Karte ({@link MapColor}),
 * samt der Schattierung: heller, wo es nach Norden hin ansteigt, dunkler, wo
 * es abfaellt.
 */
public final class KartenSpeicher {
    private static final int BUDGET = 6000;
    private static final long ALTER_MS = 4000;
    /** Zeit relativ zum Start, damit sie in die obersten 24 Bit passt. */
    private static final long START = System.currentTimeMillis();

    /** Schluessel: Saeule. Wert: Zeit (ms/64) << 40 | (Hoehe + 4096) << 24 | RGB. */
    private static final Long2LongOpenHashMap SPEICHER = new Long2LongOpenHashMap();
    private static Object welt;
    private static boolean hoehlenModus;
    private static int hoehlenY;
    private static int budget;

    private KartenSpeicher() {
    }

    /** Einmal je Bild vor dem Lesen: Budget auffuellen, bei Weltwechsel leeren. */
    public static void neuesBild(boolean hoehle, int spielerY) {
        Minecraft mc = Minecraft.getInstance();
        budget = BUDGET;
        if (mc.level != welt || hoehle != hoehlenModus
                || (hoehle && Math.abs(spielerY - hoehlenY) > 6) || SPEICHER.size() > 400_000) {
            SPEICHER.clear();
            welt = mc.level;
            hoehlenModus = hoehle;
            hoehlenY = spielerY;
        }
    }

    /** Ob der Spieler unter einer Decke steht (Hoehle, Nether) - dann Hoehlenkarte. */
    public static boolean unterDecke() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && !mc.level.canSeeSky(mc.player.blockPosition().above());
    }

    private static long schluessel(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | ((long) z << 32);
    }

    /** RGB (ohne Alpha) mit Schattierung, oder -1 fuer "unbekannt". */
    public static int farbe(int x, int z) {
        long eintrag = eintrag(x, z);
        if (eintrag == -1) {
            return -1;
        }
        int rgb = (int) (eintrag & 0xFFFFFF);
        int hoehe = hoehe(eintrag);
        long norden = eintrag(x, z - 1);
        int faktor = 220;
        if (norden != -1) {
            int hn = hoehe(norden);
            faktor = hoehe > hn ? 255 : hoehe < hn ? 180 : 220;
        }
        int r = ((rgb >> 16) & 0xFF) * faktor / 255;
        int g = ((rgb >> 8) & 0xFF) * faktor / 255;
        int b = (rgb & 0xFF) * faktor / 255;
        return (r << 16) | (g << 8) | b;
    }

    private static int hoehe(long eintrag) {
        return (int) ((eintrag >> 24) & 0xFFFF) - 4096;
    }

    private static long eintrag(int x, int z) {
        long k = schluessel(x, z);
        long jetzt = ((System.currentTimeMillis() - START) >> 6) & 0xFFFFFFL;
        long alt = SPEICHER.getOrDefault(k, -1L);
        if (alt != -1L && alt >= 0 && (jetzt - (alt >>> 40)) * 64 < ALTER_MS) {
            return alt & 0xFFFFFFFFFFL;
        }
        if (budget <= 0) {
            return alt == -1L ? -1 : alt & 0xFFFFFFFFFFL;
        }
        budget--;
        long neu = lesen(x, z);
        if (neu == -1) {
            SPEICHER.remove(k);
            return -1;
        }
        SPEICHER.put(k, (jetzt << 40) | neu);
        return neu;
    }

    /** Liest eine Saeule aus der Welt: (Hoehe + 4096) << 24 | RGB, oder -1. */
    private static long lesen(int x, int z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.hasChunk(x >> 4, z >> 4)) {
            return -1;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int y;
        int unten = level.getMinY();
        if (hoehlenModus) {
            // Von knapp ueber dem Spieler abwaerts: der erste feste Block mit
            // Luft darueber ist der Hoehlenboden.
            y = hoehlenY + 1;
            boolean luftDarueber = false;
            int grenze = Math.max(unten, hoehlenY - 40);
            for (; y >= grenze; y--) {
                pos.set(x, y, z);
                boolean luft = level.getBlockState(pos).isAir();
                if (!luft && luftDarueber) {
                    break;
                }
                luftDarueber = luft;
            }
            if (y < grenze) {
                return (long) (hoehlenY + 4096) << 24;
            }
        } else {
            y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        }
        // Glas und Aehnliches haben keine Kartenfarbe - dann tiefer schauen.
        for (int i = 0; i < 16 && y >= unten; i++, y--) {
            pos.set(x, y, z);
            BlockState zustand = level.getBlockState(pos);
            MapColor farbe = zustand.getMapColor(level, pos);
            if (farbe != MapColor.NONE) {
                int rgb = farbe.col;
                if (!zustand.getFluidState().isEmpty()) {
                    // Wasser mit Tiefe: je tiefer, desto dunkler.
                    int tiefe = 0;
                    BlockPos.MutableBlockPos drunter = new BlockPos.MutableBlockPos(x, y - 1, z);
                    while (tiefe < 12 && !level.getBlockState(drunter).getFluidState().isEmpty()) {
                        tiefe++;
                        drunter.move(0, -1, 0);
                    }
                    int f = 255 - tiefe * 9;
                    rgb = ((((rgb >> 16) & 0xFF) * f / 255) << 16) | ((((rgb >> 8) & 0xFF) * f / 255) << 8)
                            | ((rgb & 0xFF) * f / 255);
                }
                return ((long) (y + 4096) << 24) | (rgb & 0xFFFFFF);
            }
        }
        return (long) (y + 4096) << 24;
    }
}
