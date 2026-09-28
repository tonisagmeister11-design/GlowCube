package net.minecraft.world.level.block;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.*;
public class DoorBlock {
    public static final Property<Direction> FACING = new Property<>("facing");
    public static final Property<DoubleBlockHalf> HALF = new Property<>("half");
}
