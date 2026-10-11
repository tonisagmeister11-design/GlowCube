package de.glowcube.bedwars;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/** Fuenf Teaminseln im Kreis um eine Mittelinsel, hoch oben in der Luft. */
public final class Arena {
    public static final int RADIUS = 28;
    public static final int HALF = 4;          // Insel ist 9x9
    public static final int MID_RADIUS = 6;
    public static final int Y = 180;
    public static final int VOID_Y = Y - 25;

    public final ServerLevel level;
    public final BlockPos center;
    /** Alles, was zur Karte gehoert und nicht abgebaut werden darf. */
    public final Set<Long> map = new HashSet<>();

    public Arena(ServerLevel level, BlockPos around) {
        this.level = level;
        this.center = new BlockPos(around.getX() & ~15, Y, around.getZ() & ~15);
    }

    public BlockPos islandCenter(int index) {
        double a = Math.toRadians(-90.0 + 72.0 * index);
        return new BlockPos(center.getX() + (int) Math.round(Math.cos(a) * RADIUS), Y, center.getZ() + (int) Math.round(Math.sin(a) * RADIUS));
    }

    /** Nur fuer den Selbsttest ohne echten Spieler: haelt die Arena-Chunks im Takt. */
    public void forceLoad(boolean on) {
        int ext = RADIUS + 12;
        for (int cx = (center.getX() - ext) >> 4; cx <= (center.getX() + ext) >> 4; cx++) {
            for (int cz = (center.getZ() - ext) >> 4; cz <= (center.getZ() + ext) >> 4; cz++) {
                level.setChunkForced(cx, cz, on);
            }
        }
    }

    public boolean inside(Vec3 p) {
        return Math.abs(p.x - center.getX()) < RADIUS + 14 && Math.abs(p.z - center.getZ()) < RADIUS + 14 && p.y > Y - 60 && p.y < Y + 40;
    }

    /** Raeumt die ganze Flaeche frei und baut alles neu. */
    public void build() {
        clear();
        buildIslands();
    }

    public void clear() {
        map.clear();
        int ext = RADIUS + 12;
        for (int cx = (center.getX() - ext) >> 4; cx <= (center.getX() + ext) >> 4; cx++) {
            for (int cz = (center.getZ() - ext) >> 4; cz <= (center.getZ() + ext) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - ext; x <= center.getX() + ext; x++) {
            for (int z = center.getZ() - ext; z <= center.getZ() + ext; z++) {
                for (int y = Y - 6; y <= Y + 16; y++) {
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, air, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }
    }

    private void buildIslands() {
        // Mittelinsel
        for (int dx = -MID_RADIUS; dx <= MID_RADIUS; dx++) {
            for (int dz = -MID_RADIUS; dz <= MID_RADIUS; dz++) {
                if (dx * dx + dz * dz <= MID_RADIUS * MID_RADIUS) {
                    put(center.offset(dx, 0, dz), Blocks.END_STONE);
                }
            }
        }
        put(center.offset(4, 1, 0), Blocks.DIAMOND_BLOCK);
        put(center.offset(-4, 1, 0), Blocks.DIAMOND_BLOCK);
        put(center.offset(0, 1, 4), Blocks.EMERALD_BLOCK);
        put(center.offset(0, 1, -4), Blocks.EMERALD_BLOCK);

        // Teaminseln
        for (BwTeam team : BwTeam.values()) {
            BlockPos c = islandCenter(team.ordinal());
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    boolean rim = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                    put(c.offset(dx, 0, dz), rim ? team.wool : Blocks.END_STONE);
                }
            }
            put(generator(c).below(), Blocks.IRON_BLOCK);
        }
    }

    private void put(BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        map.add(pos.asLong());
    }

    // --- Lage der Dinge auf einer Teaminsel ------------------------------------------------

    /** Standort des Generators (Block darueber, dort entstehen die Items). */
    public static BlockPos generator(BlockPos island) {
        return island.offset(0, 2, -3);
    }

    public static BlockPos bedFoot(BlockPos island) {
        return island.offset(-1, 1, 0);
    }

    public static Direction bedFacing() {
        return Direction.EAST;
    }

    public static Vec3 spawn(BlockPos island) {
        return new Vec3(island.getX() + 1.5, island.getY() + 1.0, island.getZ() + 2.5);
    }

    public static Vec3 shop(BlockPos island) {
        return new Vec3(island.getX() + 3.5, island.getY() + 1.0, island.getZ() + 0.5);
    }

    /** Setzt das Bett des Teams auf seine Insel. */
    public void placeBed(BwTeam team) {
        BlockPos foot = bedFoot(islandCenter(team.ordinal()));
        Direction facing = bedFacing();
        BlockState state = team.bed.defaultBlockState().setValue(BedBlock.FACING, facing);
        level.setBlock(foot, state.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
        level.setBlock(foot.relative(facing), state.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
    }

    /** Alle Bettbloecke des Teams auf seiner Insel (auch ein vom Spieler selbst gesetztes Bett). */
    public java.util.List<BlockPos> bedBlocks(BwTeam team) {
        BlockPos c = islandCenter(team.ordinal());
        java.util.List<BlockPos> found = new java.util.ArrayList<>(2);
        for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
            for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (level.getBlockState(p).getBlock() == team.bed) {
                        found.add(p);
                    }
                }
            }
        }
        return found;
    }

    public void removeBed(BwTeam team) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos p : bedBlocks(team)) {
            level.setBlock(p, air, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    public boolean hasBed(BwTeam team) {
        return !bedBlocks(team).isEmpty();
    }

    /** Zu welchem Team gehoert ein Bettblock an dieser Stelle? null, wenn zu keinem. */
    public BwTeam bedOwner(BlockPos pos) {
        for (BwTeam t : BwTeam.values()) {
            BlockPos c = islandCenter(t.ordinal());
            if (Math.abs(pos.getX() - c.getX()) <= HALF + 1 && Math.abs(pos.getZ() - c.getZ()) <= HALF + 1 && Math.abs(pos.getY() - c.getY()) <= 4) {
                return t;
            }
        }
        return null;
    }
}
