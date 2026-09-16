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
            "Umkreis in Bloecken", 32, 8, 96, 8));
    private final NumberSetting interval = register(new NumberSetting("Interval",
            "Ticks zwischen zwei Durchlaeufen", 20, 5, 100, 5));

    private static final int FARBE = 0xFF5FE3A1;

    /** Gefundene Stellen, damit nicht jeder Frame neu gesucht wird. */
    private final java.util.List<BlockPos> treffer = new java.util.ArrayList<>();
    private int ticks;

    public Search() {
        super("Search", "Markiert gesuchte Bloecke, ohne die Sicht zu veraendern",
                Category.RENDER, InputConstants.KEY_L);
    }

    @Override
    public void onEnable() {
        ticks = 0;
        treffer.clear();
    }

    @Override
    public void onDisable() {
        treffer.clear();
    }

    @Override
    public void onTick() {
        // Die Suche laeuft nicht jeden Frame, sondern in Abstaenden - sonst
        // kostet sie mehr Bilder pro Sekunde, als sie wert ist.
        if (++ticks < interval.getInt()) {
            return;
        }
        ticks = 0;
        suchen();
    }

    private void suchen() {
        treffer.clear();
        int radius = range.getInt();
        BlockPos mitte = player().blockPosition();
        BlockPos.MutableBlockPos stelle = new BlockPos.MutableBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -radius; y <= radius; y++) {
                    // Ausserhalb der Welthoehe liefert getBlockState Luft -
                    // eine eigene Pruefung braucht es dafuer nicht.
                    stelle.set(mitte.getX() + x, mitte.getY() + y, mitte.getZ() + z);
                    BlockState zustand = level().getBlockState(stelle);
                    if (zustand.isAir()) {
                        continue;
                    }
                    String id = BuiltInRegistries.BLOCK.getKey(zustand.getBlock()).toString();
                    if (blocks.contains(id)) {
                        treffer.add(stelle.immutable());
                    }
                }
            }
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
