package de.gtacity.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Big block letters for shop signs (3x5 pixel font). */
final class Signs {
    private Signs() {
    }

    private static final Map<Character, String[]> FONT = new HashMap<>();

    private static void glyph(char ch, String... rows) {
        FONT.put(ch, rows);
    }

    static {
        glyph('A', "010", "101", "111", "101", "101");
        glyph('B', "110", "101", "110", "101", "110");
        glyph('C', "011", "100", "100", "100", "011");
        glyph('D', "110", "101", "101", "101", "110");
        glyph('E', "111", "100", "110", "100", "111");
        glyph('F', "111", "100", "110", "100", "100");
        glyph('G', "011", "100", "101", "101", "011");
        glyph('H', "101", "101", "111", "101", "101");
        glyph('I', "111", "010", "010", "010", "111");
        glyph('J', "001", "001", "001", "101", "010");
        glyph('K', "101", "101", "110", "101", "101");
        glyph('L', "100", "100", "100", "100", "111");
        glyph('M', "101", "111", "111", "101", "101");
        glyph('N', "110", "101", "101", "101", "101");
        glyph('O', "010", "101", "101", "101", "010");
        glyph('P', "110", "101", "110", "100", "100");
        glyph('Q', "010", "101", "101", "110", "011");
        glyph('R', "110", "101", "110", "101", "101");
        glyph('S', "011", "100", "010", "001", "110");
        glyph('T', "111", "010", "010", "010", "010");
        glyph('U', "101", "101", "101", "101", "111");
        glyph('V', "101", "101", "101", "101", "010");
        glyph('W', "101", "101", "111", "111", "101");
        glyph('X', "101", "101", "010", "101", "101");
        glyph('Y', "101", "101", "010", "010", "010");
        glyph('Z', "111", "001", "010", "100", "111");
        glyph('0', "111", "101", "101", "101", "111");
        glyph('1', "010", "110", "010", "010", "111");
        glyph('2', "110", "001", "010", "100", "111");
        glyph('3', "110", "001", "010", "001", "110");
        glyph('4', "101", "101", "111", "001", "001");
        glyph('5', "111", "100", "110", "001", "110");
        glyph('6', "011", "100", "111", "101", "111");
        glyph('7', "111", "001", "010", "010", "010");
        glyph('8', "111", "101", "111", "101", "111");
        glyph('9', "111", "101", "111", "001", "110");
        glyph('-', "000", "000", "111", "000", "000");
        glyph('/', "001", "001", "010", "100", "100");
        glyph('$', "011", "110", "010", "011", "110");
        glyph('+', "000", "010", "111", "010", "000");
        glyph(' ', "000", "000", "000", "000", "000");
    }

    static int width(String text) {
        return text.length() * 4 - 1;
    }

    private static boolean pixel(char ch, int x, int row) {
        String[] g = FONT.get(Character.toUpperCase(ch));
        return g != null && g[row].charAt(x) == '1';
    }

    /**
     * Draws the part of a 7 block high sign board that falls into column (u, v) of a lot frame.
     *
     * @param vFront  the v row the board stands on
     * @param uCenter the u coordinate of the board centre
     * @param yBase   lowest y of the board
     * @return true if the column belongs to the board
     */
    static boolean board(Column c, int u, int v, int vFront, int uCenter, int yBase, String text, BlockState bg,
                         BlockState fg) {
        if (v != vFront) {
            return false;
        }
        int tw = width(text);
        int start = uCenter - tw / 2;
        int i = u - start;
        if (i < -1 || i > tw) {
            return false;
        }
        for (int row = 0; row < 7; row++) {
            BlockState s = bg;
            if (row >= 1 && row <= 5 && i >= 0 && i < tw) {
                int ch = i / 4, px = i % 4;
                if (px < 3 && pixel(text.charAt(ch), px, row - 1)) {
                    s = fg;
                }
            }
            c.set(yBase + 6 - row, s);
        }
        return true;
    }
}
