package de.glowcube.claudeai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;

/** Alle Einstellungen aus config.yml an einem Ort. */
public final class Settings {

    public String name = "Claude";
    public String skin = "steve";
    public String chatFormat = "<%name%> %msg%";
    public List<String> owners = new ArrayList<>();
    public boolean requireName = false;
    public int listenSeconds = 90;
    public boolean followAfterSpawn = true;
    public boolean invulnerable = true;
    public boolean allowPvp = false;
    public boolean defendAgainstPlayers = true;
    public boolean buildNeedsMaterials = false;
    public int blocksPerTick = 2;
    public boolean respectProtection = true;
    public boolean idleTalk = true;
    public double walkSpeed = 0.23;
    public int searchRadius = 40;
    public int smeltTicksPerItem = 20;
    public boolean realisticTools = true;

    public static Settings load(FileConfiguration c) {
        Settings s = new Settings();
        s.name = c.getString("name", s.name);
        s.skin = c.getString("skin", s.skin).trim();
        s.chatFormat = c.getString("chat-format", s.chatFormat);
        List<String> owners = c.getStringList("owners");
        if (owners != null) for (String o : owners) s.owners.add(o.toLowerCase(Locale.ROOT));
        s.requireName = c.getBoolean("require-name", s.requireName);
        s.listenSeconds = c.getInt("listen-seconds", s.listenSeconds);
        s.followAfterSpawn = c.getBoolean("follow-after-spawn", s.followAfterSpawn);
        s.invulnerable = c.getBoolean("invulnerable", s.invulnerable);
        s.allowPvp = c.getBoolean("allow-pvp", s.allowPvp);
        s.defendAgainstPlayers = c.getBoolean("defend-against-players", s.defendAgainstPlayers);
        s.buildNeedsMaterials = c.getBoolean("build.needs-materials", s.buildNeedsMaterials);
        s.blocksPerTick = Math.max(1, Math.min(20, c.getInt("build.blocks-per-tick", s.blocksPerTick)));
        s.respectProtection = c.getBoolean("respect-protection", s.respectProtection);
        s.idleTalk = c.getBoolean("idle-talk", s.idleTalk);
        s.walkSpeed = Math.max(0.05, Math.min(0.6, c.getDouble("walk-speed", s.walkSpeed)));
        s.searchRadius = Math.max(8, Math.min(64, c.getInt("search-radius", s.searchRadius)));
        s.smeltTicksPerItem = Math.max(1, c.getInt("smelt-ticks-per-item", s.smeltTicksPerItem));
        s.realisticTools = c.getBoolean("realistic-tools", s.realisticTools);
        return s;
    }

    public boolean isOwner(String player) {
        return owners.isEmpty() || owners.contains(player.toLowerCase(Locale.ROOT));
    }
}
