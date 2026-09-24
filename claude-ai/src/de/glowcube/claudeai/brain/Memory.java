package de.glowcube.claudeai.brain;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Claudes Gedaechtnis: Orte, gelernte Woerter, Spitznamen und Fakten ueber Spieler. */
public final class Memory {

    private final File file;
    public final Map<String, Location> places = new LinkedHashMap<>();
    public final Map<String, String> aliases = new LinkedHashMap<>();
    public final Map<String, String> nicknames = new LinkedHashMap<>();
    public final Map<String, List<String>> facts = new LinkedHashMap<>();

    public Memory(File file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection p = y.getConfigurationSection("places");
        if (p != null) {
            for (String key : p.getKeys(false)) {
                ConfigurationSection s = p.getConfigurationSection(key);
                if (s == null) continue;
                World w = Bukkit.getWorld(s.getString("world"));
                if (w == null) continue;
                places.put(key, new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z")));
            }
        }
        ConfigurationSection a = y.getConfigurationSection("aliases");
        if (a != null) for (String k : a.getKeys(false)) aliases.put(k, a.getString(k));
        ConfigurationSection n = y.getConfigurationSection("nicknames");
        if (n != null) for (String k : n.getKeys(false)) nicknames.put(k, n.getString(k));
        ConfigurationSection f = y.getConfigurationSection("facts");
        if (f != null) {
            for (String k : f.getKeys(false)) {
                List<String> list = new ArrayList<>();
                for (Object o : y.getStringList("facts." + k)) list.add(String.valueOf(o));
                facts.put(k, list);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Location> e : places.entrySet()) {
            Location l = e.getValue();
            String base = "places." + e.getKey() + ".";
            y.set(base + "world", l.getWorld().getName());
            y.set(base + "x", l.getX());
            y.set(base + "y", l.getY());
            y.set(base + "z", l.getZ());
        }
        aliases.forEach((k, v) -> y.set("aliases." + k, v));
        nicknames.forEach((k, v) -> y.set("nicknames." + k, v));
        facts.forEach((k, v) -> y.set("facts." + k, v));
        try {
            file.getParentFile().mkdirs();
            y.save(file);
        } catch (IOException ignored) {
            // naechstes Mal
        }
    }

    public static String placeKey(String name) {
        return name.replaceAll("[^a-z0-9]", "");
    }
}
