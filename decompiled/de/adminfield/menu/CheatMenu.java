/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.CheatWatch;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import de.adminfield.menu.PlayerActionMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CheatMenu
extends Menu {
    private static final int FIRST_SLOT = 18;

    public CheatMenu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        super(adminFieldPlugin, menu);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <red><bold>Cheat-Überwachung</bold></red> <dark_gray>· " + this.plugin.cheats().count() + " beobachtet");
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
        ArrayList<CheatWatch.Report> arrayList = new ArrayList<CheatWatch.Report>(this.plugin.cheats().reports());
        arrayList.sort((report, report2) -> Integer.compare(report2.totalHits(), report.totalHits()));
        this.set(4, Ui.glowing(Material.SPYGLASS, "<red><bold>Cheat-Überwachung</bold>", List.of("<gray>Beobachtet: <white>" + arrayList.size() + " Spieler", "", "<gray>Geprüft wird auf <white>Fliegen<gray>, <white>Speed<gray>,", "<white>X-Ray<gray>, <white>Reach<gray> und <white>NoFall<gray>.", "", "<dark_gray>Das sind Hinweise, keine Beweise.", "<dark_gray>Lags, Eis und Boote können mitzählen –", "<dark_gray>am Ende schaust du selbst nach.")));
        this.backButton(45);
        this.closeButton(53);
        this.divider(1);
        if (arrayList.isEmpty()) {
            this.set(31, Ui.icon(Material.STRUCTURE_VOID, "<gray>Niemand wird beobachtet", List.of("<gray>Im Spielermenü einen Spieler anklicken", "<gray>und dort <white>Cheat beobachten<gray> einschalten.")));
            this.fillEmpty();
            return;
        }
        for (int i = 0; i < arrayList.size() && 18 + i < this.rows() * 9 - 9; ++i) {
            CheatWatch.Report report3 = (CheatWatch.Report)arrayList.get(i);
            this.set(18 + i, this.headOf(report3), inventoryClickEvent -> {
                Player player = Bukkit.getPlayer((UUID)report3.uuid());
                if (inventoryClickEvent.isRightClick()) {
                    this.plugin.cheats().stop(report3.uuid());
                    this.plugin.send((CommandSender)this.viewer, "<gray>" + report3.name() + " wird nicht mehr beobachtet.");
                    this.redraw();
                    return;
                }
                if (player == null) {
                    this.plugin.send((CommandSender)this.viewer, "<red>" + report3.name() + " ist offline.");
                    return;
                }
                new PlayerActionMenu(this.plugin, this, player).open(this.viewer);
            });
        }
        this.fillEmpty();
    }

    private ItemStack headOf(CheatWatch.Report report) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer((UUID)report.uuid());
        Player player = Bukkit.getPlayer((UUID)report.uuid());
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add(player != null ? "<green>Online" : "<dark_gray>Offline");
        arrayList.add("<gray>Beobachtet seit <white>" + Ui.ago(System.currentTimeMillis() - report.since()));
        arrayList.add("<gray>Gestartet von <white>" + report.watchedBy());
        arrayList.add("");
        int n = report.totalHits();
        if (n == 0) {
            arrayList.add("<green>Bisher nichts Auffälliges.");
        } else {
            arrayList.add("<red><bold>" + n + " Auffälligkeiten</bold>");
            for (CheatWatch.Kind kind : CheatWatch.Kind.values()) {
                int n2 = report.hits(kind);
                if (n2 == 0) continue;
                arrayList.add(kind.color() + kind.label() + "<dark_gray>: <white>" + n2 + "x <dark_gray>· " + this.plugin.cheats().confidence(report, kind));
                String string = report.detail(kind);
                if (string == null) continue;
                arrayList.add("<dark_gray>  " + string);
            }
        }
        arrayList.add("");
        arrayList.add("<gray>Abgebaut: <white>" + report.minedTotal() + " Blöcke");
        arrayList.add("<gray>Versteckte Adern: <white>" + report.minedValuable() + "  <dark_gray>·  <gray>sichtbar gefunden: <white>" + report.exposedFinds());
        arrayList.add("");
        arrayList.add("<yellow>➤ Linksklick: Spieler öffnen");
        arrayList.add("<dark_gray>Rechtsklick: Beobachtung beenden");
        String string = n > 0 ? "<red><bold>" + report.name() + "</bold></red> <dark_gray>· " + n + " Treffer" : "<white><bold>" + report.name() + "</bold></white> <dark_gray>· sauber";
        return Ui.head(offlinePlayer, string, arrayList);
    }
}

