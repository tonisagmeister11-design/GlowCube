package net.glowcube.client.util;

import net.glowcube.client.mixin.CountPlacementAccessor;
import net.glowcube.client.mixin.HeightRangePlacementAccessor;
import net.glowcube.client.mixin.RarityFilterAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uebertragen aus Meteor Rejects (GPL-3.0), Klasse {@code Ore}.
 *
 * <p>Eine Erzart, so wie die Weltgenerierung sie kennt: in welchem
 * Generierungsschritt sie liegt, an welcher Stelle innerhalb dieses
 * Schrittes, wie oft sie je Chunk versucht wird, in welcher Hoehe, wie
 * selten, wie gross die Ader wird und ob sie zerstreut ist.
 *
 * <p>Das Entscheidende: diese Werte werden <b>nicht abgeschrieben</b>,
 * sondern aus Minecrafts eigenem Verzeichnis gelesen. Damit stimmen sie
 * zwangslaeufig mit der gespielten Fassung ueberein - eine abgetippte
 * Tabelle waere beim naechsten Snapshot falsch, ohne dass es jemand merkt.
 *
 * <p>{@code schritt} und {@code stelle} sind der Grund, warum das ueberhaupt
 * geht: Minecraft zieht fuer jedes Merkmal eine eigene Zufallsfolge, deren
 * Startwert sich aus Weltseed, Chunk und genau diesen beiden Zahlen ergibt.
 * Wer sie kennt, kann dieselbe Folge nachziehen.
 */
public final class Erz {
    /** Die Erzarten, die einzeln ein- und ausschaltbar sind. */
    public enum Art {
        KOHLE("Kohle", 0xFF2F2C36),
        EISEN("Eisen", 0xFFECAD77),
        GOLD("Gold", 0xFFF7E51E),
        REDSTONE("Redstone", 0xFFF50717),
        DIAMANT("Diamant", 0xFF21F4FF),
        LAPIS("Lapis", 0xFF081ABD),
        KUPFER("Kupfer", 0xFFEF9700),
        SMARAGD("Smaragd", 0xFF1BD12D),
        QUARZ("Quarz", 0xFFCDCDCD),
        NETHERIT("Netherit", 0xFFD11BF5);

        private final String bezeichnung;
        private final int farbe;

        Art(String bezeichnung, int farbe) {
            this.bezeichnung = bezeichnung;
            this.farbe = farbe;
        }

        public String bezeichnung() {
            return bezeichnung;
        }

        public int farbe() {
            return farbe;
        }
    }

    public final Art art;
    public final int schritt;
    public final int stelle;
    public IntProvider anzahl = ConstantInt.of(1);
    public HeightProvider hoehe;
    public WorldGenerationContext hoehenRahmen;
    public float seltenheit = 1.0f;
    public float verwerfenAnLuft;
    public int groesse;
    public boolean zerstreut;

    private Erz(PlacedFeature merkmal, int schritt, int stelle, Art art) {
        this.art = art;
        this.schritt = schritt;
        this.stelle = stelle;

        int unten = Minecraft.getInstance().level.getMinY();
        int hoch = Minecraft.getInstance().level.dimensionType().logicalHeight();
        this.hoehenRahmen = new WorldGenerationContext(null, LevelHeightAccessor.create(unten, hoch));

        for (PlacementModifier regel : merkmal.placement()) {
            if (regel instanceof CountPlacement) {
                this.anzahl = ((CountPlacementAccessor) regel).glowcube$anzahl();
            } else if (regel instanceof HeightRangePlacement) {
                this.hoehe = ((HeightRangePlacementAccessor) regel).glowcube$hoehe();
            } else if (regel instanceof RarityFilter) {
                this.seltenheit = ((RarityFilterAccessor) regel).glowcube$haeufigkeit();
            }
        }

        FeatureConfiguration einstellung = merkmal.feature().value().config();
        if (!(einstellung instanceof OreConfiguration erzEinstellung)) {
            throw new IllegalStateException("Kein Erzmerkmal: " + merkmal);
        }
        this.verwerfenAnLuft = erzEinstellung.discardChanceOnAirExposure;
        this.groesse = erzEinstellung.size;
        this.zerstreut = merkmal.feature().value().feature() instanceof ScatteredOreFeature;
    }

    // --------------------------------------------------------- Verzeichnis

