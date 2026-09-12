/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

public final class LogMenu
extends Menu {
    private static final int PER_PAGE = 45;

    public LogMenu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        super(adminFieldPlugin, menu);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <yellow><bold>Verlauf</bold></yellow> <dark_gray>· letzte Ereignisse");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void draw() {
        List<ActivityLog.Entry> list = this.plugin.log().recent(225);
        this.divider(5);
        this.backButton(45);
        this.pager(list.size(), 45, 48, 50);
        this.set(49, Ui.icon(Material.WRITABLE_BOOK, "<yellow>Verlauf", List.of("<gray>Einträge: <white>" + this.plugin.log().size(), "", "<gray>Ereignisse mit Ort lassen sich", "<gray>per Klick anspringen.", "", "<gray>" + ActivityLog.Level.INFO.marker() + " <gray>Information", "<gray>" + ActivityLog.Level.WARN.marker() + " <gray>Auffällig", "<gray>" + ActivityLog.Level.ALERT.marker() + " <gray>Alarm")));
        this.set(52, Ui.icon(Material.LAVA_BUCKET, "<red>Verlauf leeren", List.of("<red>➤ Klicken - passiert sofort")), inventoryClickEvent -> {
            this.plugin.log().clear();
            this.redraw();
        });
        this.closeButton(53);
        if (list.isEmpty()) {
            this.set(22, Ui.icon(Material.STRUCTURE_VOID, "<gray>Noch nichts passiert", List.of("<gray>Joins, Tode, neue Bauwerke und", "<gray>Alarme landen automatisch hier.")));
            this.fillEmpty();
            return;
        }
        int n = this.page * 45;
        for (int i = 0; i < 45 && n + i < list.size(); ++i) {
            ActivityLog.Entry entry = list.get(n + i);
            this.set(i, this.iconFor(entry), inventoryClickEvent -> {
                if (!entry.hasLocation()) {
                    this.plugin.send((CommandSender)this.viewer, "<gray>Zu diesem Eintrag gibt es keinen Ort.");
                    return;
                }
                World world = Bukkit.getWorld((String)entry.world());
                if (world == null) {
                    this.plugin.send((CommandSender)this.viewer, "<red>Die Welt <white>" + entry.world() + "<red> ist nicht geladen.");
                    return;
                }
                this.viewer.closeInventory();
                this.plugin.teleport(this.viewer, new Location(world, (double)entry.x() + 0.5, (double)entry.y() + 1.0, (double)entry.z() + 0.5), entry.text());
            });
        }
        this.fillEmpty();
    }

    private ItemStack iconFor(ActivityLog.Entry entry) {
        Material material = switch (entry.level()) {
            default -> throw new MatchException(null, null);
            case ActivityLog.Level.INFO -> Material.PAPER;
            case ActivityLog.Level.WARN -> Material.ORANGE_DYE;
            case ActivityLog.Level.ALERT -> Material.REDSTONE;
        };
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("<gray>" + entry.text());
        arrayList.add("");
        arrayList.add("<dark_gray>Zeit: " + ActivityLog.clock(entry.time()) + "  ·  " + Ui.ago(System.currentTimeMillis() - entry.time()));
        if (entry.hasLocation()) {
            arrayList.add("<gray>Ort: <white>" + entry.coordinates() + " <dark_gray>(" + entry.world() + ")");
            arrayList.add("");
            arrayList.add("<yellow>➤ Klicken zum Teleportieren");
        }
        String string = entry.level().marker() + " " + entry.level().color() + LogMenu.shorten(entry.text(), 34);
        return Ui.icon(material, string, arrayList);
    }

    private static String shorten(String string, int n) {
        return string.length() <= n ? string : string.substring(0, n - 1) + "…";
    }
}

