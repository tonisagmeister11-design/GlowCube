package net.glowcube.client.module.render;

import net.glowcube.client.render.WeltRender;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/** Kisten, Faesser, Shulker und Trichter durch Waende. */
public final class StorageEsp extends Module {
    private static final int CHEST   = 0xFFFFC53D;
    private static final int ENDER   = 0xFF9B6BFF;
    private static final int SHULKER = 0xFFFF5FA2;
    private static final int HOPPER  = 0xFF9AA3B8;

    private final NumberSetting range = register(new NumberSetting("Range",
            "Umkreis in Chunks", 6, 1, 16, 1));
    private final BooleanSetting chests = register(new BooleanSetting("Chests", "Kisten und Faesser", true));
    private final BooleanSetting enderChests = register(new BooleanSetting("Ender", "Enderkisten", true));
    private final BooleanSetting shulkers = register(new BooleanSetting("Shulker", "Shulkerkisten", true));
    private final BooleanSetting hoppers = register(new BooleanSetting("Hopper", "Trichter, Werfer, Spender", false));

    public StorageEsp() {
        super("StorageESP", "Behaelter durch Waende", Category.RENDER);
    }

    @Override
    public void onWorldRender(WeltRender render) {
        if (!inGame()) {
            return;
        }
        int radius = range.getInt();
        int centerX = player().chunkPosition().x;
        int centerZ = player().chunkPosition().z;

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                LevelChunk chunk = level().getChunkSource().getChunk(x, z, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    int color = colorFor(entity);
                    if (color != 0) {
                        render.box(boxFor(entity.getBlockPos()), color, true);
                    }
                }
            }
        }
    }

    private int colorFor(BlockEntity entity) {
        if (entity instanceof EnderChestBlockEntity) {
            return enderChests.get() ? ENDER : 0;
        }
        if (entity instanceof ShulkerBoxBlockEntity) {
            return shulkers.get() ? SHULKER : 0;
        }
        if (entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity) {
            return chests.get() ? CHEST : 0;
        }
        if (entity instanceof HopperBlockEntity || entity instanceof DispenserBlockEntity) {
            return hoppers.get() ? HOPPER : 0;
        }
        return 0;
    }

    /** Minimal geschrumpft, sonst liegt die Linie genau im Blockgitter und flimmert. */
    private AABB boxFor(BlockPos pos) {
        return new AABB(pos).deflate(0.02);
    }
}
