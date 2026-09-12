/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class Jail {
    private final AdminFieldPlugin plugin;
    private Location location;
    private final Map<UUID, Inmate> inmates = new HashMap<UUID, Inmate>();
    private final Map<UUID, UUID> pendingSetup = new HashMap<UUID, UUID>();
    private final Map<UUID, Long> lastWarning = new HashMap<UUID, Long>();
    private File file;

    public Jail(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
    }

    public boolean isSet() {
        return this.location != null && this.location.getWorld() != null;
    }

    public Location location() {
        return this.location == null ? null : this.location.clone();
    }

    public void setLocation(Location location) {
        this.location = location.clone();
        this.save();
    }

    public void beginSetup(Player player, UUID uUID) {
        this.pendingSetup.put(player.getUniqueId(), uUID);
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> player.closeInventory());
        this.plugin.send((CommandSender)player, "<yellow>Klicke jetzt auf den Boden, wo das Gefängnis sein soll.");
        player.sendMessage(this.plugin.prefix().append(Ui.mm("<dark_gray>Der Spieler landet genau auf diesem Block. <click:run_command:'/admin gefaengnis abbrechen'><hover:show_text:'<gray>Auswahl abbrechen'><red>[Abbrechen]</red></hover></click>")));
        player.showTitle(Title.title((Component)Ui.mm("<yellow><bold>Gefängnis festlegen</bold>"), (Component)Ui.mm("<gray>Klicke auf den Boden"), (Title.Times)Title.Times.times((Duration)Duration.ofMillis(200L), (Duration)Duration.ofSeconds(4L), (Duration)Duration.ofMillis(600L))));
    }

    public boolean isSettingUp(UUID uUID) {
        return this.pendingSetup.containsKey(uUID);
    }

    public void cancelSetup(UUID uUID) {
        this.pendingSetup.remove(uUID);
    }

    public void completeSetup(Player player, Block block) {
        UUID uUID = this.pendingSetup.remove(player.getUniqueId());
        Location location = block.getLocation().add(0.5, 1.0, 0.5);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(0.0f);
        this.setLocation(location);
        this.plugin.send((CommandSender)player, "<green>Gefängnis festgelegt bei <white>" + Ui.pos(location) + "<green> in <white>" + location.getWorld().getName() + "<green>.");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.4f);
        this.plugin.log().add(ActivityLog.Level.INFO, player.getName() + " legte das Gefängnis fest", location, player.getUniqueId());
        if (uUID == null) {
            return;
        }
        Player player2 = Bukkit.getPlayer((UUID)uUID);
        if (player2 == null) {
            this.plugin.send((CommandSender)player, "<red>Der Spieler ist inzwischen offline.");
            return;
        }
        this.jail(player, player2);
    }

    public boolean isJailed(UUID uUID) {
        return this.inmates.containsKey(uUID);
    }

    public Inmate inmate(UUID uUID) {
        return this.inmates.get(uUID);
    }

    public Collection<Inmate> inmates() {
        return new ArrayList<Inmate>(this.inmates.values());
    }

    public int count() {
        return this.inmates.size();
    }

    public boolean jail(Player player, Player player2) {
        if (!this.isSet()) {
            this.plugin.send((CommandSender)player, "<red>Es ist noch kein Gefängnis festgelegt.");
            return false;
        }
        if (this.isJailed(player2.getUniqueId())) {
            this.plugin.send((CommandSender)player, "<red>" + player2.getName() + " sitzt bereits.");
            return false;
        }
        Location location = player2.getLocation().clone();
        this.inmates.put(player2.getUniqueId(), new Inmate(player2.getUniqueId(), player2.getName(), System.currentTimeMillis(), player.getName(), player2.getGameMode(), location));
        player2.teleportAsync(this.location.clone());
        player2.setGameMode(this.jailGameMode());
        player2.setFireTicks(0);
        player2.showTitle(Title.title((Component)Ui.mm("<red><bold>Gefängnis</bold>"), (Component)Ui.mm("<gray>Du bist jetzt im Gefängnis"), (Title.Times)Title.Times.times((Duration)Duration.ofMillis(300L), (Duration)Duration.ofSeconds(4L), (Duration)Duration.ofSeconds(1L))));
        this.plugin.send((CommandSender)player2, "<red>Du bist jetzt im Gefängnis.<gray> Ein Admin holt dich wieder raus.");
        player2.playSound(player2.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.8f);
        this.plugin.send((CommandSender)player, "<gray><white>" + player2.getName() + "<gray> sitzt jetzt im Gefängnis.");
        this.plugin.log().add(ActivityLog.Level.WARN, player.getName() + " sperrte " + player2.getName() + " ein", location, player2.getUniqueId());
        this.save();
        return true;
    }

    public boolean release(String string, UUID uUID) {
        Inmate inmate = this.inmates.remove(uUID);
        if (inmate == null) {
            return false;
        }
        this.lastWarning.remove(uUID);
        this.save();
        Player player = Bukkit.getPlayer((UUID)uUID);
        if (player == null) {
            this.plugin.log().add(ActivityLog.Level.INFO, string + " entließ " + inmate.name() + " (offline)", null, uUID);
            return true;
        }
        Location location = inmate.returnLocation();
        if (location != null && location.getWorld() != null) {
            player.teleportAsync(location.clone());
        }
        if (this.plugin.getConfig().getBoolean("jail.restore-gamemode", true) && inmate.previousMode() != null) {
            player.setGameMode(inmate.previousMode());
        }
        player.showTitle(Title.title((Component)Ui.mm("<green><bold>Freigelassen</bold>"), (Component)Ui.mm("<gray>Du bist wieder frei"), (Title.Times)Title.Times.times((Duration)Duration.ofMillis(300L), (Duration)Duration.ofSeconds(3L), (Duration)Duration.ofMillis(800L))));
        this.plugin.send((CommandSender)player, "<green>Du wurdest aus dem Gefängnis entlassen.");
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.2f);
        this.plugin.log().add(ActivityLog.Level.INFO, string + " entließ " + inmate.name(), location, uUID);
        return true;
    }

    private GameMode jailGameMode() {
        String string = this.plugin.getConfig().getString("jail.gamemode", "SURVIVAL");
        try {
            return GameMode.valueOf((String)string.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException illegalArgumentException) {
            return GameMode.SURVIVAL;
        }
    }

    public void tick() {
        if (this.inmates.isEmpty()) {
            return;
        }
        int n = this.plugin.getConfig().getInt("jail.feed-below", 6);
        int n2 = Math.max(1, this.plugin.getConfig().getInt("jail.bread-amount", 2));
        double d = Math.max(2.0, this.plugin.getConfig().getDouble("jail.radius", 12.0));
        double d2 = d * d;
        for (Inmate inmate : this.inmates()) {
            Player player = Bukkit.getPlayer((UUID)inmate.uuid());
            if (player == null) continue;
            this.feed(player, n, n2);
            this.keepInside(player, d2);
        }
    }

    private void feed(Player player, int n, int n2) {
        if (player.getFoodLevel() > n) {
            return;
        }
        int n3 = 0;
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (itemStack == null || itemStack.getType() != Material.BREAD) continue;
            n3 += itemStack.getAmount();
        }
        int n4 = n2 - n3;
        if (n4 <= 0) {
            return;
        }
        for (ItemStack itemStack : player.getInventory().addItem(new ItemStack[]{new ItemStack(Material.BREAD, n4)}).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
        }
        this.plugin.send((CommandSender)player, "<gray>Essensausgabe: <white>" + n4 + "x Brot<gray>.");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.0f);
    }

    private void keepInside(Player player, double d) {
        boolean bl;
        if (this.location == null || this.location.getWorld() == null) {
            return;
        }
        boolean bl2 = bl = !player.getWorld().equals((Object)this.location.getWorld()) || player.getLocation().distanceSquared(this.location) > d;
        if (!bl) {
            return;
        }
        player.teleportAsync(this.location.clone());
        long l = System.currentTimeMillis();
        Long l2 = this.lastWarning.get(player.getUniqueId());
        if (l2 == null || l - l2 > 5000L) {
            this.lastWarning.put(player.getUniqueId(), l);
            this.plugin.send((CommandSender)player, "<red>Du kommst hier nicht raus.");
        }
    }

    public void onJoin(Player player) {
        if (!this.isJailed(player.getUniqueId()) || !this.isSet()) {
            return;
        }
        player.teleportAsync(this.location.clone());
        player.setGameMode(this.jailGameMode());
        this.plugin.send((CommandSender)player, "<red>Du sitzt weiterhin im Gefängnis.");
    }

    public void load() {
        ConfigurationSection configurationSection;
        this.file = new File(this.plugin.getDataFolder(), "jail.yml");
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)this.file);
        ConfigurationSection configurationSection2 = yamlConfiguration.getConfigurationSection("location");
        if (configurationSection2 != null) {
            configurationSection = Bukkit.getWorld((String)configurationSection2.getString("world", ""));
            if (configurationSection != null) {
                this.location = new Location((World)configurationSection, configurationSection2.getDouble("x"), configurationSection2.getDouble("y"), configurationSection2.getDouble("z"), (float)configurationSection2.getDouble("yaw"), (float)configurationSection2.getDouble("pitch"));
            } else {
                this.plugin.getLogger().warning("Gefängniswelt '" + configurationSection2.getString("world") + "' ist nicht geladen.");
            }
        }
        if ((configurationSection = yamlConfiguration.getConfigurationSection("inmates")) == null) {
            return;
        }
        for (String string : configurationSection.getKeys(false)) {
            ConfigurationSection configurationSection3 = configurationSection.getConfigurationSection(string);
            if (configurationSection3 == null) continue;
            try {
                GameMode gameMode;
                UUID uUID = UUID.fromString(string);
                Location location = null;
                ConfigurationSection configurationSection4 = configurationSection3.getConfigurationSection("return");
                if (configurationSection4 != null && (gameMode = Bukkit.getWorld((String)configurationSection4.getString("world", ""))) != null) {
                    location = new Location((World)gameMode, configurationSection4.getDouble("x"), configurationSection4.getDouble("y"), configurationSection4.getDouble("z"), (float)configurationSection4.getDouble("yaw"), (float)configurationSection4.getDouble("pitch"));
                }
                try {
                    gameMode = GameMode.valueOf((String)configurationSection3.getString("gamemode", "SURVIVAL"));
                }
                catch (IllegalArgumentException illegalArgumentException) {
                    gameMode = GameMode.SURVIVAL;
                }
                this.inmates.put(uUID, new Inmate(uUID, configurationSection3.getString("name", "Unbekannt"), configurationSection3.getLong("jailed-at"), configurationSection3.getString("jailed-by", "Unbekannt"), gameMode, location));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                this.plugin.getLogger().warning("Gefangener " + string + " konnte nicht geladen werden.");
            }
        }
        if (!this.inmates.isEmpty()) {
            this.plugin.getLogger().info(this.inmates.size() + " Gefangene geladen.");
        }
    }

    public void save() {
        if (this.file == null) {
            this.file = new File(this.plugin.getDataFolder(), "jail.yml");
        }
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        if (this.isSet()) {
            yamlConfiguration.set("location.world", (Object)this.location.getWorld().getName());
            yamlConfiguration.set("location.x", (Object)this.location.getX());
            yamlConfiguration.set("location.y", (Object)this.location.getY());
            yamlConfiguration.set("location.z", (Object)this.location.getZ());
            yamlConfiguration.set("location.yaw", (Object)Float.valueOf(this.location.getYaw()));
            yamlConfiguration.set("location.pitch", (Object)Float.valueOf(this.location.getPitch()));
        }
        for (Inmate inmate : this.inmates.values()) {
            String string = "inmates." + String.valueOf(inmate.uuid());
            yamlConfiguration.set(string + ".name", (Object)inmate.name());
            yamlConfiguration.set(string + ".jailed-at", (Object)inmate.jailedAt());
            yamlConfiguration.set(string + ".jailed-by", (Object)inmate.jailedBy());
            yamlConfiguration.set(string + ".gamemode", (Object)(inmate.previousMode() == null ? GameMode.SURVIVAL.name() : inmate.previousMode().name()));
            Location location = inmate.returnLocation();
            if (location == null || location.getWorld() == null) continue;
            yamlConfiguration.set(string + ".return.world", (Object)location.getWorld().getName());
            yamlConfiguration.set(string + ".return.x", (Object)location.getX());
            yamlConfiguration.set(string + ".return.y", (Object)location.getY());
            yamlConfiguration.set(string + ".return.z", (Object)location.getZ());
            yamlConfiguration.set(string + ".return.yaw", (Object)Float.valueOf(location.getYaw()));
            yamlConfiguration.set(string + ".return.pitch", (Object)Float.valueOf(location.getPitch()));
        }
        try {
            this.plugin.getDataFolder().mkdirs();
            yamlConfiguration.save(this.file);
        }
        catch (IOException iOException) {
            this.plugin.getLogger().warning("jail.yml konnte nicht gespeichert werden: " + iOException.getMessage());
        }
    }

    public List<String> inmateNames() {
        ArrayList<String> arrayList = new ArrayList<String>();
        for (Inmate inmate : this.inmates.values()) {
            arrayList.add(inmate.name());
        }
        return arrayList;
    }

    public record Inmate(UUID uuid, String name, long jailedAt, String jailedBy, GameMode previousMode, Location returnLocation) {
    }
}

