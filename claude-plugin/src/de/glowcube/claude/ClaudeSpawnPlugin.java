package de.glowcube.claude;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Holt Claude (den Bot aus claude-bot/) per "/spawn claude" neben den Spieler.
 *
 * Der Bot ist die ganze Zeit mit dem Server verbunden, wartet aber unsichtbar als
 * Zuschauer, fuer niemanden sichtbar und ohne Beitrittsmeldung. "/spawn claude"
 * macht ihn sichtbar, setzt den Spielmodus und teleportiert ihn direkt neben den
 * Rufenden. "/despawn claude" (oder Claude selbst) schickt ihn zurueck ins Warten.
 *
 * Absprache mit dem Bot ueber den Plugin-Kanal "claude:control":
 *   Bot    -> Plugin: hello|1|standby, hello|1|active, leave
 *   Plugin -> Bot:    state|standby, state|active|Spieler, summon|Spieler, dismiss|Spieler
 */
public final class ClaudeSpawnPlugin extends JavaPlugin implements Listener, PluginMessageListener, CommandExecutor, TabCompleter {

    static final String CHANNEL = "claude:control";

    private String botName = "Claude";
    private GameMode gameMode = GameMode.SURVIVAL;
    private boolean announce = true;
    private String msgSpawned = "";
    private String msgGone = "";
    private String msgOffline = "";

