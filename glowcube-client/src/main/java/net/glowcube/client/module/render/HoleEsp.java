package net.glowcube.client.module.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Render3D;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Zeigt Loecher, in die man sich stellen kann, um Explosionen zu ueberstehen.
 *
 * Gesucht wird ein Feld, in dem der Spieler steht (zwei Bloecke Luft), dessen
 * Boden und vier Seiten geschlossen sind. Bedrock haelt alles aus, Obsidian
 * fast alles - deshalb die zwei Farben. Nachempfunden HoleESP aus BleachHack
 * (GPL-3.0).
 */
public final class HoleEsp extends Module {
    private static final int BEDROCK = 0xFF4DE3FF;
    private static final int OBSIDIAN = 0xFFC46BFF;

    private final NumberSetting range = register(new NumberSetting("Range",
            "Umkreis in Bloecken", 12, 4, 32, 2));
    private final BooleanSetting nurBedrock = register(new BooleanSetting("OnlyBedrock",
            "Nur die wirklich sicheren zeigen", false));

    /** Gefundene Loecher mit ihrer Farbe - einmal je Tick gefuellt. */
    private final java.util.Map<BlockPos, Integer> gefunden = new java.util.LinkedHashMap<>();

    public HoleEsp() {
        super("HoleESP", "Zeigt sichere Loecher", Category.RENDER);
    }

    @Override
    public void onDisable() {
        gefunden.clear();
    }

    /**
     * Gesucht wird im Tick, nicht im Bild.
     *
     * Vorher lief die Suche bei jedem Frame - bei Reichweite 12 waren das
     * 15.625 Bloecke mal sechzig Bilder je Sekunde. Einmal je Tick reicht
     * voellig, denn Loecher entstehen nicht zwischen zwei Bildern. Senkrecht
     * wird ausserdem nur ein schmales Band geprueft: ein Loch drei Stockwerke
     * ueber einem nuetzt nichts.
     */
    @Override
    public void onTick() {
        gefunden.clear();
        int radius = range.getInt();
        BlockPos mitte = player().blockPosition();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -3; y <= 3; y++) {
                    BlockPos stelle = mitte.offset(x, y, z);
                    int farbe = pruefen(stelle);
                    if (farbe != 0) {
                        gefunden.put(stelle, farbe);
                    }
                }
            }
        }
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        for (var eintrag : gefunden.entrySet()) {
            Render3D.box(context, new AABB(eintrag.getKey()).deflate(0.05),
                    eintrag.getValue(), true);
        }
    }

    /** 0 heisst: kein Loch. Sonst die Farbe nach Widerstandsfaehigkeit. */
    private int pruefen(BlockPos stelle) {
        // Zwei Bloecke Platz zum Stehen.
        if (!level().getBlockState(stelle).isAir()
                || !level().getBlockState(stelle.above()).isAir()) {
            return 0;
        }
        BlockPos[] waende = {
                stelle.below(), stelle.north(), stelle.south(), stelle.east(), stelle.west()
        };
        boolean allesBedrock = true;
        for (BlockPos wand : waende) {
            var block = level().getBlockState(wand).getBlock();
            if (block == Blocks.BEDROCK) {
                continue;
            }
            allesBedrock = false;
            if (block != Blocks.OBSIDIAN && block != Blocks.CRYING_OBSIDIAN
                    && block != Blocks.RESPAWN_ANCHOR && block != Blocks.ANCIENT_DEBRIS) {
                return 0;
            }
        }
        if (nurBedrock.get() && !allesBedrock) {
            return 0;
        }
        return allesBedrock ? BEDROCK : OBSIDIAN;
    }
}
