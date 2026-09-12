/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.Ui;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.block.Biome;

public final class Biomes {
    private static final Map<String, String> NAMES = new HashMap<String, String>();

    private Biomes() {
    }

    private static void put(String string, String string2) {
        NAMES.put(string, string2);
    }

    public static String name(Biome biome) {
        if (biome == null) {
            return "Unbekannt";
        }
        String string = biome.getKey().getKey();
        String string2 = NAMES.get(string);
        return string2 != null ? string2 : Ui.pretty(string);
    }

    static {
        Biomes.put("badlands", "Tafelberge");
        Biomes.put("bamboo_jungle", "Bambusdschungel");
        Biomes.put("basalt_deltas", "Basaltdelta");
        Biomes.put("beach", "Strand");
        Biomes.put("birch_forest", "Birkenwald");
        Biomes.put("cherry_grove", "Kirschblütenhain");
        Biomes.put("cold_ocean", "Kalter Ozean");
        Biomes.put("crimson_forest", "Karmesinwald");
        Biomes.put("dark_forest", "Dunkler Wald");
        Biomes.put("deep_cold_ocean", "Kalter Tiefseeozean");
        Biomes.put("deep_dark", "Tiefendunkel");
        Biomes.put("deep_frozen_ocean", "Gefrorener Tiefseeozean");
        Biomes.put("deep_lukewarm_ocean", "Lauwarmer Tiefseeozean");
        Biomes.put("deep_ocean", "Tiefsee");
        Biomes.put("desert", "Wüste");
        Biomes.put("dripstone_caves", "Tropfsteinhöhlen");
        Biomes.put("end_barrens", "End-Ödland");
        Biomes.put("end_highlands", "End-Hochland");
        Biomes.put("end_midlands", "End-Mittelland");
        Biomes.put("eroded_badlands", "Erodierte Tafelberge");
        Biomes.put("flower_forest", "Blumenwald");
        Biomes.put("forest", "Wald");
        Biomes.put("frozen_ocean", "Gefrorener Ozean");
        Biomes.put("frozen_peaks", "Vereiste Gipfel");
        Biomes.put("frozen_river", "Gefrorener Fluss");
        Biomes.put("grove", "Hain");
        Biomes.put("ice_spikes", "Eiszapfentundra");
        Biomes.put("jagged_peaks", "Zerklüftete Gipfel");
        Biomes.put("jungle", "Dschungel");
        Biomes.put("lukewarm_ocean", "Lauwarmer Ozean");
        Biomes.put("lush_caves", "Üppige Höhlen");
        Biomes.put("mangrove_swamp", "Mangrovensumpf");
        Biomes.put("meadow", "Bergwiese");
        Biomes.put("mushroom_fields", "Pilzland");
        Biomes.put("nether_wastes", "Nether-Ödland");
        Biomes.put("ocean", "Ozean");
        Biomes.put("old_growth_birch_forest", "Hoher Birkenwald");
        Biomes.put("old_growth_pine_taiga", "Kiefern-Taiga");
        Biomes.put("old_growth_spruce_taiga", "Fichten-Taiga");
        Biomes.put("pale_garden", "Blasser Garten");
        Biomes.put("plains", "Ebene");
        Biomes.put("river", "Fluss");
        Biomes.put("savanna", "Savanne");
        Biomes.put("savanna_plateau", "Savannenhochebene");
        Biomes.put("small_end_islands", "Kleine End-Inseln");
        Biomes.put("snowy_beach", "Verschneiter Strand");
        Biomes.put("snowy_plains", "Verschneite Ebene");
        Biomes.put("snowy_slopes", "Verschneite Hänge");
        Biomes.put("snowy_taiga", "Verschneite Taiga");
        Biomes.put("soul_sand_valley", "Seelensandtal");
        Biomes.put("sparse_jungle", "Dschungelrand");
        Biomes.put("stony_peaks", "Steinige Gipfel");
        Biomes.put("stony_shore", "Steinige Küste");
        Biomes.put("sunflower_plains", "Sonnenblumenebene");
        Biomes.put("swamp", "Sumpf");
        Biomes.put("taiga", "Taiga");
        Biomes.put("the_end", "Das Ende");
        Biomes.put("the_void", "Die Leere");
        Biomes.put("warm_ocean", "Warmer Ozean");
        Biomes.put("warped_forest", "Wirrwald");
        Biomes.put("windswept_forest", "Sturmwald");
        Biomes.put("windswept_gravelly_hills", "Kiesige Sturmhügel");
        Biomes.put("windswept_hills", "Sturmhügel");
        Biomes.put("windswept_savanna", "Sturmsavanne");
        Biomes.put("wooded_badlands", "Bewaldete Tafelberge");
    }
}

