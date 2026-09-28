package net.minecraft.world.level.block;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.*;
public class StairBlock {
    public static final Property<Direction> FACING = new Property<>("facing");
    public static final Property<Half> HALF = new Property<>("half");
}