    /** Soll Claude gerade sichtbar im Spiel sein? */
    private boolean active = false;
    private String summoner = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("claude");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        // Nach /reload koennte der Bot schon online sein
        Player bot = bot();
        if (bot != null) hide(bot);
        getLogger().info("Bereit. Bot-Name: " + botName + " - rufen mit /spawn claude");
    }

    private void loadSettings() {
        reloadConfig();
        botName = getConfig().getString("bot-name", "Claude");
        try {
            gameMode = GameMode.valueOf(getConfig().getString("gamemode", "SURVIVAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            gameMode = GameMode.SURVIVAL;
        }
        announce = getConfig().getBoolean("announce", true);
        msgSpawned = color(getConfig().getString("messages.spawned", "&e%bot% ist da!"));
        msgGone = color(getConfig().getString("messages.gone", "&e%bot% ist verschwunden."));
        msgOffline = color(getConfig().getString("messages.offline",
                "&c%bot% ist nicht verbunden. Starte zuerst das Bot-Programm (claude-bot)."));
    }

    private static String color(String s) {
        return s == null ? "" : s.replace('&', '§');
    }

    private String fill(String template, String player) {
        return template.replace("%bot%", botName).replace("%player%", player == null ? "" : player);
    }

    boolean isBot(Player p) {
        return p != null && p.getName().equalsIgnoreCase(botName);
    }

    Player bot() {
        return getServer().getPlayerExact(botName);
    }

    // ---------------------------------------------------------------- Sichtbarkeit

    private void hide(Player bot) {
        bot.setGameMode(GameMode.SPECTATOR);
        for (Player p : getServer().getOnlinePlayers()) {
            if (p != bot) p.hidePlayer(this, bot);
        }
    }

    private void reveal(Player bot) {
        for (Player p : getServer().getOnlinePlayers()) {
            if (p != bot) p.showPlayer(this, bot);
        }
        bot.setGameMode(gameMode);
    }

    private void broadcast(String message) {
        for (Player p : getServer().getOnlinePlayers()) p.sendMessage(message);
        getLogger().info(message);
    }

    /** Beitritts-/Austrittsmeldung unterdruecken - die alten String-Methoden sind in Paper veraltet. */
    private static void silence(Runnable r) {
        try {
            r.run();
        } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
            // Dann sieht man eben die Meldung - Hauptsache, der Rest funktioniert.
        }
    }

    private void send(Player bot, String message) {
        bot.sendPluginMessage(this, CHANNEL, message.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- Rufen und Wegschicken

    void summon(Player caller) {
        if (!caller.hasPermission("claudebot.summon")) {
            caller.sendMessage("§cDas darfst du nicht.");
            return;
        }
        Player bot = bot();
        if (bot == null) {
            caller.sendMessage(fill(msgOffline, caller.getName()));
            return;
        }
        if (bot == caller) return;
        boolean wasActive = active;
        active = true;
        summoner = caller.getName();
        // Erst hinstellen, dann sichtbar machen - sonst faellt er kurz irgendwo herum
        bot.teleport(spotNextTo(caller));
        reveal(bot);
        send(bot, "summon|" + caller.getName());
        if (announce && !wasActive) broadcast(fill(msgSpawned, caller.getName()));
    }

    void dismiss(String by) {
        Player bot = bot();
        boolean wasActive = active;
        active = false;
        summoner = null;
        if (bot == null) return;
        hide(bot);
        send(bot, "dismiss|" + (by == null ? "-" : by));
        if (announce && wasActive) broadcast(fill(msgGone, by));
    }

    /** Rechts neben dem Spieler, sonst links, hinten, vorne - Hauptsache Platz fuer Fuesse und Kopf. */
    static Location spotNextTo(Player player) {
        Location base = player.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double[][] offsets = { { -fz, fx }, { fz, -fx }, { -fx, -fz }, { fx, fz } };
        for (double[] off : offsets) {
            Location spot = base.clone().add(off[0] * 1.5, 0, off[1] * 1.5);
            if (spot.getBlock().isPassable() && spot.clone().add(0, 1, 0).getBlock().isPassable()) {
                face(spot, base);
                return spot;
            }
        }
        return base.clone();
    }

    private static void face(Location from, Location target) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        from.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        from.setPitch(0f);
    }

    // ---------------------------------------------------------------- Ereignisse

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (isBot(p)) {
            if (!active) {
                hide(p);
                silence(() -> event.setJoinMessage(null));
            }
            return;
        }
        Player bot = bot();
        if (bot != null && !active) p.hidePlayer(this, bot);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (isBot(event.getPlayer()) && !active) silence(() -> event.setQuitMessage(null));
    }

    /** "/spawn claude" abfangen, bevor ein anderes Plugin (z.B. Essentials /spawn) es bekommt. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String name = botName.toLowerCase(Locale.ROOT);
        if (msg.equals("/spawn " + name) || msg.equals("/spawn claude")) {
            event.setCancelled(true);
            summon(event.getPlayer());
        } else if (msg.equals("/despawn " + name) || msg.equals("/despawn claude")) {
            event.setCancelled(true);
            if (event.getPlayer().hasPermission("claudebot.summon")) dismiss(event.getPlayer().getName());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || !isBot(player)) return;
        String[] parts = new String(message, StandardCharsets.UTF_8).split("\\|");
        switch (parts[0]) {
            case "hello" -> {
                boolean wantsActive = parts.length > 2 && parts[2].equals("active");
                if (wantsActive && !active) {
                    // Bot laeuft ohne Warte-Modus: einfach sichtbar da sein
                    active = true;
                    reveal(player);
                    if (announce) broadcast(fill(msgSpawned, null));
                }
                if (active) {
                    reveal(player);
                    send(player, "state|active|" + (summoner == null ? "-" : summoner));
                } else {
                    hide(player);
                    send(player, "state|standby");
                }
            }
            case "leave" -> dismiss(player.getName());
            default -> { }
        }
    }

    // ---------------------------------------------------------------- /claude

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn", "komm" -> {
                if (sender instanceof Player p) summon(p);
                else sender.sendMessage("Nur im Spiel moeglich.");
            }
            case "despawn", "weg" -> {
                if (!sender.hasPermission("claudebot.summon")) {
                    sender.sendMessage("§cDas darfst du nicht.");
                } else {
                    dismiss(sender.getName());
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("claudebot.admin")) {
                    sender.sendMessage("§cDas darfst du nicht.");
                } else {
                    loadSettings();
                    sender.sendMessage("§aClaudeSpawn neu geladen. Bot-Name: " + botName);
                }
            }
            default -> {
                Player bot = bot();
                String state = bot == null ? "§cnicht verbunden"
                        : active ? "§aim Spiel" + (summoner != null ? " (gerufen von " + summoner + ")" : "")
                        : "§ewartet unsichtbar";
                sender.sendMessage("§6" + botName + ": " + state);
                sender.sendMessage("§7/spawn claude §8- neben dich holen, §7/despawn claude §8- wegschicken");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[] { "spawn", "despawn", "status", "reload" }) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        }
        return out;
    }
}
