package de.ownercontrol;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class OwnerControlPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

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

    boolean isOwnerName(String name, UUID id) {
        for (String o : getConfig().getStringList("owners")) {
            if (o.equalsIgnoreCase(name) || o.equalsIgnoreCase(id.toString())) return true;
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        // Konsole darf nur die Ownerliste pflegen
        if (!(sender instanceof Player me)) {
            if (a.length == 3 && a[0].equalsIgnoreCase("owner")) return editOwners(sender, a);
            sender.sendMessage("Nur als Spieler. Owner eintragen: control owner add|remove <Name>");
            return true;
        }
        if (!isOwnerName(me.getName(), me.getUniqueId())) {
            sender.sendMessage("§cUnbekannter Befehl.");
            return true;
        }
        if (label.equalsIgnoreCase("uncontrol")) { stopCmd(me); return true; }
        if (a.length == 0) { help(me); return true; }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "stop" -> stopCmd(me);
            case "say" -> {
                Player t = need(me, a, 3);
                if (t != null) manager.chatAs(t, String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)));
            }
            case "run" -> {
                Player t = need(me, a, 3);
                if (t != null) manager.commandAs(t, String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)));
            }
            case "fx" -> {
                if (a.length < 3) { me.sendMessage("§e/control fx <Spieler> <" + String.join("|", Effects.NAMES) + ">"); break; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { me.sendMessage("§cSpieler nicht online."); break; }
                if (!Effects.apply(this, t, a[2].toLowerCase(Locale.ROOT))) me.sendMessage("§cUnbekannter Effekt.");
            }
            case "owner" -> me.sendMessage("§cDas geht nur in der Konsole.");
            default -> {
                Player t = Bukkit.getPlayerExact(a[0]);
                if (t == null) { me.sendMessage("§cSpieler '" + a[0] + "' ist nicht online."); break; }
                String err = manager.start(me, t);
                if (err != null) me.sendMessage("§c" + err);
                else me.sendMessage("§aDu steuerst jetzt " + t.getName() + ". Beenden mit /control stop. "
                        + "Chat laeuft ueber ihn, Befehle weiter als du.");
            }
        }
        return true;
    }

    private Player need(Player me, String[] a, int len) {
        if (a.length < len) { me.sendMessage("§e/control " + a[0] + " <Spieler> <...>"); return null; }
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
        me.sendMessage("§6/control <Spieler> §7- ihn steuern (kein Bestaetigen)");
        me.sendMessage("§6/control stop §7- beenden (auch /uncontrol)");
        me.sendMessage("§6/control say <Spieler> <Text> §7- als er chatten");
        me.sendMessage("§6/control run <Spieler> <Befehl> §7- Befehl als er ausfuehren");
        me.sendMessage("§6/control fx <Spieler> <Effekt> §7- " + String.join(", ", Effects.NAMES));
    }

    private boolean editOwners(CommandSender sender, String[] a) {
        List<String> owners = new ArrayList<>(getConfig().getStringList("owners"));
        if (a[1].equalsIgnoreCase("add")) { if (!owners.contains(a[2])) owners.add(a[2]); }
        else owners.removeIf(x -> x.equalsIgnoreCase(a[2]));
        getConfig().set("owners", owners);
        saveConfig();
        sender.sendMessage("Owner: " + owners);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player me) || !isOwnerName(me.getName(), me.getUniqueId())) return out;
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            out.addAll(List.of("stop", "say", "run", "fx"));
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 2 && List.of("say", "run", "fx").contains(a[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("fx")) {
            out.addAll(Effects.NAMES);
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
