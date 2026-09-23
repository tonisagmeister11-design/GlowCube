package net.glowcube.client.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.glowcube.client.GlowCubeClient;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Das Pixel-Ressourcenpaket des Performance-Modus: jede Blocktextur wird zu
 * genau einem Pixel in ihrer Durchschnittsfarbe. Spieler, Mobs und Items
 * behalten ihre Texturen - erfasst wird nur {@code textures/block}.
 *
 * <p>Das Paket wird beim Einschalten frisch aus den gerade geladenen Texturen
 * erzeugt (ein eigenes Texturpaket faerbt also mit) und liegt als Ordner
 * {@code resourcepacks/GlowCube-Pixel}. Aktiviert wird es nur in der
 * laufenden Sitzung: {@code options.txt} bekommt davon nichts ab, und beim
 * Ausschalten steht die Paketliste wieder genau so da wie vorher.
 *
 * <p>Fast durchsichtige Texturen (Blumen, Fackeln, Schienen, Glas) bleiben wie
 * sie sind - als einfarbige Flaeche saehen sie aus wie Bretter.
 */
public final class PixelPaket {
    private static final String NAME = "GlowCube-Pixel";
    private static final String ID = "file/" + NAME;

    /** Die Paketliste von vor dem Einschalten - null heisst: Paket nicht aktiv. */
    private static List<String> vorher;

    private PixelPaket() {
    }

    public static boolean aktiv() {
        return vorher != null;
    }

    /** Paket erzeugen, zur Paketliste hinzufuegen und Ressourcen neu laden. */
    public static void an() {
        if (vorher != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PackRepository paketliste = mc.getResourcePackRepository();
        List<String> ausgangslage = new ArrayList<>(paketliste.getSelectedIds());
        try {
            // Steckt es noch von frueher drin (Absturz), die Texturen nicht aus
            // sich selbst erzeugen - die sind ja schon ein Pixel gross.
            if (!ausgangslage.contains(ID)) {
                erzeugen(mc);
            }
            paketliste.reload();
            if (!paketliste.getAvailableIds().contains(ID)) {
                GlowCubeClient.LOGGER.warn("GlowCube: Pixel-Paket wurde nicht gefunden");
                return;
            }
            List<String> neu = new ArrayList<>(ausgangslage);
            neu.remove(ID);
            // Als letztes = ganz oben, ueber allen anderen Paketen.
            neu.add(ID);
            vorher = ausgangslage;
            paketliste.setSelected(neu);
            mc.reloadResourcePacks();
        } catch (IOException | RuntimeException fehler) {
            GlowCubeClient.LOGGER.warn("GlowCube: Pixel-Paket konnte nicht erzeugt werden", fehler);
        }
    }

    /** Die alte Paketliste zurueck und neu laden. */
    public static void aus() {
        if (vorher == null) {
            return;
        }
        List<String> zurueck = vorher;
        vorher = null;
        Minecraft mc = Minecraft.getInstance();
        // Kann vom Trennen der Verbindung aus kommen - neu geladen wird immer
        // auf dem Spiel-Thread.
        mc.execute(() -> {
            try {
                PackRepository paketliste = mc.getResourcePackRepository();
                paketliste.setSelected(zurueck);
                mc.reloadResourcePacks();
            } catch (RuntimeException fehler) {
                GlowCubeClient.LOGGER.warn("GlowCube: Paketliste konnte nicht zurueckgesetzt werden", fehler);
            }
        });
    }

    // --------------------------------------------------------------- intern

    private static void erzeugen(Minecraft mc) throws IOException {
        Path ordner = mc.getResourcePackDirectory().resolve(NAME);
        loeschen(ordner);
        Files.createDirectories(ordner);

        PackFormat format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        String mcmeta = "{\n  \"pack\": {\n    \"description\": \"GlowCube Performance: jeder Block ein Pixel\",\n"
                + "    \"min_format\": " + format.major() + ",\n    \"max_format\": " + format.major() + "\n  }\n}\n";
        Files.writeString(ordner.resolve("pack.mcmeta"), mcmeta, StandardCharsets.UTF_8);

        Map<Identifier, Resource> texturen = mc.getResourceManager()
                .listResources("textures/block", id -> id.getPath().endsWith(".png"));
        int geschrieben = 0;
        for (Map.Entry<Identifier, Resource> eintrag : texturen.entrySet()) {
            Identifier id = eintrag.getKey();
            Integer farbe;
            try (InputStream in = eintrag.getValue().open(); NativeImage bild = NativeImage.read(in)) {
                farbe = durchschnitt(bild);
            } catch (IOException | RuntimeException kaputt) {
                continue;
            }
            if (farbe == null) {
                continue;
            }
            Path ziel = ordner.resolve("assets").resolve(id.getNamespace()).resolve(id.getPath());
            Files.createDirectories(ziel.getParent());
            try (NativeImage pixel = new NativeImage(1, 1, false)) {
                pixel.setPixel(0, 0, farbe);
                pixel.writeToFile(ziel);
            }
            // Animierte Texturen: eine leere Metadatei ueberdeckt die des
            // Originals, sonst suchte Minecraft Einzelbilder im einen Pixel.
            Identifier meta = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + ".mcmeta");
            if (mc.getResourceManager().getResource(meta).isPresent()) {
                Files.writeString(ziel.resolveSibling(ziel.getFileName() + ".mcmeta"), "{}\n", StandardCharsets.UTF_8);
            }
            geschrieben++;
        }
        GlowCubeClient.LOGGER.info("GlowCube: Pixel-Paket mit {} Blocktexturen erzeugt", geschrieben);
    }

    /**
     * Durchschnittsfarbe (ARGB) des ersten Einzelbilds, gewichtet nach
     * Deckkraft. Null, wenn die Textur ueberwiegend durchsichtig ist.
     */
    private static Integer durchschnitt(NativeImage bild) {
        int breite = bild.getWidth();
        // Animationen stehen als Streifen untereinander - nur das erste Bild.
        int hoehe = Math.min(bild.getHeight(), breite);
        long r = 0;
        long g = 0;
        long b = 0;
        long deckSumme = 0;
        int durchsichtig = 0;
        int anzahl = breite * hoehe;
        if (anzahl == 0) {
            return null;
        }
        for (int y = 0; y < hoehe; y++) {
            for (int x = 0; x < breite; x++) {
                int argb = bild.getPixel(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < 16) {
                    durchsichtig++;
                    continue;
                }
                r += (long) ((argb >> 16) & 0xFF) * a;
                g += (long) ((argb >> 8) & 0xFF) * a;
                b += (long) (argb & 0xFF) * a;
                deckSumme += a;
            }
        }
        if (deckSumme == 0 || durchsichtig * 10 > anzahl * 4) {
            return null;
        }
        int alpha = (int) Math.min(255, deckSumme / anzahl);
        // Fast deckend zaehlt als deckend - sonst wuerden Bloecke mit ein paar
        // Loechern (Laub) halb durchsichtig.
        if (alpha > 200 || durchsichtig > 0) {
            alpha = 255;
        }
        return (alpha << 24)
                | (int) (r / deckSumme) << 16
                | (int) (g / deckSumme) << 8
                | (int) (b / deckSumme);
    }

    private static void loeschen(Path ordner) throws IOException {
        if (!Files.exists(ordner)) {
            return;
        }
        try (Stream<Path> alle = Files.walk(ordner)) {
            for (Path p : alle.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
