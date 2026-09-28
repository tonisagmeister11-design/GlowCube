package de.gtacity.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/** One vertical column of blocks. A {@code null} entry means air. */
public final class Column {
    public final BlockState[] states;
    public final int minY;
    public final int maxY;

    public Column(int minY, int height) {
        this.minY = minY;
        this.maxY = minY + height - 1;
        this.states = new BlockState[height];
    }

    public void clear() {
        Arrays.fill(states, null);
    }

    public void set(int y, BlockState state) {
        int i = y - minY;
        if (i >= 0 && i < states.length) {
            states[i] = state;
        }
    }

    public void fill(int fromY, int toY, BlockState state) {
        int a = Math.max(fromY, minY);
        int b = Math.min(toY, maxY);
        for (int y = a; y <= b; y++) {
            states[y - minY] = state;
        }
    }

    public BlockState get(int y) {
        int i = y - minY;
        return i >= 0 && i < states.length ? states[i] : null;
    }
}
