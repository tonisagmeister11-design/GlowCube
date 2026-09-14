/*
 * Decompiled with CFR 0.152.
 */
package com.glowcube.utils;

import com.glowcube.utils.Home;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class HomeManager {
    public static final String DEFAULT_HOME = "default";
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Home>> homes = new LinkedHashMap<UUID, Map<String, Home>>();

    public HomeManager(JavaPlugin javaPlugin) {
        this.plugin = javaPlugin;
        this.file = new File(javaPlugin.getDataFolder(), "homes.yml");
    }

    public int getMaxHomes() {
        return Math.max(0, this.plugin.getConfig().getInt("max-homes", 0));
    }

    public int getMaxNameLength() {
        return Math.max(1, this.plugin.getConfig().getInt("max-home-name-length", 32));
    }

    public Set<World.Environment> getBlockedEnvironments() {
        EnumSet<World.Environment> enumSet = EnumSet.noneOf(World.Environment.class);
        for (String string : this.plugin.getConfig().getStringList("blocked-environments")) {
            try {
                enumSet.add(World.Environment.valueOf((String)string.trim().toUpperCase(Locale.ROOT)));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                this.plugin.getLogger().warning("Unbekannter Welt-Typ in config.yml: " + string);
            }
        }
        return enumSet;
    }

    public boolean isBlockedWorld(World world) {
        return this.getBlockedEnvironments().contains(world.getEnvironment());
    }

    public List<Home> getHomes(UUID uUID) {
        Map<String, Home> map = this.homes.get(uUID);
        return map == null ? Collections.emptyList() : new ArrayList<Home>(map.values());
    }

    public Home getHome(UUID uUID, String string) {
        Map<String, Home> map = this.homes.get(uUID);
        return map == null ? null : map.get(string.toLowerCase(Locale.ROOT));
    }

    public boolean hasHome(UUID uUID, String string) {
        return this.getHome(uUID, string) != null;
    }

    public void setHome(UUID uUID2, Home home) {
        this.homes.computeIfAbsent(uUID2, uUID -> new LinkedHashMap()).put(home.name().toLowerCase(Locale.ROOT), home);
        this.save();
    }

    public boolean deleteHome(UUID uUID, String string) {
        boolean bl;
        Map<String, Home> map = this.homes.get(uUID);
        if (map == null) {
            return false;
        }
        boolean bl2 = bl = map.remove(string.toLowerCase(Locale.ROOT)) != null;
        if (map.isEmpty()) {
            this.homes.remove(uUID);
        }
        if (bl) {
            this.save();
        }
        return bl;
    }

    public int totalHomes() {
        return this.homes.values().stream().mapToInt(Map::size).sum();
    }

    public void load() {
        this.homes.clear();
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)this.file);
        ConfigurationSection configurationSection = yamlConfiguration.getConfigurationSection("homes");
        if (configurationSection == null) {
            return;
        }
        for (String string : configurationSection.getKeys(false)) {
            UUID uUID2;
            try {
                uUID2 = UUID.fromString(string);
            }
            catch (IllegalArgumentException illegalArgumentException) {
                this.plugin.getLogger().warning("Ungueltige Spieler-UUID in homes.yml: " + string);
                continue;
            }
            ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection(string);
            if (configurationSection2 == null) continue;
            for (String string2 : configurationSection2.getKeys(false)) {
                UUID uUID3;
                ConfigurationSection configurationSection3 = configurationSection2.getConfigurationSection(string2);
                if (configurationSection3 == null) continue;
                try {
                    uUID3 = UUID.fromString(configurationSection3.getString("world-id", ""));
                }
                catch (IllegalArgumentException illegalArgumentException) {
                    uUID3 = new UUID(0L, 0L);
                }
                Home home = new Home(configurationSection3.getString("name", string2), uUID3, configurationSection3.getString("world", "world"), configurationSection3.getDouble("x"), configurationSection3.getDouble("y"), configurationSection3.getDouble("z"), (float)configurationSection3.getDouble("yaw"), (float)configurationSection3.getDouble("pitch"));
                this.homes.computeIfAbsent(uUID2, uUID -> new LinkedHashMap()).put(string2.toLowerCase(Locale.ROOT), home);
            }
        }
    }

    public void save() {
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Home>> entry : this.homes.entrySet()) {
            for (Home home : entry.getValue().values()) {
                String string = "homes." + String.valueOf(entry.getKey()) + "." + home.name().toLowerCase(Locale.ROOT);
                yamlConfiguration.set(string + ".name", (Object)home.name());
                yamlConfiguration.set(string + ".world", (Object)home.worldName());
                yamlConfiguration.set(string + ".world-id", (Object)home.worldId().toString());
                yamlConfiguration.set(string + ".x", (Object)home.x());
                yamlConfiguration.set(string + ".y", (Object)home.y());
                yamlConfiguration.set(string + ".z", (Object)home.z());
                yamlConfiguration.set(string + ".yaw", (Object)home.yaw());
                yamlConfiguration.set(string + ".pitch", (Object)home.pitch());
            }
        }
        try {
            if (!this.plugin.getDataFolder().exists()) {
                this.plugin.getDataFolder().mkdirs();
            }
            yamlConfiguration.save(this.file);
        }
        catch (IOException iOException) {
            this.plugin.getLogger().severe("homes.yml konnte nicht gespeichert werden: " + iOException.getMessage());
        }
    }
}

