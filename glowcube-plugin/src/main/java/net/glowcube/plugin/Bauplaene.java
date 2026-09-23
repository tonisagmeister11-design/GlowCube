package net.glowcube.plugin;

import net.glowcube.plugin.bauplan.Bauplan;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Die Schematics des Plugins: dieselben eingebauten wie im Client, dazu alles
 * in {@code plugins/GlowCubeAgent/schematics/}. Der Client schickt nur den
 * Namen (Dateiname ohne Endung) - ein Schematic muss also auf dem Server
 * liegen, damit der Builder es dort bauen kann.
 */
final class Bauplaene {
    private Bauplaene() {
    }

    static File ordner(GlowCubeAgentPlugin plugin) {
        File o = new File(plugin.getDataFolder(), "schematics");
        o.mkdirs();
        return o;
    }

    private static Map<String, String> eingebaut() {
        Map<String, String> liste = new LinkedHashMap<>();
        try (InputStream in = Bauplaene.class.getResourceAsStream("/schematics/index.txt")) {
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
        } catch (IOException ignoriert) {
            // Ohne Liste eben nur der Ordner.
        }
        return liste;
    }

    static Bauplan laden(GlowCubeAgentPlugin plugin, String name) {
        try {
            File[] dateien = ordner(plugin).listFiles();
            if (dateien != null) {
                for (File f : dateien) {
                    String n = f.getName();
                    String k = n.toLowerCase(Locale.ROOT);
                    if ((k.endsWith(".schem") || k.endsWith(".litematic") || k.endsWith(".nbt"))
                            && ohneEndung(n).equals(name)) {
                        try (InputStream in = Files.newInputStream(f.toPath())) {
                            return Bauplan.laden(name, n, in);
                        }
                    }
                }
            }
            String datei = eingebaut().get(name);
            if (datei != null) {
                try (InputStream in = Bauplaene.class.getResourceAsStream("/schematics/" + datei)) {
                    if (in != null) {
                        return Bauplan.laden(name, datei, in);
                    }
                }
            }
        } catch (IOException | RuntimeException fehler) {
            plugin.getLogger().warning("Schematic " + name + " nicht lesbar: " + fehler);
        }
        return null;
    }

    private static String ohneEndung(String n) {
        int punkt = n.lastIndexOf('.');
        return punkt > 0 ? n.substring(0, punkt) : n;
    }
}
