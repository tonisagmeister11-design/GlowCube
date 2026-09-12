/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.menu.BuildMenu;
import de.adminfield.menu.JailMenu;
import de.adminfield.menu.LogMenu;
import de.adminfield.menu.MainMenu;
import de.adminfield.menu.PlayerListMenu;
import de.adminfield.menu.RoleMenu;
import de.adminfield.menu.ServerMenu;
import de.adminfield.menu.ToolsMenu;
import de.adminfield.menu.WorldMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class AdminCommand
implements CommandExecutor,
TabCompleter {
    private final AdminFieldPlugin plugin;

    public AdminCommand(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        Object object;
        if (!this.plugin.hasRank(commandSender)) {
            this.plugin.send(commandSender, "<red>AdminField ist nur für Serveradmins.");
            return true;
        }
        if (commandSender instanceof Player) {
            object = (Player)commandSender;
            if (!this.plugin.access().hasRole(object.getUniqueId())) {
                new RoleMenu(this.plugin).open((Player)object);
                return true;
            }
        }
        if (stringArray.length == 0) {
            if (!(commandSender instanceof Player)) {
                this.plugin.send(commandSender, "<gray>Das Menü geht nur im Spiel. Nutze <white>/admin hilfe<gray>.");
                return true;
            }
            object = (Player)commandSender;
            new MainMenu(this.plugin).open((Player)object);
            return true;
        }
        block24 : switch (stringArray[0].toLowerCase(Locale.ROOT)) {
            case "tp": {
                if (!(commandSender instanceof Player)) {
                    this.plugin.send(commandSender, "<red>Nur im Spiel möglich.");
                    return true;
                }
                Player player = (Player)commandSender;
                if (stringArray.length < 5) {
                    this.plugin.send(commandSender, "<gray>/admin tp [welt] [x] [y] [z]");
                    return true;
                }
                World world = Bukkit.getWorld((String)stringArray[1]);
                if (world == null) {
                    this.plugin.send(commandSender, "<red>Unbekannte Welt: <white>" + stringArray[1]);
                    return true;
                }
                try {
                    int n = Integer.parseInt(stringArray[2]);
                    int n2 = Integer.parseInt(stringArray[3]);
                    int n3 = Integer.parseInt(stringArray[4]);
                    int n4 = world.getHighestBlockYAt(n, n3);
                    int n5 = Math.max(n2, Math.min(n4 + 1, world.getMaxHeight() - 2));
                    this.plugin.teleport(player, new Location(world, (double)n + 0.5, (double)n5, (double)n3 + 0.5), n + " / " + n2 + " / " + n3);
                }
                catch (NumberFormatException numberFormatException) {
                    this.plugin.send(commandSender, "<red>Die Koordinaten müssen Zahlen sein.");
                }
                break;
            }
            case "spieler": 
            case "players": {
                if (!(commandSender instanceof Player)) break;
                Player player = (Player)commandSender;
                new PlayerListMenu(this.plugin, null).open(player);
                break;
            }
            case "bauwerke": 
            case "sites": 
            case "builds": {
                if (!(commandSender instanceof Player)) break;
                Player player = (Player)commandSender;
                new BuildMenu(this.plugin, null).open(player);
                break;
            }
            case "verlauf": 
            case "log": {
                if (!(commandSender instanceof Player)) break;
                Player player = (Player)commandSender;
                new LogMenu(this.plugin, null).open(player);
                break;
            }
            case "server": 
            case "status": {
                if (commandSender instanceof Player) {
                    Player player = (Player)commandSender;
                    new ServerMenu(this.plugin, null).open(player);
                    break;
                }
                double[] dArray = Bukkit.getServer().getTPS();
                this.plugin.send(commandSender, "<gray>TPS: <white>" + String.format("%.2f", dArray[0]) + "<gray>, Spieler: <white>" + Bukkit.getOnlinePlayers().size() + "<gray>, Bauwerke: <white>" + this.plugin.tracker().confirmed().size());
                break;
            }
            case "welt": 
            case "world": {
                if (!(commandSender instanceof Player)) break;
                Player player = (Player)commandSender;
                new WorldMenu(this.plugin, null, player.getWorld()).open(player);
                break;
            }
            case "werkzeuge": 
            case "tools": {
                if (!(commandSender instanceof Player)) break;
                Player player = (Player)commandSender;
                new ToolsMenu(this.plugin, null).open(player);
                break;
            }
            case "gefaengnis": 
            case "gefängnis": 
            case "jail": {
                String string2;
                if (!(commandSender instanceof Player)) {
                    this.plugin.send(commandSender, "<red>Nur im Spiel möglich.");
                    return true;
                }
                Player player = (Player)commandSender;
                switch (string2 = stringArray.length >= 2 ? stringArray[1].toLowerCase(Locale.ROOT) : "") {
                    case "festlegen": 
                    case "set": {
                        this.plugin.jail().beginSetup(player, null);
                        break block24;
                    }
                    case "abbrechen": 
                    case "cancel": {
                        this.plugin.jail().cancelSetup(player.getUniqueId());
                        this.plugin.send((CommandSender)player, "<gray>Auswahl abgebrochen.");
                        break block24;
                    }
                }
                new JailMenu(this.plugin, null).open(player);
                break;
            }
            case "zugang": 
            case "pin": {
                if (stringArray.length >= 3 && (stringArray[1].equalsIgnoreCase("reset") || stringArray[1].equalsIgnoreCase("sperren"))) {
                    Player player = Bukkit.getPlayerExact((String)stringArray[2]);
                    if (player == null) {
                        this.plugin.send(commandSender, "<red>Spieler nicht gefunden: <white>" + stringArray[2]);
                        return true;
                    }
                    if (!this.plugin.isOwner(commandSender)) {
                        this.plugin.send(commandSender, "<red>Ränge vergeben darf nur der Owner.");
                        return true;
                    }
                    if (this.plugin.access().revoke(player.getUniqueId())) {
                        this.plugin.send(commandSender, "<gray>Rang von <white>" + player.getName() + "<gray> entfernt – er muss beim nächsten /admin neu wählen.");
                    } else {
                        this.plugin.send(commandSender, "<gray>" + player.getName() + " hatte gar keinen Rang.");
                    }
                    return true;
                }
                String string3 = this.plugin.access().ownerName();
                this.plugin.send(commandSender, "<gray>Owner: " + (String)(string3 == null ? "<red>noch keiner" : "<gold>" + string3));
                this.plugin.send(commandSender, "<gray>Admins: <white>" + this.plugin.access().adminCount());
                this.plugin.send(commandSender, "<dark_gray>/admin zugang reset [spieler] nimmt einen Rang weg.");
                break;
            }
            case "reload": {
                this.plugin.reloadConfig();
                this.plugin.send(commandSender, "<green>Konfiguration neu geladen.");
                break;
            }
            case "save": {
                this.plugin.tracker().save();
                this.plugin.send(commandSender, "<green>Baustellen gespeichert.");
                break;
            }
            default: {
                this.help(commandSender);
            }
        }
        return true;
    }

    private void help(CommandSender commandSender) {
        this.plugin.send(commandSender, "<gradient:#5ad1ff:#a06bff><bold>AdminField</bold></gradient> <gray>– Übersicht");
        commandSender.sendMessage(Ui.mm("<gray> /admin <dark_gray>– Kontrollzentrum öffnen"));
        commandSender.sendMessage(Ui.mm("<gray> /admin spieler <dark_gray>– Spielerliste"));
        commandSender.sendMessage(Ui.mm("<gray> /admin bauwerke <dark_gray>– erkannte Bauwerke"));
        commandSender.sendMessage(Ui.mm("<gray> /admin verlauf <dark_gray>– Ereignisverlauf"));
        commandSender.sendMessage(Ui.mm("<gray> /admin server <dark_gray>– Serverstatus"));
        commandSender.sendMessage(Ui.mm("<gray> /admin welt <dark_gray>– Zeit, Wetter, Spielregeln"));
        commandSender.sendMessage(Ui.mm("<gray> /admin werkzeuge <dark_gray>– Vanish, Flug, Godmode"));
        commandSender.sendMessage(Ui.mm("<gray> /admin gefaengnis <dark_gray>– Gefängnis und Gefangene"));
        commandSender.sendMessage(Ui.mm("<gray> /admin gefaengnis festlegen <dark_gray>– Ort neu setzen"));
        commandSender.sendMessage(Ui.mm("<gray> /admin zugang <dark_gray>– PIN-Freischaltungen verwalten"));
        commandSender.sendMessage(Ui.mm("<gray> /admin reload <dark_gray>– Konfiguration neu laden"));
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (!this.plugin.hasAccess(commandSender)) {
            return arrayList;
        }
        if (stringArray.length == 1) {
            arrayList.add("spieler");
            arrayList.add("bauwerke");
            arrayList.add("verlauf");
            arrayList.add("server");
            arrayList.add("welt");
            arrayList.add("werkzeuge");
            arrayList.add("gefaengnis");
            arrayList.add("zugang");
            arrayList.add("reload");
            arrayList.add("save");
        } else if (stringArray.length == 2 && stringArray[0].equalsIgnoreCase("zugang")) {
            arrayList.add("reset");
        } else if (stringArray.length == 3 && stringArray[0].equalsIgnoreCase("zugang")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                arrayList.add(player.getName());
            }
        } else if (stringArray.length == 2 && stringArray[0].equalsIgnoreCase("tp")) {
            for (World world : Bukkit.getWorlds()) {
                arrayList.add(world.getName());
            }
        } else if (stringArray.length == 2 && (stringArray[0].equalsIgnoreCase("gefaengnis") || stringArray[0].equalsIgnoreCase("jail"))) {
            arrayList.add("festlegen");
            arrayList.add("abbrechen");
        }
        String string3 = stringArray[stringArray.length - 1].toLowerCase(Locale.ROOT);
        arrayList.removeIf(string2 -> !string2.toLowerCase(Locale.ROOT).startsWith(string3));
        return arrayList;
    }
}

