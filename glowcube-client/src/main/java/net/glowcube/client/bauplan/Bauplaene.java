package net.glowcube.client.bauplan;

import net.fabricmc.loader.api.FabricLoader;
import net.glowcube.client.GlowCubeClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Welche Schematics es gibt: die eingebauten aus der JAR
 * ({@code assets/glowcube/schematics}, aufgezaehlt in {@code index.txt}) und
 * alles, was in {@code .minecraft/config/glowcube/schematics/} liegt. Der
 * Name ist der Dateiname ohne Endung; liegt derselbe Name in beiden, gewinnt
 * der Ordner.
 */
public final class Bauplaene {
    private static final String INDEX = "/assets/glowcube/schematics/index.txt";
    private static final String[] ENDUNGEN = {".schem", ".litematic", ".nbt"};

    private Bauplaene() {
    }

    public static Path ordner() {
        return FabricLoader.getInstance().getConfigDir().resolve("glowcube").resolve("schematics");
    }

    /** Name -> Dateiname, eingebaute zuerst. */
    private static Map<String, String> eingebaut() {
        Map<String, String> liste = new LinkedHashMap<>();
        try (InputStream in = Bauplaene.class.getResourceAsStream(INDEX)) {
            if (in == null) {
                return liste;
            }
            BufferedReader leser = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String zeile;
            while ((zeile = leser.readLine()) != null) {
                zeile = zeile.trim();
                if (!zeile.isEmpty() && !zeile.startsWith("#")) {
                    liste.put(ohneEndung(zeile), zeile);
                }
            }
        } catch (IOException fehler) {
            GlowCubeClient.LOGGER.warn("GlowCube: Schematic-Liste nicht lesbar", fehler);
        }
        return liste;
    }

    private static Map<String, Path> imOrdner() {
        Map<String, Path> liste = new LinkedHashMap<>();
        Path o = ordner();
        try {
            Files.createDirectories(o);
            try (Stream<Path> dateien = Files.list(o)) {
                dateien.sorted().forEach(p -> {
                    String n = p.getFileName().toString();
                    if (hatEndung(n)) {
                        liste.put(ohneEndung(n), p);
                    }
                });
            }
        } catch (IOException fehler) {
            GlowCubeClient.LOGGER.warn("GlowCube: Schematic-Ordner nicht lesbar", fehler);
        }
        return liste;
    }

    /** Alle Namen fuer die Auswahl im Menue. Nie leer - notfalls ein Hinweis. */
    public static List<String> namen() {
        List<String> namen = new ArrayList<>(eingebaut().keySet());
        for (String n : imOrdner().keySet()) {
            if (!namen.contains(n)) {
                namen.add(n);
            }
        }
        if (namen.isEmpty()) {
            namen.add("(keine Schematics)");
        }
        return namen;
    }

    /** Laedt einen Plan nach Namen - oder null, wenn es ihn nicht gibt oder er kaputt ist. */
    public static Bauplan laden(String name) {
        Path datei = imOrdner().get(name);
        try {
            if (datei != null) {
                try (InputStream in = Files.newInputStream(datei)) {
                    return Bauplan.laden(name, datei.getFileName().toString(), in);
                }
            }
            String eingebauteDatei = eingebaut().get(name);
            if (eingebauteDatei != null) {
                try (InputStream in = Bauplaene.class.getResourceAsStream("/assets/glowcube/schematics/" + eingebauteDatei)) {
                    if (in != null) {
                        return Bauplan.laden(name, eingebauteDatei, in);
                    }
                }
            }
        } catch (IOException | RuntimeException fehler) {
            GlowCubeClient.LOGGER.warn("GlowCube: Schematic {} nicht lesbar", name, fehler);
        }
        return null;
    }

    private static boolean hatEndung(String n) {
        String k = n.toLowerCase(Locale.ROOT);
        for (String e : ENDUNGEN) {
            if (k.endsWith(e)) {
                return true;
            }
        }
        return false;
    }

    private static String ohneEndung(String n) {
        int punkt = n.lastIndexOf('.');
        return punkt > 0 ? n.substring(0, punkt) : n;
    }
}
