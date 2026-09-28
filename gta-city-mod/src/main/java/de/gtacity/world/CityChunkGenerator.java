package de.gtacity.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.gtacity.gameplay.CitySpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Chunk generator that turns the whole overworld into one big city. */
public class CityChunkGenerator extends ChunkGenerator {
    public static final MapCodec<CityChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(instance, instance.stable(CityChunkGenerator::new)));

    public CityChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
                                                       StructureManager structureManager, BiomeManager biomeManager,
                                                       @Nullable WorldGenRegion carverBiomeRegion,
                                                       Set<Holder<Biome>> possibleBiomes) {
        int minY = chunk.getMinY();
        int height = chunk.getHeight();
        Column column = new Column(minY, height);
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                column.clear();
                CityLayout.fillColumn(baseX + dx, baseZ + dz, column);
                for (int i = 0; i < height; i++) {
                    BlockState state = column.states[i];
                    if (state == null) {
                        continue;
                    }
                    int y = minY + i;
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    section.setBlockState(dx, y & 15, dz, state, false);
                    oceanFloor.update(dx, y, dz, state);
                    surface.update(dx, y, dz, state);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    private Column computeColumn(int x, int z, LevelHeightAccessor level) {
        Column column = new Column(level.getMinY(), level.getHeight());
        CityLayout.fillColumn(x, z, column);
        return column;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        Column column = computeColumn(x, z, level);
        for (int i = column.states.length - 1; i >= 0; i--) {
            BlockState state = column.states[i];
            if (state != null && type.isOpaque().test(state)) {
                return column.minY + i + 1;
            }
        }
        return level.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        Column column = computeColumn(x, z, level);
        BlockState[] states = new BlockState[column.states.length];
        for (int i = 0; i < states.length; i++) {
            states[i] = column.states[i] == null ? Blocks.AIR.defaultBlockState() : column.states[i];
        }
        return new NoiseColumn(column.minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos,
                                   SamplerContext samplerContext) {
        info.add("GTA City: " + CityLayout.describe(pos.getX(), pos.getZ()));
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        CitySpawns.onChunkGenerated(region);
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return CityLayout.SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return -64;
    }
}
