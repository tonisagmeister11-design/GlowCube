package net.glowcube.client.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fassung fuer <b>26.3 und neuer</b> von {@link Erz}.
 *
 * <p>OreSim liest die Erzverteilung aus Minecrafts eigenem Weltgenerierungs-
 * Verzeichnis. Ab 26.3 ist die Erz-Konfiguration ({@code OreConfiguration})
 * aber in das Merkmal selbst verschmolzen und nicht mehr als eigenes,
 * auslesbares Objekt vorhanden - {@code aderngroesse} und
 * {@code discardChanceOnAirExposure} lassen sich von aussen nicht mehr
 * verlaesslich abgreifen. Ohne diese beiden Werte laesst sich die Adern-
 * generierung nicht Block fuer Block nachrechnen.
 *
 * <p>Deshalb bleibt OreSim auf 26.3 vorerst still: {@link #verzeichnis} gibt
 * eine leere Zuordnung zurueck, das Modul stuerzt nichts ab, findet aber auch
 * keine Erze. Auf 1.21.x und 26.2 laeuft die volle Fassung aus src/main.
 * Zum Erzsuchen steht auf 26.3 weiterhin X-Ray bereit.
 *
 * <p>Die oeffentliche Flaeche ist Wort fuer Wort dieselbe wie in der vollen
 * Fassung, damit {@code OreSim} unveraendert dagegen uebersetzt.
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

    // Bleibt ungenutzt, solange verzeichnis() leer zurueckgibt; nur damit die
    // Flaeche vollstaendig ist. Legt keine 26.3-fremde Klasse an.
    private Erz(Art art, int schritt, int stelle) {
        this.art = art;
        this.schritt = schritt;
        this.stelle = stelle;
    }

    /**
     * Auf 26.3 (noch) leer - siehe Klassenkommentar. Der Rueckgabetyp ist
     * derselbe wie in der vollen Fassung, damit OreSim unveraendert bleibt.
     */
    public static Map<ResourceKey<Biome>, List<Erz>> verzeichnis(String dimension) {
        net.glowcube.client.GlowCubeClient.LOGGER.info(
                "OreSim: auf dieser Fassung (26.3+) noch nicht verfuegbar - "
                + "die Weltgenerierung wurde umgebaut. X-Ray funktioniert weiter.");
        return new LinkedHashMap<>();
    }
}
