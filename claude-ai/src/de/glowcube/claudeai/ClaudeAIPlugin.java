package de.glowcube.claudeai;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import de.glowcube.claudeai.brain.Brain;
import de.glowcube.claudeai.brain.Lexicon;
import de.glowcube.claudeai.brain.Memory;
import de.glowcube.claudeai.npc.Npc;

/**
 * ClaudeAI - eine KI-Mitspielerin, die komplett im Server laeuft. Kein API-Schluessel,
 * kein externes Programm: Sprachverstaendnis, Planung, Wegfindung und Bauen stecken alle
 * in diesem Plugin.
 */
public final class ClaudeAIPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private Settings settings;
    private Npc npc;
    private Brain brain;
    private BukkitTask ticker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        settings = Settings.load(getConfig());
        npc = new Npc(this);
        brain = new Brain(this, npc, new Memory(new File(getDataFolder(), "memory.yml")));
        Bukkit.getPluginManager().registerEvents(new Events(this), this);
        PluginCommand cmd = getCommand("claude");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(this, this::restore, 40L);
        getLogger().info(settings.name + " ist bereit. Im Spiel: /spawn claude");
    }

    @Override
    public void onDisable() {
        if (ticker != null) ticker.cancel();
        if (npc != null) {
            saveState();
            if (npc.isSpawned()) npc.despawn();
        }
        if (brain != null) brain.memory().save();
    }

    private void tick() {
        try {
            npc.tick();
        } catch (RuntimeException ex) {
            getLogger().warning("Fehler im Takt: " + ex);
        }
    }

    public Settings settings() {
        return settings;
    }

    public Npc npc() {
        return npc;
    }

    public Brain brain() {
        return brain;
    }

    // ================================================================== Rufen

    public void summon(Player p) {
        if (!p.hasPermission("claudeai.spawn")) {
            p.sendMessage("§cDas darfst du nicht.");
            return;
        }
        Location spot = spotBeside(p);
        boolean was = npc.isSpawned();
        if (was) npc.teleportNear(spot);
        else npc.spawn(spot);
        npc.setPartner(p);
        npc.setActor(p);
        if (settings.followAfterSpawn) npc.setMode(Npc.Mode.FOLLOW, p, null);
        String who = brain.memory().nicknames.getOrDefault(p.getName().toLowerCase(Locale.ROOT), p.getName());
        npc.sayLater(was ? "Da bin ich, " + who + "!" : Lexicon.pick(Lexicon.GREET, npc.random()).replace("%p", who)
                + " Sag 'was kannst du?', wenn du wissen willst, was ich alles kann.", 15);
    }

    /** Rechts neben dem Spieler, sonst irgendwo nah dran. */
    private static Location spotBeside(Player p) {
        Location base = p.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        Location right = base.clone().add(-fz * 1.5, 0, fx * 1.5);
        Location spot = Npc.standableNear(right, 1);
        if (spot == null) spot = Npc.standableNear(base, 3);
        if (spot == null) spot = base.clone();
        double dx = base.getX() - spot.getX(), dz = base.getZ() - spot.getZ();
        spot.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        return spot;
    }

    public void dismiss(CommandSender by) {
        if (!npc.isSpawned()) {
            by.sendMessage("§e" + settings.name + " ist gar nicht da.");
            return;
        }
        npc.say("Tschuess! Ruft mich mit /spawn claude, wenn ihr mich braucht.");
        Bukkit.getScheduler().runTaskLater(this, npc::despawn, 20L);
    }

    // ================================================================== /claude

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn", "komm" -> {
                if (sender instanceof Player p) summon(p);
                else sender.sendMessage("Nur im Spiel.");
            }
            case "despawn", "weg" -> {
                if (sender.hasPermission("claudeai.spawn")) dismiss(sender);
            }
            case "stop" -> {
                npc.stopAll();
                npc.setMode(Npc.Mode.IDLE, null, null);
                sender.sendMessage("§a" + settings.name + " hat aufgehoert.");
            }
            case "inventar", "inv" -> {
                if (sender instanceof Player p && sender.hasPermission("claudeai.use")) p.openInventory(npc.getInventory());
            }
            case "reload" -> {
                if (!sender.hasPermission("claudeai.admin")) {
                    sender.sendMessage("§cDas darfst du nicht.");
                    return true;
                }
                reloadConfig();
                settings = Settings.load(getConfig());
                sender.sendMessage("§aClaudeAI neu geladen.");
            }
            default -> {
                sender.sendMessage("§6" + settings.name + ": " + (npc.isSpawned()
                        ? "§aim Spiel§7, " + (npc.currentTask() != null ? npc.currentTask().label() : "hat gerade nichts zu tun")
                        : "§enicht da"));
                sender.sendMessage("§7/spawn claude§8 rufen §7| /despawn claude§8 wegschicken §7| "
                        + "/claude inventar | stop | reload");
                sender.sendMessage("§7Einfach im Chat mit " + settings.name + " reden - z.B. 'Claude, folge mir' oder 'Claude, bau ein Haus'.");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[] { "spawn", "despawn", "status", "inventar", "stop", "reload" }) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        }
        return out;
    }

    // ================================================================== Speichern

    private File stateFile() {
        return new File(getDataFolder(), "state.yml");
    }

    private void saveState() {
        YamlConfiguration y = new YamlConfiguration();
        Location l = npc.lastLocation();
        y.set("spawned", npc.isSpawned());
        if (l != null) {
            y.set("world", l.getWorld().getName());
            y.set("x", l.getX());
            y.set("y", l.getY());
            y.set("z", l.getZ());
        }
        y.set("mode", npc.mode().name());
        y.set("mode-player", npc.modePlayerId() == null ? null : npc.modePlayerId().toString());
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack s : npc.getInventory().getContents()) if (s != null) items.add(s);
        y.set("inventory", items);
        try {
            getDataFolder().mkdirs();
            y.save(stateFile());
        } catch (IOException ex) {
            getLogger().warning("Konnte Zustand nicht speichern: " + ex.getMessage());
        }
    }

    private void restore() {
        File f = stateFile();
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<ItemStack> items = new ArrayList<>();
        List<?> raw = y.getList("inventory");
        if (raw != null) for (Object o : raw) if (o instanceof ItemStack s) items.add(s);
        if (!y.getBoolean("spawned", false)) {
            for (ItemStack s : items) npc.getInventory().addItem(s);
            return;
        }
        World w = Bukkit.getWorld(y.getString("world", ""));
        if (w == null) return;
        Location at = new Location(w, y.getDouble("x", 0), y.getDouble("y", 64), y.getDouble("z", 0));
        Npc.Mode mode;
        try {
            mode = Npc.Mode.valueOf(y.getString("mode", "IDLE"));
        } catch (IllegalArgumentException ex) {
            mode = Npc.Mode.IDLE;
        }
        String mp = y.getString("mode-player", null);
        npc.restore(at, mode, mp == null ? null : UUID.fromString(mp), items);
    }
}
