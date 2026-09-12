/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.menu.BuildMenu;
import de.adminfield.menu.Menu;
import de.adminfield.menu.PlayerActionMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

public final class ServerMenu
extends Menu {
    public ServerMenu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        super(adminFieldPlugin, menu);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <green><bold>Serverstatus</bold></green>");
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void draw() {
        double[] dArray = Bukkit.getServer().getTPS();
        double d = Bukkit.getServer().getAverageTickTime();
        Runtime runtime = Runtime.getRuntime();
        long l = (runtime.totalMemory() - runtime.freeMemory()) / 0x100000L;
        long l2 = runtime.maxMemory() / 0x100000L;
        double d2 = l2 <= 0L ? 0.0 : (double)l / (double)l2;
        int n = 0;
        int n2 = 0;
        int n3 = 0;
        for (World world : Bukkit.getWorlds()) {
            n += world.getLoadedChunks().length;
            for (Entity entity : world.getEntities()) {
                ++n2;
                if (!(entity instanceof Item)) continue;
                ++n3;
            }
        }
        this.set(4, Ui.glowing(Material.COMPARATOR, "<green><bold>Serverstatus</bold>", List.of("<gray>Version: <white>" + Bukkit.getMinecraftVersion(), "<gray>Welten: <white>" + Bukkit.getWorlds().size(), "<gray>Plugins aktiv: <white>" + Bukkit.getPluginManager().getPlugins().length)));
        this.set(10, Ui.icon(Material.CLOCK, "<white>Ticks", List.of("<gray>TPS 1 Min: " + Ui.gradeHigh(dArray[0], 19.0, 17.0) + String.format("%.2f", dArray[0]), "<gray>TPS 5 Min: " + Ui.gradeHigh(dArray[1], 19.0, 17.0) + String.format("%.2f", dArray[1]), "<gray>TPS 15 Min: " + Ui.gradeHigh(dArray[2], 19.0, 17.0) + String.format("%.2f", dArray[2]), "", "<gray>Tickzeit: " + Ui.gradeLow(d, 30.0, 45.0) + String.format("%.1f", d) + " ms", "<dark_gray>Ab 50 ms fällt der Server unter 20 TPS.")));
        this.set(11, Ui.icon(Material.REDSTONE, "<white>Arbeitsspeicher", List.of("<gray>Belegt: " + Ui.gradeLow(d2, 0.7, 0.85) + l + " MB", "<gray>Maximum: <white>" + l2 + " MB", "", "<gray>" + Ui.bar(l, Math.max(1L, l2), 20, d2 > 0.85 ? "red" : (d2 > 0.7 ? "gold" : "green"), "dark_gray"))));
        this.set(12, Ui.icon(Material.GRASS_BLOCK, "<white>Welt & Last", List.of("<gray>Geladene Chunks: <white>" + n, "<gray>Entities gesamt: <white>" + n2, "<gray>Davon Bodenitems: <white>" + n3, "", "<dark_gray>Viele Bodenitems bremsen den Server.")));
        this.set(13, Ui.icon(Material.PLAYER_HEAD, "<white>Spieler", List.of("<gray>Online: <white>" + Bukkit.getOnlinePlayers().size() + "<dark_gray>/<white>" + Bukkit.getMaxPlayers(), "<gray>Erkannte Bauwerke: <white>" + this.plugin.tracker().confirmed().size(), "<gray>Davon gerade aktiv: <white>" + BuildMenu.activeCount(this.plugin))));
        this.set(14, Ui.icon(Material.AMETHYST_SHARD, "<white>Laufzeit", List.of("<gray>Server: <white>" + Ui.duration(this.plugin.serverUptime()), "<gray>AdminField: <white>" + Ui.duration(this.plugin.pluginUptime()), "<gray>Verlaufseinträge: <white>" + this.plugin.log().size())));
        this.set(20, Ui.icon(Material.GOLDEN_APPLE, "<green>Alle heilen", List.of("<gray>Setzt bei allen Spielern Leben,", "<gray>Hunger und Effekte zurück.", "", "<yellow>➤ Klicken")), inventoryClickEvent -> {
            int n = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerActionMenu.fullyHeal(player);
                ++n;
            }
            this.plugin.send((CommandSender)this.viewer, "<green>" + n + " Spieler geheilt.");
            this.plugin.log().add(ActivityLog.Level.INFO, this.viewer.getName() + " heilte alle Spieler");
        });
        this.set(21, Ui.icon(Material.HOPPER, "<gold>Bodenitems aufräumen", List.of("<gray>Entfernt alle herumliegenden Items", "<gray>in allen Welten.", "", "<gray>Aktuell: <white>" + n3, "<red>➤ Klicken - passiert sofort")), inventoryClickEvent -> {
            int n = 0;
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (!(entity instanceof Item)) continue;
                    entity.remove();
                    ++n;
                }
            }
            this.plugin.send((CommandSender)this.viewer, "<gold>" + n + " Bodenitems entfernt.");
            this.plugin.log().add(ActivityLog.Level.INFO, this.viewer.getName() + " räumte " + n + " Bodenitems weg");
            this.redraw();
        });
        this.set(22, Ui.icon(Material.ZOMBIE_HEAD, "<red>Monster entfernen", List.of("<gray>Entfernt alle feindlichen Kreaturen", "<gray>in allen Welten. Tiere bleiben.", "", "<red>➤ Klicken - passiert sofort")), inventoryClickEvent -> {
            int n = 0;
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (!(entity instanceof Monster)) continue;
                    entity.remove();
                    ++n;
                }
            }
            this.plugin.send((CommandSender)this.viewer, "<red>" + n + " Monster entfernt.");
            this.plugin.log().add(ActivityLog.Level.INFO, this.viewer.getName() + " entfernte " + n + " Monster");
            this.redraw();
        });
        this.set(23, Ui.icon(Material.CHEST, "<aqua>Welten speichern", List.of("<gray>Schreibt alle Welten auf die Platte.", "<dark_gray>Kann kurz ruckeln.", "", "<yellow>➤ Klicken")), inventoryClickEvent -> {
            for (World world : Bukkit.getWorlds()) {
                world.save();
            }
            this.plugin.send((CommandSender)this.viewer, "<aqua>Alle Welten gespeichert.");
            this.plugin.log().add(ActivityLog.Level.INFO, this.viewer.getName() + " speicherte alle Welten");
        });
        this.set(24, Ui.icon(Material.ICE, "<aqua>Alle auftauen", List.of("<gray>Hebt alle Einfrierungen auf.", "", "<gray>Eingefroren: <white>" + this.plugin.state().frozenPlayers().size())), inventoryClickEvent -> {
            ArrayList<UUID> arrayList = new ArrayList<UUID>(this.plugin.state().frozenPlayers());
            for (UUID uUID : arrayList) {
                this.plugin.state().setFrozen(uUID, false);
                Player player = Bukkit.getPlayer((UUID)uUID);
                if (player == null) continue;
                this.plugin.send((CommandSender)player, "<green>Du kannst dich wieder bewegen.");
            }
            this.plugin.send((CommandSender)this.viewer, "<green>" + arrayList.size() + " Spieler aufgetaut.");
            this.redraw();
        });
        this.divider(3);
        this.backButton(36);
        this.closeButton(44);
        this.fillEmpty();
    }
}

