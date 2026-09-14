/*
 * Decompiled with CFR 0.152.
 */
package com.glowcube.utils;

import com.glowcube.utils.Home;
import com.glowcube.utils.HomeManager;
import com.glowcube.utils.Msg;
import com.glowcube.utils.SidebarManager;
import com.glowcube.utils.TeleportManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public class HomeCommands
implements TabExecutor {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_\\-]+");
    private final HomeManager homes;
    private final TeleportManager teleports;
    private final SidebarManager sidebar;

    public HomeCommands(HomeManager homeManager, TeleportManager teleportManager, SidebarManager sidebarManager) {
        this.homes = homeManager;
        this.teleports = teleportManager;
        this.sidebar = sidebarManager;
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("Nur Spieler koennen diesen Befehl nutzen.");
            return true;
        }
        Player player = (Player)commandSender;
        if (!player.hasPermission("glowcube.home")) {
            Msg.error((CommandSender)player, "Dafuer hast du keine Berechtigung.", new TagResolver[0]);
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sethome": {
                this.setHome(player, stringArray.length > 0 ? stringArray[0] : "default");
                break;
            }
            case "home": {
                this.goHome(player, stringArray.length > 0 ? stringArray[0] : null);
                break;
            }
            case "delhome": {
                if (stringArray.length == 0) {
                    Msg.info((CommandSender)player, "Nutzung: <yellow>/delhome <name_lit>", new TagResolver[0]);
                    return true;
                }
                this.delHome(player, stringArray[0]);
                break;
            }
            case "movehome": {
                if (stringArray.length == 0) {
                    Msg.info((CommandSender)player, "Nutzung: <yellow>/movehome <name_lit>", new TagResolver[0]);
                    return true;
                }
                this.moveHome(player, stringArray[0]);
                break;
            }
            case "homes": {
                this.listHomes(player);
                break;
            }
            default: {
                return false;
            }
        }
        return true;
    }

    private void setHome(Player player, String string) {
        if (!this.validateName(player, string)) {
            return;
        }
        if (!this.checkDimension(player)) {
            return;
        }
        if (this.homes.hasHome(player.getUniqueId(), string)) {
            Msg.error((CommandSender)player, "Du hast schon ein Home namens <yellow><home></yellow>. Nutze <yellow>/movehome <home></yellow>, um es hierher zu verschieben.", Msg.ph("home", string));
            return;
        }
        int n = this.homes.getMaxHomes();
        int n2 = this.homes.getHomes(player.getUniqueId()).size();
        if (n > 0 && n2 >= n && !player.hasPermission("glowcube.home.bypass-max")) {
            Msg.error((CommandSender)player, "Du hast das Home-Limit von <yellow><max></yellow> erreicht. Loesche eins mit <yellow>/delhome <name_lit></yellow>.", Msg.ph("max", String.valueOf(n)));
            return;
        }
        this.homes.setHome(player.getUniqueId(), Home.of(string, player.getLocation()));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        Msg.success((CommandSender)player, "Home <yellow><home></yellow> wurde gesetzt. Mit <yellow>/home <home></yellow> kommst du zurueck.", Msg.ph("home", string));
        this.sidebar.update(player);
    }

    private void goHome(Player player, String string) {
        Location location;
        Home home;
        List<Home> list = this.homes.getHomes(player.getUniqueId());
        if (list.isEmpty()) {
            Msg.error((CommandSender)player, "Du hast noch kein Home. Setze eins mit <yellow>/sethome</yellow>.", new TagResolver[0]);
            return;
        }
        if (string == null) {
            home = this.homes.getHome(player.getUniqueId(), "default");
            if (home == null && list.size() == 1) {
                home = list.get(0);
            }
            if (home == null) {
                Msg.error((CommandSender)player, "Du hast mehrere Homes. Nutze <yellow>/home <name_lit></yellow> oder <yellow>/homes</yellow>.", new TagResolver[0]);
                return;
            }
        } else {
            home = this.homes.getHome(player.getUniqueId(), string);
            if (home == null) {
                Msg.error((CommandSender)player, "Das Home <yellow><home></yellow> gibt es nicht. Deine Homes: <yellow>/homes", Msg.ph("home", string));
                return;
            }
        }
        if ((location = home.toLocation()) != null && this.homes.isBlockedWorld(location.getWorld()) && !player.hasPermission("glowcube.home.bypass-dimension")) {
            Msg.error((CommandSender)player, "Du kannst nicht zu diesem Home teleportieren, weil es in einer gesperrten Dimension liegt.", new TagResolver[0]);
            return;
        }
        this.teleports.teleport(player, home);
    }

    private void delHome(Player player, String string) {
        if (!this.homes.deleteHome(player.getUniqueId(), string)) {
            Msg.error((CommandSender)player, "Das Home <yellow><home></yellow> gibt es nicht.", Msg.ph("home", string));
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        Msg.success((CommandSender)player, "Home <yellow><home></yellow> wurde geloescht.", Msg.ph("home", string));
        this.sidebar.update(player);
    }

    private void moveHome(Player player, String string) {
        Home home = this.homes.getHome(player.getUniqueId(), string);
        if (home == null) {
            Msg.error((CommandSender)player, "Das Home <yellow><home></yellow> gibt es nicht.", Msg.ph("home", string));
            return;
        }
        if (!this.checkDimension(player)) {
            return;
        }
        this.homes.setHome(player.getUniqueId(), Home.of(home.name(), player.getLocation()));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        Msg.success((CommandSender)player, "Home <yellow><home></yellow> wurde hierher verschoben.", Msg.ph("home", home.name()));
    }

    private void listHomes(Player player) {
        List<Home> list = this.homes.getHomes(player.getUniqueId());
        if (list.isEmpty()) {
            Msg.error((CommandSender)player, "Du hast noch kein Home. Setze eins mit <yellow>/sethome</yellow>.", new TagResolver[0]);
            return;
        }
        int n = this.homes.getMaxHomes();
        Object object = n > 0 ? list.size() + "/" + n : String.valueOf(list.size());
        Msg.info((CommandSender)player, "Deine Homes <dark_gray>(<aqua><count></aqua>)<gray>: <dark_gray>[Klicken zum Teleportieren]", Msg.ph("count", (String)object));
        for (Home home : list) {
            String string = home.worldName() + " " + (int)home.x() + ", " + (int)home.y() + ", " + (int)home.z();
            Component component = Msg.mm("  <gray>\u00bb <yellow><home> <dark_gray>(<coords>)", Msg.ph("home", home.name()), Msg.ph("coords", string)).clickEvent(ClickEvent.runCommand((String)("/home " + home.name()))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)Msg.mm("<green>Klicken: <yellow>/home <home>", Msg.ph("home", home.name()))));
            player.sendMessage(component);
        }
    }

    private boolean validateName(Player player, String string) {
        if (string.isBlank() || !VALID_NAME.matcher(string).matches()) {
            Msg.error((CommandSender)player, "Ungueltiger Name. Erlaubt sind Buchstaben, Zahlen, <yellow>_</yellow> und <yellow>-</yellow>.", new TagResolver[0]);
            return false;
        }
        int n = this.homes.getMaxNameLength();
        if (string.length() > n) {
            Msg.error((CommandSender)player, "Der Name ist zu lang (maximal <yellow><max></yellow> Zeichen).", Msg.ph("max", String.valueOf(n)));
            return false;
        }
        return true;
    }

    private boolean checkDimension(Player player) {
        if (this.homes.isBlockedWorld(player.getWorld()) && !player.hasPermission("glowcube.home.bypass-dimension")) {
            String string = switch (player.getWorld().getEnvironment()) {
                case World.Environment.NETHER -> "im Nether";
                case World.Environment.THE_END -> "im End";
                default -> "in dieser Welt";
            };
            Msg.error((CommandSender)player, "Du kannst <yellow><where></yellow> kein Home setzen. Nur in der Oberwelt!", Msg.ph("where", string));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        return true;
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        Player player;
        block6: {
            block5: {
                if (!(commandSender instanceof Player)) break block5;
                player = (Player)commandSender;
                if (stringArray.length == 1) break block6;
            }
            return List.of();
        }
        String string2 = command.getName().toLowerCase(Locale.ROOT);
        if (!(string2.equals("home") || string2.equals("delhome") || string2.equals("movehome"))) {
            return List.of();
        }
        String string3 = stringArray[0].toLowerCase(Locale.ROOT);
        ArrayList<String> arrayList = new ArrayList<String>();
        for (Home home : this.homes.getHomes(player.getUniqueId())) {
            if (!home.name().toLowerCase(Locale.ROOT).startsWith(string3)) continue;
            arrayList.add(home.name());
        }
        return arrayList;
    }
}