    /**
     * Baut die Zuordnung Biom -> Erzarten fuer eine Dimension.
     *
     * <p>Der Weg ueber {@code VanillaRegistries} und {@code FeatureSorter}
     * ist der des Originals und gleichzeitig der einzige richtige: die
     * Nummer eines Merkmals innerhalb seines Schrittes haengt davon ab,
     * welche Biome es in der Welt ueberhaupt gibt und in welcher Reihenfolge
     * das Spiel sie sortiert. Wer sie fest eintraegt, liegt in der Haelfte
     * aller Welten daneben.
     */
    public static Map<ResourceKey<Biome>, List<Erz>> verzeichnis(String dimension) {
        HolderLookup.Provider verzeichnis = VanillaRegistries.createLookup();
        HolderLookup.RegistryLookup<PlacedFeature> merkmale =
                verzeichnis.lookupOrThrow(Registries.PLACED_FEATURE);
        var dimensionen = verzeichnis.lookupOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions().dimensions();

        var stamm = switch (dimension) {
            case "nether" -> dimensionen.get(LevelStem.NETHER);
            case "end" -> dimensionen.get(LevelStem.END);
            default -> dimensionen.get(LevelStem.OVERWORLD);
        };

        var biome = stamm.generator().getBiomeSource().possibleBiomes().stream().toList();
        List<FeatureSorter.StepFeatureData> nummerierung = FeatureSorter.buildFeaturesPerStep(
                biome, eintrag -> eintrag.value().getGenerationSettings().features(), true);

        Map<PlacedFeature, Erz> merkmalZuErz = new HashMap<>();
        // Oberwelt (Schritt 6 = UNDERGROUND_ORES)
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_COAL_LOWER, 6, Art.KOHLE);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_COAL_UPPER, 6, Art.KOHLE);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_IRON_MIDDLE, 6, Art.EISEN);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_IRON_SMALL, 6, Art.EISEN);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_IRON_UPPER, 6, Art.EISEN);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_GOLD, 6, Art.GOLD);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_GOLD_LOWER, 6, Art.GOLD);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_GOLD_EXTRA, 6, Art.GOLD);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_REDSTONE, 6, Art.REDSTONE);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_REDSTONE_LOWER, 6, Art.REDSTONE);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_DIAMOND, 6, Art.DIAMANT);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_DIAMOND_BURIED, 6, Art.DIAMANT);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_DIAMOND_LARGE, 6, Art.DIAMANT);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_DIAMOND_MEDIUM, 6, Art.DIAMANT);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_LAPIS, 6, Art.LAPIS);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_LAPIS_BURIED, 6, Art.LAPIS);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_COPPER, 6, Art.KUPFER);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_COPPER_LARGE, 6, Art.KUPFER);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_EMERALD, 6, Art.SMARAGD);
        // Nether (Schritt 7 = UNDERGROUND_DECORATION)
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_GOLD_NETHER, 7, Art.GOLD);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_GOLD_DELTAS, 7, Art.GOLD);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_QUARTZ_NETHER, 7, Art.QUARZ);
        eintragen(merkmalZuErz, nummerierung, merkmale, OrePlacements.ORE_QUARTZ_DELTAS, 7, Art.QUARZ);
        eintragen(merkmalZuErz, nummerierung, merkmale,
                OrePlacements.ORE_ANCIENT_DEBRIS_SMALL, 7, Art.NETHERIT);
        eintragen(merkmalZuErz, nummerierung, merkmale,
                OrePlacements.ORE_ANCIENT_DEBRIS_LARGE, 7, Art.NETHERIT);

        Map<ResourceKey<Biome>, List<Erz>> proBiom = new LinkedHashMap<>();
        for (Holder<Biome> eintrag : biome) {
            List<Erz> liste = new ArrayList<>();
            eintrag.value().getGenerationSettings().features().stream()
                    .flatMap(HolderSet::stream)
                    .map(Holder::value)
                    .filter(merkmalZuErz::containsKey)
                    .forEach(merkmal -> liste.add(merkmalZuErz.get(merkmal)));
            proBiom.put(eintrag.unwrapKey().orElseThrow(), liste);
        }
        return proBiom;
    }

    private static void eintragen(Map<PlacedFeature, Erz> ziel,
                                  List<FeatureSorter.StepFeatureData> nummerierung,
                                  HolderLookup.RegistryLookup<PlacedFeature> merkmale,
                                  ResourceKey<PlacedFeature> schluessel,
                                  int schritt, Art art) {
        try {
            PlacedFeature merkmal = merkmale.getOrThrow(schluessel).value();
            int stelle = nummerierung.get(schritt).indexMapping().applyAsInt(merkmal);
            ziel.put(merkmal, new Erz(merkmal, schritt, stelle, art));
        } catch (RuntimeException fehler) {
            // Ein Erz, das es in dieser Fassung nicht gibt, faellt weg -
            // besser als ein Modul, das gar nicht mehr startet.
            net.glowcube.client.GlowCubeClient.LOGGER.warn(
                    "OreSim: {} nicht im Verzeichnis ({})", schluessel, fehler.toString());
        }
    }
}
