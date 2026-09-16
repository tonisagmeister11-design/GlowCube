package net.glowcube.client.module.world;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BlockListSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Baut die Bloecke im Umkreis ab.
 *
 * Drei Betriebsarten: alles, nur was auf der Liste steht, oder alles ausser
 * dem, was auf der Liste steht. Die dritte ist die nuetzlichste - so raeumt
 * man Stein weg und laesst Truhen und Erze stehen. Nachempfunden Nuker aus
 * BleachHack (GPL-3.0).
 */
public final class Nuker extends Module {
    private final NumberSetting range = register(new NumberSetting("Range",
            "Umkreis in Bloecken", 4, 1, 8, 1));
    private final ModeSetting mode = register(new ModeSetting("Mode",
            "Was abgebaut wird", "Alles", "Alles", "Nur Liste", "Ausser Liste"));
    private final NumberSetting perTick = register(new NumberSetting("PerTick",
            "Bloecke je Tick - hoch faellt auf", 1, 1, 16, 1));
    private final BlockListSetting liste = register(new BlockListSetting("Blocks",
            "Die Liste fuer die zwei Listen-Betriebsarten",
            "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
            "minecraft:shulker_box", "minecraft:spawner", "minecraft:bedrock"));

    public Nuker() {
        super("Nuker", "Baut alles im Umkreis ab", Category.WORLD);
    }

    @Override
    public void onTick() {
        int radius = range.getInt();
        int uebrig = perTick.getInt();
        BlockPos mitte = player().blockPosition();

        for (int x = -radius; x <= radius && uebrig > 0; x++) {
            for (int y = -radius; y <= radius && uebrig > 0; y++) {
                for (int z = -radius; z <= radius && uebrig > 0; z++) {
                    BlockPos stelle = mitte.offset(x, y, z);
                    if (!abbauen(stelle)) {
                        continue;
                    }
                    mc.gameMode.destroyBlock(stelle);
                    uebrig--;
                }
            }
        }
    }

    private boolean abbauen(BlockPos stelle) {
        BlockState zustand = level().getBlockState(stelle);
        if (zustand.isAir() || zustand.getBlock() == Blocks.BEDROCK) {
            return false;
        }
        if (mode.is("Alles")) {
            return true;
        }
        boolean aufListe = liste.contains(BuiltInRegistries.BLOCK.getKey(zustand.getBlock()).toString());
        return mode.is("Nur Liste") == aufListe;
    }

    @Override
    public String hudSuffix() {
        return mode.get();
    }
}
