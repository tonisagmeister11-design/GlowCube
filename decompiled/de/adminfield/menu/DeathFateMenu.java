/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.DeathFate;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class DeathFateMenu
extends Menu {
    private final Player target;

    public DeathFateMenu(AdminFieldPlugin adminFieldPlugin, Menu menu, Player player) {
        super(adminFieldPlugin, menu);
        this.target = player;
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <dark_red><bold>Schicksal</bold></dark_red> <dark_gray>· " + this.target.getName());
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
        this.set(4, Ui.head((OfflinePlayer)this.target, "<dark_red><bold>" + this.target.getName() + "</bold>", List.of("<gray>Wähle, was ihm zustoßen soll.", "", "<gray>Nichts passiert sofort: Das Plugin wartet,", "<gray>bis er wirklich in der passenden Lage ist.", "", "<green>Grün <gray>= die Lage passt gerade", "<dark_gray>Grau = es muss noch gewartet werden")));
        List<DeathFate> list = DeathFate.all();
        int n = 9;
        for (DeathFate deathFate : list) {
            if (n >= 45) break;
            this.set(n, this.iconFor(deathFate), inventoryClickEvent -> this.choose(deathFate));
            ++n;
        }
        this.divider(5);
        this.backButton(45);
        this.set(49, Ui.glowing(Material.ENDER_EYE, "<light_purple><bold>Zufall</bold>", List.of("<gray>Das Plugin sucht selbst aus,", "<gray>was am besten zu ihm passt.", "", "<yellow>➤ Klicken")), inventoryClickEvent -> {
            ArrayList<DeathFate> arrayList = new ArrayList<DeathFate>();
            for (DeathFate deathFate : DeathFate.all()) {
                if (!this.plugin.deathNote().fits(this.target, deathFate)) continue;
                arrayList.add(deathFate);
            }
            List<DeathFate> list = arrayList.isEmpty() ? DeathFate.all() : arrayList;
            this.choose((DeathFate)((Object)((Object)list.get(ThreadLocalRandom.current().nextInt(list.size())))));
        });
        this.closeButton(53);
        this.fillEmpty();
    }

    private void choose(DeathFate deathFate) {
        this.plugin.deathNote().write(this.viewer, this.target, deathFate);
        this.viewer.closeInventory();
        this.viewer.playSound(this.viewer.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.7f);
        this.plugin.send((CommandSender)this.viewer, "<dark_red>Eingetragen: <white>" + this.target.getName() + "<dark_red> · " + deathFate.label());
        this.plugin.send((CommandSender)this.viewer, "<dark_gray>Es geschieht, sobald er in die Lage kommt: " + deathFate.condition().toLowerCase(Locale.ROOT) + ".");
    }

    private ItemStack iconFor(DeathFate deathFate) {
        boolean bl = this.plugin.deathNote().fits(this.target, deathFate);
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("<gray>" + deathFate.description());
        arrayList.add("");
        arrayList.add("<gray>Bedingung: <white>" + deathFate.condition());
        arrayList.add(bl ? "<green>▪ Trifft gerade zu" : "<dark_gray>▪ Trifft gerade nicht zu");
        arrayList.add("");
        arrayList.add("<yellow>➤ Klicken zum Eintragen");
        String string = (bl ? "<green>" : "<gray>") + "<bold>" + deathFate.label() + "</bold>";
        return bl ? Ui.glowing(deathFate.icon(), string, arrayList) : Ui.icon(deathFate.icon(), string, arrayList);
    }
}

