/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.AdminRole;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class AdminAccess {
    private final AdminFieldPlugin plugin;
    private final Map<UUID, AdminRole> roles = new HashMap<UUID, AdminRole>();
    private final Map<UUID, String> names = new LinkedHashMap<UUID, String>();
    private UUID owner;
    private final Map<UUID, Integer> failures = new HashMap<UUID, Integer>();
    private final Map<UUID, Long> lockedUntil = new HashMap<UUID, Long>();
    private File file;

    public AdminAccess(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
    }

    public AdminRole role(UUID uUID) {
        return this.roles.get(uUID);
    }

    public boolean hasRole(UUID uUID) {
        return this.roles.containsKey(uUID);
    }

    public boolean isOwner(UUID uUID) {
        return this.roles.get(uUID) == AdminRole.OWNER;
    }

    public boolean ownerTaken() {
        return this.owner != null;
    }

    public UUID ownerId() {
        return this.owner;
    }

    public String ownerName() {
        if (this.owner == null) {
            return null;
        }
        return this.names.getOrDefault(this.owner, "Unbekannt");
    }

    public int adminCount() {
        int n = 0;
        for (AdminRole adminRole : this.roles.values()) {
            if (adminRole != AdminRole.ADMIN) continue;
            ++n;
        }
        return n;
    }

    public Map<UUID, AdminRole> all() {
        return new LinkedHashMap<UUID, AdminRole>(this.roles);
    }

    public String nameOf(UUID uUID) {
        return this.names.getOrDefault(uUID, "Unbekannt");
    }

    public String ownerCode() {
        return this.plugin.getConfig().getString("access.owner-code", "0510");
    }

    public int codeLength() {
        return Math.max(1, this.ownerCode().length());
    }

    public boolean claimOwner(Player player, String string) {
        UUID uUID = player.getUniqueId();
        if (this.isLockedOut(uUID)) {
            return false;
        }
        if (this.ownerTaken() && !uUID.equals(this.owner)) {
            return false;
        }
        if (!this.ownerCode().equals(string)) {
            this.registerFailure(player);
            return false;
        }
        this.failures.remove(uUID);
        this.lockedUntil.remove(uUID);
        this.owner = uUID;
        this.roles.put(uUID, AdminRole.OWNER);
        this.names.put(uUID, player.getName());
        this.save();
        this.plugin.log().add(ActivityLog.Level.ALERT, player.getName() + " ist jetzt Owner von AdminField", player.getLocation(), uUID);
        return true;
    }

    public boolean claimAdmin(Player player) {
        if (!player.isOp()) {
            return false;
        }
        UUID uUID = player.getUniqueId();
        if (this.isOwner(uUID)) {
            return false;
        }
        this.roles.put(uUID, AdminRole.ADMIN);
        this.names.put(uUID, player.getName());
        this.save();
        this.plugin.log().add(ActivityLog.Level.INFO, player.getName() + " ist jetzt AdminField-Admin", player.getLocation(), uUID);
        return true;
    }

    public boolean revoke(UUID uUID) {
        AdminRole adminRole = this.roles.remove(uUID);
        this.failures.remove(uUID);
        this.lockedUntil.remove(uUID);
        if (adminRole == null) {
            return false;
        }
        if (adminRole == AdminRole.OWNER && uUID.equals(this.owner)) {
            this.owner = null;
        }
        this.save();
        return true;
    }

    private void registerFailure(Player player) {
        UUID uUID = player.getUniqueId();
        int n = this.failures.merge(uUID, 1, Integer::sum);
        int n2 = Math.max(1, this.plugin.getConfig().getInt("access.max-attempts", 3));
        this.plugin.log().add(ActivityLog.Level.WARN, player.getName() + " gab einen falschen Owner-Code ein (" + n + "/" + n2 + ")", player.getLocation(), uUID);
        if (n >= n2) {
            long l = Math.max(5L, this.plugin.getConfig().getLong("access.lockout-seconds", 300L));
            this.lockedUntil.put(uUID, System.currentTimeMillis() + l * 1000L);
            this.failures.remove(uUID);
            this.plugin.log().add(ActivityLog.Level.ALERT, player.getName() + " wurde nach zu vielen Fehlversuchen gesperrt", player.getLocation(), uUID);
        }
    }

    public boolean isLockedOut(UUID uUID) {
        Long l = this.lockedUntil.get(uUID);
        return l != null && System.currentTimeMillis() < l;
    }

    public long lockoutSeconds(UUID uUID) {
        Long l = this.lockedUntil.get(uUID);
        if (l == null) {
            return 0L;
        }
        return Math.max(0L, (l - System.currentTimeMillis()) / 1000L);
    }

    public int attemptsLeft(UUID uUID) {
        int n = Math.max(1, this.plugin.getConfig().getInt("access.max-attempts", 3));
        return Math.max(0, n - this.failures.getOrDefault(uUID, 0));
    }

    public void load() {
        this.file = new File(this.plugin.getDataFolder(), "access.yml");
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)this.file);
        ConfigurationSection configurationSection = yamlConfiguration.getConfigurationSection("roles");
        if (configurationSection != null) {
            for (String string : configurationSection.getKeys(false)) {
                try {
                    UUID uUID = UUID.fromString(string);
                    AdminRole adminRole = AdminRole.byName(configurationSection.getString(string + ".role"));
                    if (adminRole == null) continue;
                    this.roles.put(uUID, adminRole);
                    this.names.put(uUID, configurationSection.getString(string + ".name", "Unbekannt"));
                    if (adminRole != AdminRole.OWNER) continue;
                    this.owner = uUID;
                }
                catch (IllegalArgumentException illegalArgumentException) {
                    this.plugin.getLogger().warning("Ungültiger Eintrag in access.yml: " + string);
                }
            }
        }
        if (!this.roles.isEmpty()) {
            this.plugin.getLogger().info(this.roles.size() + " AdminField-Ränge geladen" + (String)(this.owner != null ? " (Owner: " + this.ownerName() + ")" : "") + ".");
        }
    }

    public void save() {
        if (this.file == null) {
            this.file = new File(this.plugin.getDataFolder(), "access.yml");
        }
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        for (Map.Entry<UUID, AdminRole> entry : this.roles.entrySet()) {
            String string = "roles." + String.valueOf(entry.getKey());
            yamlConfiguration.set(string + ".role", (Object)entry.getValue().name());
            yamlConfiguration.set(string + ".name", (Object)this.names.getOrDefault(entry.getKey(), "Unbekannt"));
        }
        try {
            this.plugin.getDataFolder().mkdirs();
            yamlConfiguration.save(this.file);
        }
        catch (IOException iOException) {
            this.plugin.getLogger().warning("access.yml konnte nicht gespeichert werden: " + iOException.getMessage());
        }
    }

    public void refreshName(Player player) {
        if (this.roles.containsKey(player.getUniqueId())) {
            this.names.put(player.getUniqueId(), player.getName());
        }
    }

    public boolean ownerOnline() {
        return this.owner != null && Bukkit.getPlayer((UUID)this.owner) != null;
    }
}

