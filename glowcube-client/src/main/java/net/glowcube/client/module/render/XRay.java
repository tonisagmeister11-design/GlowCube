package net.glowcube.client.module.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.util.Gamma;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blendet alles aus, was nicht auf der Liste steht. Die eigentliche Arbeit
 * macht der BlockMixin - hier stehen nur Liste und Zustand.
 */
public final class XRay extends Module {
    private static XRay instance;

    private final BlockListSetting blocks = register(new BlockListSetting("Blocks",
            "Was sichtbar bleibt",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
            "minecraft:ancient_debris",
            "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
            "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
            "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
            "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
            "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
            "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
            "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
            "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore",
            "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
            "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault",
            "minecraft:budding_amethyst", "minecraft:raw_iron_block",
            "minecraft:water", "minecraft:lava", "minecraft:portal", "minecraft:end_portal_frame"));

    private final BooleanSetting brighten = register(new BooleanSetting("Brighten",
            "Helligkeit hochziehen, damit die Adern im Dunkeln stehen", true));

    public XRay() {
        super("X-Ray", "Laesst nur ausgewaehlte Bloecke stehen", Category.RENDER, InputConstants.KEY_X);
        instance = this;
    }

    @Override
    public void onEnable() {
        if (brighten.get()) {
            Gamma.acquire();
        }
        reloadChunks();
    }

    @Override
    public void onDisable() {
        if (brighten.get()) {
            Gamma.release();
        }
        reloadChunks();
    }

    /**
     * Ohne Neuaufbau der Chunk-Meshes aendert sich am Bild nichts. In 26.2
     * heisst der Aufruf nicht mehr allChanged().
     */
    private void reloadChunks() {
        if (mc.levelRenderer != null) {
            mc.levelRenderer.resetLevelRenderData();
        }
    }

    public BlockListSetting blocks() {
        return blocks;
    }

    // ------------------------------------------------- vom Mixin aus gerufen

    /** True, wenn gerade gefiltert wird. */
    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    /** True, wenn dieser Block sichtbar bleiben soll. */
    public static boolean visible(BlockState state) {
        if (instance == null) {
            return true;
        }
        return instance.blocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }
}
