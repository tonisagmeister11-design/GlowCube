/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.DeathNote;
import de.adminfield.Ui;
import de.adminfield.menu.DeathTargetMenu;
import de.adminfield.menu.Menu;
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

public final class DeathNoteMenu
extends Menu {
    private static final int FIRST_SLOT = 18;

    public DeathNoteMenu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        super(adminFieldPlugin, menu);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <dark_red><bold>Death Note</bold></dark_red> <dark_gray>· " + this.plugin.deathNote().count() + " offen");
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
        if (!this.plugin.access().isOwner(this.viewer.getUniqueId())) {
            this.viewer.closeInventory();
            return;
        }
        ArrayList<DeathNote.Entry> arrayList = new ArrayList<DeathNote.Entry>(this.plugin.deathNote().entries());
        this.backButton(0);
        this.set(2, Ui.icon(Material.WRITABLE_BOOK, "<dark_red><bold>Neuer Eintrag</bold>", List.of("<gray>Einen Spieler eintragen und", "<gray>sein Schicksal auswählen.", "", "<yellow>➤ Klicken")), inventoryClickEvent -> new DeathTargetMenu(this.plugin, this).open(this.viewer));
        this.set(4, Ui.glowing(Material.BOOK, "<dark_red><bold>Death Note</bold>", List.of("<gray>Offene Einträge: <white>" + arrayList.size(), "", "<gray>Ein eingetragener Name wartet, bis das", "<gray>Ziel wirklich in der passenden Lage ist.", "<gray>Dann geschieht dort etwas, das genauso", "<gray>im normalen Spiel hätte passieren können.", "", "<dark_gray>Niemand außer dir erfährt davon.")));
        List<String> list = this.plugin.deathNote().history();
        ArrayList<String> arrayList2 = new ArrayList<String>();
        if (list.isEmpty()) {
            arrayList2.add("<gray>Noch nichts erledigt.");
        } else {
            arrayList2.add("<gray>Die letzten Ausführungen:");
            arrayList2.add("");
            arrayList2.addAll(list);
        }
        this.set(6, Ui.icon(Material.PAPER, "<gray><bold>Verlauf</bold>", arrayList2));
        this.closeButton(8);
        this.divider(1);
        if (arrayList.isEmpty()) {
            this.set(31, Ui.icon(Material.STRUCTURE_VOID, "<gray>Kein Eintrag offen", List.of("<gray>Oben links einen neuen anlegen.")));
            this.fillEmpty();
            return;
        }
        for (int i = 0; i < arrayList.size() && 18 + i < this.rows() * 9; ++i) {
            DeathNote.Entry entry = (DeathNote.Entry)arrayList.get(i);
            this.set(18 + i, this.iconFor(entry), inventoryClickEvent -> {
                this.plugin.deathNote().erase(entry.target());
                this.plugin.send((CommandSender)this.viewer, "<gray>Eintrag für <white>" + entry.name() + "<gray> gestrichen.");
                this.redraw();
            });
        }
        this.fillEmpty();
    }

    private ItemStack iconFor(DeathNote.Entry entry) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer((UUID)entry.target());
        Player player = Bukkit.getPlayer((UUID)entry.target());
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add(player != null ? "<green>Online" : "<dark_gray>Offline – es passiert nichts");
        arrayList.add("");
        arrayList.add("<gray>Schicksal: <white>" + entry.fate().label());
        arrayList.add("<dark_gray>" + entry.fate().description());
        arrayList.add("<gray>Wartet auf: <white>" + entry.fate().condition());
        arrayList.add("");
        arrayList.add("<gray>Eingetragen: <white>" + Ui.ago(System.currentTimeMillis() - entry.writtenAt()));
        arrayList.add("<gray>Gelegenheiten bisher: <white>" + entry.attempts());
        if (player != null) {
            boolean bl = this.plugin.deathNote().fits(player, entry.fate());
            arrayList.add("");
            arrayList.add(bl ? "<green>▪ Lage passt gerade" : "<dark_gray>▪ Lage passt noch nicht");
        }
        arrayList.add("");
        arrayList.add("<yellow>➤ Klicken zum Streichen");
        return Ui.head(offlinePlayer, "<dark_red><bold>" + entry.name() + "</bold>", arrayList);
    }
}

