package net.glowcube.client.module.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Render3D;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Sucht Bloecke im Umkreis und zeichnet einen Kasten um jeden Fund.
 *
 * Der Unterschied zu X-Ray: X-Ray blendet alles andere aus und veraendert
 * damit das ganze Bild. Search laesst die Welt, wie sie ist, und markiert
 * nur - man kann also normal weiterspielen. Nachempfunden dem gleichnamigen
 * Modul aus BleachHack (GPL-3.0).
 */
public final class Search extends Module {
    private final BlockListSetting blocks = register(new BlockListSetting("Blocks",
            "Wonach gesucht wird",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
            "minecraft:ancient_debris", "minecraft:spawner", "minecraft:trial_spawner",
            "minecraft:vault", "minecraft:budding_amethyst"));
    private final NumberSetting range = register(new NumberSetting("Range",
            "Umkreis in Bloecken", 32, 8, 64, 8));
    private final NumberSetting perTick = register(new NumberSetting("LayersPerTick",
            "Wie viele Hoehenschichten je Tick geprueft werden", 6, 1, 32, 1));

    private static final int FARBE = 0xFF5FE3A1;

    /** Gefundene Stellen - gezeichnet wird aus dieser Liste, nicht neu gesucht. */
    private final java.util.List<BlockPos> treffer = new java.util.ArrayList<>();
    /** Was gerade zusammengesucht wird; erst am Ende wird umgeschaltet. */
    private java.util.List<BlockPos> imBau = new java.util.ArrayList<>();
    /** Aktuelle Hoehenschicht des laufenden Durchgangs. */
    private int schicht;

    public Search() {
        super("Search", "Markiert gesuchte Bloecke, ohne die Sicht zu veraendern",
                Category.RENDER, InputConstants.KEY_L);
    }

    @Override
    public void onEnable() {
        schicht = 0;
        treffer.clear();
        imBau = new java.util.ArrayList<>();
    }

    @Override
    public void onDisable() {
        treffer.clear();
        imBau.clear();
    }

    /**
     * Sucht schichtweise statt auf einen Schlag.
     *
     * Bei Reichweite 32 sind das 274.625 Bloecke - alle in einem Tick zu
     * pruefen laesst das Bild sichtbar stocken. Deshalb wandert der
     * Durchgang Hoehenschicht fuer Hoehenschicht durch und schaltet erst um,
     * wenn er ganz durch ist. So bleibt die angezeigte Liste immer
     * vollstaendig, statt zwischendurch halb leer zu sein.
     */
    @Override
    public void onTick() {
        int radius = range.getInt();
        int hoehen = radius * 2 + 1;
        BlockPos mitte = player().blockPosition();
        BlockPos.MutableBlockPos stelle = new BlockPos.MutableBlockPos();

        for (int n = 0; n < perTick.getInt() && schicht < hoehen; n++, schicht++) {
            int y = mitte.getY() - radius + schicht;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    stelle.set(mitte.getX() + x, y, mitte.getZ() + z);
                    BlockState zustand = level().getBlockState(stelle);
                    if (zustand.isAir()) {
                        continue;
                    }
                    String id = BuiltInRegistries.BLOCK.getKey(zustand.getBlock()).toString();
                    if (blocks.contains(id)) {
                        imBau.add(stelle.immutable());
                    }
                }
            }
        }

        if (schicht >= hoehen) {
            treffer.clear();
            treffer.addAll(imBau);
            imBau = new java.util.ArrayList<>();
            schicht = 0;
        }
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        for (BlockPos stelle : treffer) {
            Render3D.box(context, new AABB(stelle).deflate(0.02), FARBE, true);
        }
    }

    @Override
    public String hudSuffix() {
        return String.valueOf(treffer.size());
    }
}
