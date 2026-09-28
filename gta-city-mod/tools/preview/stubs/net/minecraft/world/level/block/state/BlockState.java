package net.minecraft.world.level.block.state;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.*;
public final class BlockState {
    public final Block block; public final Map<String,Object> props;
    public BlockState(Block b, Map<String,Object> p) { block = b; props = p; }
    public <T> BlockState setValue(Property<T> prop, T value) { Map<String,Object> m = new TreeMap<>(props); m.put(prop.name, value); return new BlockState(block, m); }
    public Block getBlock() { return block; }
    public String toString() { return block.name + props; }
}
