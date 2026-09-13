/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.ActivityLog;
import de.adminfield.AdminAccess;
import de.adminfield.AdminCommand;
import de.adminfield.AdminListeners;
import de.adminfield.AdminState;
import de.adminfield.Assassin;
import de.adminfield.BuildTracker;
import de.adminfield.CheatWatch;
import de.adminfield.Curses;
import de.adminfield.DeathNote;
import de.adminfield.Jail;
import de.adminfield.LuckyBlocks;
import de.adminfield.OwnerItems;
import de.adminfield.OwnerPowers;
import de.adminfield.Ui;
import de.adminfield.disguise.MobDisguise;
import de.adminfield.disguise.NameDisguise;
import de.adminfield.menu.Menu;
import de.adminfield.nms.PlayerDisguise;
import java.lang.management.ManagementFactory;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdminFieldPlugin
extends JavaPlugin {
    private AdminState state;
    private BuildTracker tracker;
    private ActivityLog log;
    private Jail jail;
    private AdminAccess access;
    private LuckyBlocks luckyBlocks;
    private OwnerItems ownerItems;
    private OwnerPowers powers;
    private CheatWatch cheats;
    private Assassin assassin;
    private PlayerDisguise disguise;
    private DeathNote deathNote;
    private Curses curses;
    private long enabledAt;

    public void onEnable() {
        this.saveDefaultConfig();
        this.enabledAt = System.currentTimeMillis();
        this.state = new AdminState(this);
        this.log = new ActivityLog(this);
        this.tracker = new BuildTracker(this);
        this.tracker.load();
        this.jail = new Jail(this);
        this.jail.load();
        this.access = new AdminAccess(this);
        this.access.load();
        this.ownerItems = new OwnerItems(this);
        this.powers = new OwnerPowers(this);
        this.cheats = new CheatWatch(this);
        this.disguise = null;
        if (this.getConfig().getBoolean("assassin.real-skin", false)) {
            try {
                this.disguise = new PlayerDisguise(this);
                Bukkit.getPluginManager().registerEvents((Listener)this.disguise, (Plugin)this);
                this.getLogger().info("Attentaeter-Skin (experimentell) ist aktiv.");
            }
            catch (Throwable throwable) {
                this.disguise = null;
                this.getLogger().warning("Spieler-Skins fuer den Attentaeter nicht verfuegbar (" + throwable.getClass().getSimpleName() + ") - nutze Kopf-Variante.");
            }
        }
        this.assassin = new Assassin(this);
        this.deathNote = new DeathNote(this);
        this.deathNote.load();
        this.curses = new Curses(this);
        this.curses.load();
        this.luckyBlocks = new LuckyBlocks(this);
        Bukkit.getPluginManager().registerEvents((Listener)new AdminListeners(this), (Plugin)this);
        AdminCommand adminCommand = new AdminCommand(this);
        Objects.requireNonNull(this.getCommand("admin"), "Befehl 'admin' fehlt in der plugin.yml").setExecutor((CommandExecutor)adminCommand);
        Objects.requireNonNull(this.getCommand("admin")).setTabCompleter((TabCompleter)adminCommand);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, this::refreshOpenMenus, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, () -> this.jail.tick(), 40L, 40L);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, () -> this.cheats.tick(4), 20L, 4L);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, () -> this.assassin.tick(), 20L, 10L);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, () -> this.deathNote.tick(), 100L, 100L);
        Bukkit.getScheduler().runTaskTimer((Plugin)this, () -> this.curses.tick(), 40L, 20L);
        MobDisguise.get(this);
        NameDisguise.get(this);
        this.log.add(ActivityLog.Level.INFO, "AdminField gestartet");
        this.getLogger().info("AdminField aktiv - " + this.tracker.count() + " bekannte Baustellen.");
    }

    public void onDisable() {
        MobDisguise.stop(this);
        NameDisguise.stop(this);
        if (this.assassin != null) {
            this.assassin.dismissAll();
        }
        if (this.tracker != null) {
            this.tracker.save();
        }
        if (this.jail != null) {
            this.jail.save();
        }
        if (this.deathNote != null) {
            this.deathNote.save();
        }
        if (this.curses != null) {
            this.curses.save();
        }
    }

    private void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Menu menu;
            InventoryHolder inventoryHolder = player.getOpenInventory().getTopInventory().getHolder();
            if (!(inventoryHolder instanceof Menu) || !(menu = (Menu)inventoryHolder).live()) continue;
            menu.redraw();
        }
    }

    public AdminState state() {
        return this.state;
    }

    public BuildTracker tracker() {
        return this.tracker;
    }

    public ActivityLog log() {
        return this.log;
    }

    public Jail jail() {
        return this.jail;
    }

    public AdminAccess access() {
        return this.access;
    }

    public OwnerItems ownerItems() {
        return this.ownerItems;
    }

    public OwnerPowers powers() {
        return this.powers;
    }

    public CheatWatch cheats() {
        return this.cheats;
    }

    public Assassin assassin() {
        return this.assassin;
    }

    public PlayerDisguise disguise() {
        return this.disguise;
    }

    public DeathNote deathNote() {
        return this.deathNote;
    }

    public Curses curses() {
        return this.curses;
    }

    public boolean isOwner(CommandSender commandSender) {
        if (commandSender instanceof Player) {
            Player player = (Player)commandSender;
            return this.access.isOwner(player.getUniqueId());
        }
        return true;
    }

    public void giveItems(Player player, ItemStack ... itemStackArray) {
        for (ItemStack itemStack : itemStackArray) {
            if (itemStack == null) continue;
            for (ItemStack itemStack2 : player.getInventory().addItem(new ItemStack[]{itemStack}).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), itemStack2);
            }
        }
    }

    public LuckyBlocks luckyBlocks() {
        return this.luckyBlocks;
    }

    public long serverUptime() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }

    public long pluginUptime() {
        return System.currentTimeMillis() - this.enabledAt;
    }

    public boolean hasRank(CommandSender commandSender) {
        if (!commandSender.hasPermission("adminfield.use")) {
            return false;
        }
        if (this.getConfig().getBoolean("require-op", true) && commandSender instanceof Player) {
            Player player = (Player)commandSender;
            return player.isOp();
        }
        return true;
    }

    public boolean hasAccess(CommandSender commandSender) {
        if (!this.hasRank(commandSender)) {
            return false;
        }
        if (commandSender instanceof Player) {
            Player player = (Player)commandSender;
            return this.access.hasRole(player.getUniqueId());
        }
        return true;
    }

    public Component prefix() {
        return Ui.mm(this.getConfig().getString("messages.prefix", "<gradient:#5ad1ff:#a06bff><bold>AdminField</bold></gradient> <dark_gray>»</dark_gray> "));
    }

    public void send(CommandSender commandSender, String string) {
        commandSender.sendMessage(this.prefix().append(Ui.mm(string)));
    }

    public void notifyAdmins(String string) {
        Component component = this.prefix().append(Ui.mm(string));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("adminfield.notify") || !this.access.hasRole(player.getUniqueId())) continue;
            player.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }

    public void teleport(Player player, Location location, String string) {
        if (location == null || location.getWorld() == null) {
            this.send((CommandSender)player, "<red>Dieses Ziel liegt in einer Welt, die gerade nicht geladen ist.");
            return;
        }
        this.state.pushBack(player);
        player.teleportAsync(location).thenAccept(bl -> {
            if (Boolean.TRUE.equals(bl)) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.4f);
            }
        });
        this.send((CommandSender)player, "<gray>Teleportiert zu <white>" + string + "<gray>.");
        this.log.add(ActivityLog.Level.INFO, player.getName() + " teleportierte zu " + string, location, player.getUniqueId());
    }
}

