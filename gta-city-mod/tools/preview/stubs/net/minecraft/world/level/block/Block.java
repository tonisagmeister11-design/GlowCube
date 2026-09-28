package net.minecraft.world.level.block;
import net.minecraft.world.level.block.state.BlockState;
public class Block { public final String name; private final BlockState def; public Block(String n) { name = n; def = new BlockState(this, new java.util.TreeMap<>()); } public BlockState defaultBlockState() { return def; } }
