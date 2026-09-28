package net.minecraft.core;
public enum Direction {
    DOWN(0,-1,0), UP(0,1,0), NORTH(0,0,-1), SOUTH(0,0,1), WEST(-1,0,0), EAST(1,0,0);
    public enum Axis { X, Y, Z }
    private final int x, y, z;
    Direction(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    public int getStepX() { return x; }
    public int getStepY() { return y; }
    public int getStepZ() { return z; }
    public Axis getAxis() { return x != 0 ? Axis.X : y != 0 ? Axis.Y : Axis.Z; }
    public Direction getOpposite() { return switch (this) { case DOWN -> UP; case UP -> DOWN; case NORTH -> SOUTH; case SOUTH -> NORTH; case WEST -> EAST; case EAST -> WEST; }; }
    public Direction getClockWise() { return switch (this) { case NORTH -> EAST; case EAST -> SOUTH; case SOUTH -> WEST; case WEST -> NORTH; default -> this; }; }
    public Direction getCounterClockWise() { return getClockWise().getOpposite(); }
}
