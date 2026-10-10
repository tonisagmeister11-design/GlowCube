package de.ownercontrol;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class OwnerControlPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("stop", "say", "run", "fx", "letkill", "stopkill",
            "freeze", "mute", "bring", "inv", "ender", "strike", "help");

    private ControlManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new ControlManager(this);
        Bukkit.getPluginManager().registerEvents(manager, this);
        getCommand("control").setExecutor(this);
        getCommand("control").setTabCompleter(this);
        getCommand("uncontrol").setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.stopAll();
    }

    /** Admin (OP oder Permission control.admin) - mehr braucht es nicht, aber zusaetzlich das Passwort. */
    private boolean isAdmin(Player p) { return p.isOp() || p.hasPermission("control.admin"); }

    private boolean unlocked(Player p) { return getConfig().getStringList("unlocked").contains(p.getUniqueId().toString()); }

    private boolean checkPassword(String given) {
        String pw = getConfig().getString("password");
        if (pw == null || pw.isEmpty()) return false;
        return MessageDigest.isEqual(pw.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    /** Meldung an die Konsole (Verlauf fuer den Server-Betreiber). */
    void tell(String msg) { getLogger().info(msg); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player me) || !isAdmin(me)) {
            sender.sendMessage("§cUnbekannter Befehl.");
            return true;
        }
        if (!unlocked(me)) {
            if (a.length == 1 && checkPassword(a[0])) {
                List<String> l = new ArrayList<>(getConfig().getStringList("unlocked"));
                l.add(me.getUniqueId().toString());
                getConfig().set("unlocked", l);
                saveConfig();
                me.sendMessage("§aFreigeschaltet. /control help zeigt alle Befehle.");
            } else {
                sender.sendMessage("§cUnbekannter Befehl.");
            }
            return true;
        }
        if (label.equalsIgnoreCase("uncontrol")) { stopCmd(me); return true; }
        if (a.length == 0) { help(me); return true; }

        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(me);
            case "stop" -> stopCmd(me);
            case "say" -> {
                Player t = need(me, a, 3);
                if (t != null) manager.chatAs(t, rest(a, 2));
            }
            case "run" -> {
                Player t = need(me, a, 3);
                if (t != null) manager.commandAs(t, rest(a, 2));
            }
            case "fx" -> {
                Player t = need(me, a, 3);
                if (t != null && !Effects.apply(this, t, a[2].toLowerCase(Locale.ROOT))) {
                    me.sendMessage("§cUnbekannter Effekt. Moeglich: " + String.join(", ", Effects.NAMES));
                }
            }
            case "letkill" -> {
                if (a.length < 3) { me.sendMessage("§e/control letkill <Bot> <Ziel>"); break; }
                Player bot = Bukkit.getPlayerExact(a[1]), victim = Bukkit.getPlayerExact(a[2]);
                if (bot == null || victim == null) { me.sendMessage("§cBeide muessen online sein."); break; }
                if (manager.isBusy(bot)) { me.sendMessage("§c" + bot.getName() + " ist gerade belegt."); break; }
                String err = manager.bots.start(bot, victim);
                me.sendMessage(err != null ? "§c" + err
                        : "§a" + bot.getName() + " jagt jetzt " + victim.getName() + " (Abbruch: /control stopkill " + bot.getName() + ")");
            }
            case "stopkill" -> {
                Player t = need(me, a, 2);
                if (t != null) me.sendMessage(manager.bots.stop(t) ? "§aBot beendet." : "§cIst kein Bot.");
            }
            case "freeze" -> {
                Player t = need(me, a, 2);
                if (t != null) me.sendMessage(manager.toggleFreeze(t) ? "§b" + t.getName() + " eingefroren."
                        : "§a" + t.getName() + " ist wieder frei.");
            }
            case "mute" -> {
                Player t = need(me, a, 2);
                if (t != null) me.sendMessage(manager.toggleMute(t) ? "§e" + t.getName() + " stummgeschaltet."
                        : "§a" + t.getName() + " darf wieder schreiben.");
            }
            case "bring" -> {
                Player t = need(me, a, 2);
                if (t != null) t.teleport(me.getLocation());
            }
            case "inv" -> {
                Player t = need(me, a, 2);
                if (t != null) me.openInventory(t.getInventory());
            }
            case "ender" -> {
                Player t = need(me, a, 2);
                if (t != null) me.openInventory(t.getEnderChest());
            }
            case "strike" -> {
                Player t = need(me, a, 2);
                if (t == null) break;
                if (a.length > 2 && a[2].equalsIgnoreCase("echt")) t.getWorld().strikeLightning(t.getLocation());
                else t.getWorld().strikeLightningEffect(t.getLocation());
            }
            default -> {
                Player t = Bukkit.getPlayerExact(a[0]);
                if (t == null) { me.sendMessage("§cSpieler '" + a[0] + "' ist nicht online. /control help"); break; }
                String err = manager.start(me, t);
                me.sendMessage(err != null ? "§c" + err
                        : "§aDu steuerst jetzt " + t.getName() + " mit WASD, Maus, Springen, Hotbar, Bauen und Kaempfen. "
                        + "Chat laeuft ueber ihn. Beenden: /control stop");
            }
        }
        return true;
    }

    private static String rest(String[] a, int from) { return String.join(" ", Arrays.copyOfRange(a, from, a.length)); }

    private Player need(Player me, String[] a, int len) {
        if (a.length < len) { me.sendMessage("§eZu wenig Angaben. /control help"); return null; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) me.sendMessage("§cSpieler nicht online.");
        return t;
    }

    private void stopCmd(Player me) {
        Session s = manager.ofOwner(me);
        if (s == null) me.sendMessage("§cDu steuerst gerade niemanden.");
        else manager.stop(s, true);
    }

    private void help(Player me) {
        me.sendMessage("§6/control <Spieler> §7- ihn komplett steuern (WASD)");
        me.sendMessage("§6/control stop §7- beenden (auch /uncontrol)");
        me.sendMessage("§6/control letkill <Bot> <Ziel> §7- Bot jagt Ziel mit Kill Aura");
        me.sendMessage("§6/control stopkill <Bot> §7- Bot stoppen");
        me.sendMessage("§6/control say|run <Spieler> <...> §7- als er chatten / Befehl ausfuehren");
        me.sendMessage("§6/control fx <Spieler> <Effekt> §7- " + String.join(", ", Effects.NAMES));
        me.sendMessage("§6/control freeze|mute|bring|inv|ender <Spieler>");
        me.sendMessage("§6/control strike <Spieler> [echt] §7- Blitz");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player me) || !isAdmin(me) || !unlocked(me)) return out;
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        String first = a[0].toLowerCase(Locale.ROOT);
        boolean players = a.length == 1
                || (a.length == 2 && SUBS.contains(first) && !first.equals("help") && !first.equals("stop"))
                || (a.length == 3 && first.equals("letkill"));
        if (a.length == 1) out.addAll(SUBS);
        if (players) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        if (a.length == 3 && first.equals("fx")) out.addAll(Effects.NAMES);
        if (a.length == 3 && first.equals("strike")) out.add("echt");
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
