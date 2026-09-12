/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.BuildSite;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class BuildTracker {
    private final AdminFieldPlugin plugin;
    private final List<BuildSite> sites = new ArrayList<BuildSite>();
    private final Map<UUID, Deque<Long>> breakTimes = new HashMap<UUID, Deque<Long>>();
    private final Map<UUID, Long> lastAlarm = new HashMap<UUID, Long>();
    private File file;

    public BuildTracker(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
    }

    public void onPlace(Player player, Block block) {
        if (!this.plugin.getConfig().getBoolean("detection.enabled", true)) {
            return;
        }
        this.record(player, block, true);
    }

    public void onBreak(Player player, Block block) {
        if (this.plugin.getConfig().getBoolean("detection.enabled", true)) {
            this.record(player, block, false);
        }
        this.checkGrief(player, block);
    }

    private void record(Player player, Block block, boolean bl) {
        BuildSite buildSite = this.findOrCreate(block);
        buildSite.record(player.getUniqueId(), player.getName(), block, bl);
        int n = this.plugin.getConfig().getInt("detection.block-threshold", 30);
        if (!buildSite.announced() && buildSite.total() >= n) {
            buildSite.markAnnounced();
            this.announce(buildSite);
        }
    }

    private BuildSite findOrCreate(Block block) {
        int n = this.plugin.getConfig().getInt("detection.merge-radius", 24);
        double d = (double)n * (double)n;
        BuildSite buildSite = null;
        double d2 = Double.MAX_VALUE;
        String string = block.getWorld().getName();
        for (BuildSite buildSite2 : this.sites) {
            double d3 = buildSite2.distanceSquared(string, block.getX(), block.getY(), block.getZ());
            if (!(d3 <= d) || !(d3 < d2)) continue;
            d2 = d3;
            buildSite = buildSite2;
        }
        if (buildSite != null) {
            return buildSite;
        }
        BuildSite buildSite3 = new BuildSite(string, block.getX(), block.getY(), block.getZ());
        this.sites.add(buildSite3);
        this.prune();
        return buildSite3;
    }

    private void announce(BuildSite buildSite) {
        String string = buildSite.kind().color() + buildSite.kind().label();
        this.plugin.log().add(ActivityLog.Level.INFO, "Neue Baustelle erkannt: " + buildSite.kind().label() + " von " + buildSite.primaryBuilderName(), buildSite.worldName(), buildSite.centerX(), buildSite.centerY(), buildSite.centerZ(), buildSite.primaryBuilder());
        if (!this.plugin.getConfig().getBoolean("detection.announce", true)) {
            return;
        }
        String string2 = buildSite.centerX() + " / " + buildSite.centerY() + " / " + buildSite.centerZ();
        String string3 = "<white>" + buildSite.primaryBuilderName() + "</white> <gray>baut hier gerade: </gray>" + string + " <dark_gray>·</dark_gray> <gray>" + buildSite.total() + " Blöcke bei </gray><white>" + string2 + "</white> <dark_gray>(" + buildSite.worldName() + ")</dark_gray> <click:run_command:'/admin tp " + buildSite.worldName() + " " + buildSite.centerX() + " " + buildSite.centerY() + " " + buildSite.centerZ() + "'><hover:show_text:'<gray>Klicken zum Teleportieren'><aqua>[TP]</aqua></hover></click>";
        this.plugin.notifyAdmins(string3);
    }

    private void checkGrief(Player player, Block block) {
        if (!this.plugin.getConfig().getBoolean("grief-alarm.enabled", true)) {
            return;
        }
        if (player.hasPermission("adminfield.use")) {
            return;
        }
        int n = this.plugin.getConfig().getInt("grief-alarm.blocks", 90);
        long l = this.plugin.getConfig().getLong("grief-alarm.seconds", 12L) * 1000L;
        long l2 = System.currentTimeMillis();
        Deque deque = this.breakTimes.computeIfAbsent(player.getUniqueId(), uUID -> new ArrayDeque());
        deque.addLast(l2);
        while (!deque.isEmpty() && l2 - (Long)deque.peekFirst() > l) {
            deque.pollFirst();
        }
        if (deque.size() < n) {
            return;
        }
        Long l3 = this.lastAlarm.get(player.getUniqueId());
        if (l3 != null && l2 - l3 < 60000L) {
            return;
        }
        this.lastAlarm.put(player.getUniqueId(), l2);
        deque.clear();
        this.alarm(player, "<red>Schnellabbau</red><gray>: " + n + "+ Blöcke in " + l / 1000L + " Sekunden", block);
    }

    public void dangerous(Player player, Block block, String string) {
        if (!this.plugin.getConfig().getBoolean("grief-alarm.watch-dangerous-blocks", true)) {
            return;
        }
        if (player.hasPermission("adminfield.use")) {
            return;
        }
        this.alarm(player, "<red>" + string + "</red>", block);
    }

    private void alarm(Player player, String string, Block block) {
        this.plugin.log().add(ActivityLog.Level.ALERT, player.getName() + ": " + string.replaceAll("<[^>]*>", ""), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), player.getUniqueId());
        String string2 = block.getX() + " / " + block.getY() + " / " + block.getZ();
        this.plugin.notifyAdmins("<red><bold>ALARM</bold></red> <dark_gray>·</dark_gray> <white>" + player.getName() + "</white> <dark_gray>·</dark_gray> " + string + " <gray>bei </gray><white>" + string2 + "</white> <click:run_command:'/admin tp " + block.getWorld().getName() + " " + block.getX() + " " + block.getY() + " " + block.getZ() + "'><hover:show_text:'<gray>Klicken zum Teleportieren'><aqua>[TP]</aqua></hover></click>");
    }

    public List<BuildSite> confirmed() {
        int n = this.plugin.getConfig().getInt("detection.block-threshold", 30);
        ArrayList<BuildSite> arrayList = new ArrayList<BuildSite>();
        for (BuildSite buildSite : this.sites) {
            if (buildSite.total() < n) continue;
            arrayList.add(buildSite);
        }
        arrayList.sort(BuildSite.BY_RECENT);
        return arrayList;
    }

    public List<BuildSite> all() {
        ArrayList<BuildSite> arrayList = new ArrayList<BuildSite>(this.sites);
        arrayList.sort(BuildSite.BY_RECENT);
        return arrayList;
    }

    public BuildSite lastSiteOf(UUID uUID) {
        BuildSite buildSite = null;
        for (BuildSite buildSite2 : this.sites) {
            if (!buildSite2.contributions().containsKey(uUID) || buildSite != null && buildSite2.lastSeen() <= buildSite.lastSeen()) continue;
            buildSite = buildSite2;
        }
        return buildSite;
    }

    public BuildSite byId(UUID uUID) {
        for (BuildSite buildSite : this.sites) {
            if (!buildSite.id().equals(uUID)) continue;
            return buildSite;
        }
        return null;
    }

    public int count() {
        return this.sites.size();
    }

    public void forget(BuildSite buildSite) {
        this.sites.remove(buildSite);
    }

    public void clear() {
        this.sites.clear();
    }

    private void prune() {
        long l = this.plugin.getConfig().getLong("detection.forget-minutes", 0L);
        if (l > 0L) {
            long l2 = System.currentTimeMillis() - l * 60000L;
            this.sites.removeIf(buildSite -> buildSite.lastSeen() < l2);
        }
        int n = this.plugin.getConfig().getInt("detection.max-sites", 80);
        if (this.sites.size() <= n) {
            return;
        }
        ArrayList<BuildSite> arrayList = new ArrayList<BuildSite>(this.sites);
        arrayList.sort(Comparator.comparingInt(BuildSite::total).thenComparingLong(BuildSite::lastSeen));
        int n2 = this.sites.size() - n;
        for (int i = 0; i < n2 && i < arrayList.size(); ++i) {
            this.sites.remove(arrayList.get(i));
        }
    }

    public void load() {
        this.file = new File(this.plugin.getDataFolder(), "sites.yml");
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)this.file);
        ConfigurationSection configurationSection = yamlConfiguration.getConfigurationSection("sites");
        if (configurationSection == null) {
            return;
        }
        for (String string : configurationSection.getKeys(false)) {
            ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection(string);
            if (configurationSection2 == null) continue;
            try {
                String[] stringArray;
                List list = configurationSection2.getIntegerList("min");
                List list2 = configurationSection2.getIntegerList("max");
                List list3 = configurationSection2.getDoubleList("sum");
                if (list.size() < 3 || list2.size() < 3 || list3.size() < 3) continue;
                BuildSite buildSite = new BuildSite(configurationSection2.getString("world", "world"), (Integer)list.get(0), (Integer)list.get(1), (Integer)list.get(2), (Integer)list2.get(0), (Integer)list2.get(1), (Integer)list2.get(2), (Double)list3.get(0), (Double)list3.get(1), (Double)list3.get(2), configurationSection2.getInt("samples", 1), configurationSection2.getInt("placed"), configurationSection2.getInt("broken"), configurationSection2.getLong("first-seen"), configurationSection2.getLong("last-seen"), configurationSection2.getBoolean("announced"));
                for (String string2 : configurationSection2.getStringList("contributors")) {
                    stringArray = string2.split(";", 3);
                    if (stringArray.length < 2) continue;
                    buildSite.restoreContributor(UUID.fromString(stringArray[0]), Integer.parseInt(stringArray[1]), stringArray.length > 2 ? stringArray[2] : "Unbekannt");
                }
                for (String string2 : configurationSection2.getStringList("materials")) {
                    stringArray = string2.split(";", 2);
                    Material material = Material.matchMaterial((String)stringArray[0]);
                    if (material == null || stringArray.length <= 1) continue;
                    buildSite.restoreMaterial(material, Integer.parseInt(stringArray[1]));
                }
                this.sites.add(buildSite);
            }
            catch (IllegalArgumentException illegalArgumentException) {
                this.plugin.getLogger().warning("Baustelle " + string + " konnte nicht geladen werden: " + illegalArgumentException.getMessage());
            }
        }
        this.plugin.getLogger().info(this.sites.size() + " Baustellen geladen.");
    }

    public void save() {
        if (this.file == null) {
            this.file = new File(this.plugin.getDataFolder(), "sites.yml");
        }
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        int n = 0;
        for (BuildSite buildSite : this.sites) {
            yamlConfiguration.createSection("sites." + n, buildSite.serialize());
            ++n;
        }
        try {
            this.plugin.getDataFolder().mkdirs();
            yamlConfiguration.save(this.file);
        }
        catch (IOException iOException) {
            this.plugin.getLogger().warning("sites.yml konnte nicht gespeichert werden: " + iOException.getMessage());
        }
    }

    public void forgetPlayer(UUID uUID) {
        this.breakTimes.remove(uUID);
        this.lastAlarm.remove(uUID);
    }

    public int blocksBy(UUID uUID) {
        int n = 0;
        for (BuildSite buildSite : this.sites) {
            Integer n2 = buildSite.contributions().get(uUID);
            if (n2 == null) continue;
            n += n2.intValue();
        }
        return n;
    }

    public List<BuildSite> sitesOf(UUID uUID) {
        ArrayList<BuildSite> arrayList = new ArrayList<BuildSite>();
        for (BuildSite buildSite : this.sites) {
            if (!buildSite.contributions().containsKey(uUID)) continue;
            arrayList.add(buildSite);
        }
        arrayList.sort(BuildSite.BY_RECENT);
        return arrayList;
    }

    public void logSummary() {
        Bukkit.getLogger().info("[AdminField] " + this.sites.size() + " Baustellen im Speicher.");
    }
}

