/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Jail;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class JailMenu
extends Menu {
    private static final int FIRST_INMATE_SLOT = 18;

    public JailMenu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        super(adminFieldPlugin, menu);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <red><bold>Gefängnis</bold></red> <dark_gray>· " + this.plugin.jail().count() + " Gefangene");
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
        Object object;
        Jail jail = this.plugin.jail();
        boolean bl = jail.isSet();
        this.backButton(0);
        this.set(2, Ui.icon(Material.IRON_BARS, "<yellow>Ort festlegen", List.of("<gray>Schließt das Menü und du klickst", "<gray>im Spiel auf den Boden.", "<gray>Genau dort landen die Gefangenen.", "", bl ? "<dark_gray>Ersetzt den bisherigen Ort." : "<red>Noch kein Gefängnis festgelegt!", "<yellow>➤ Klicken")), inventoryClickEvent -> jail.beginSetup(this.viewer, null));
        ArrayList<String> arrayList = new ArrayList<String>();
        if (bl) {
            object = jail.location();
            arrayList.add("<gray>Welt: <white>" + object.getWorld().getName());
            arrayList.add("<gray>Position: <white>" + Ui.pos((Location)object));
            arrayList.add("<gray>Ausbruchsgrenze: <white>" + this.plugin.getConfig().getInt("jail.radius", 12) + " Blöcke");
            arrayList.add("");
            arrayList.add("<gray>Gefangene: <white>" + jail.count());
            arrayList.add("<gray>Essensausgabe ab Hunger <white>" + this.plugin.getConfig().getInt("jail.feed-below", 6));
        } else {
            arrayList.add("<red>Noch kein Ort festgelegt.");
            arrayList.add("");
            arrayList.add("<gray>Klicke links auf <white>Ort festlegen<gray>,");
            arrayList.add("<gray>dann im Spiel auf den Boden.");
        }
        this.set(4, bl ? Ui.glowing(Material.IRON_DOOR, "<red><bold>Gefängnis</bold>", arrayList) : Ui.icon(Material.IRON_DOOR, "<gray><bold>Gefängnis</bold>", arrayList));
        if (bl) {
            this.set(6, Ui.icon(Material.ENDER_PEARL, "<aqua>Hin teleportieren", List.of("<gray>Bringt dich zum Gefängnis.", "<dark_gray>Dein Rücksprungpunkt wird gemerkt.")), inventoryClickEvent -> {
                this.viewer.closeInventory();
                this.plugin.teleport(this.viewer, jail.location(), "das Gefängnis");
            });
        }
        this.closeButton(8);
        this.divider(1);
        object = new ArrayList<Jail.Inmate>(jail.inmates());
        object.sort((inmate, inmate2) -> Long.compare(inmate2.jailedAt(), inmate.jailedAt()));
        if (object.isEmpty()) {
            this.set(31, Ui.icon(Material.STRUCTURE_VOID, "<gray>Niemand sitzt ein", List.of("<gray>Spieler landen hier, sobald du sie", "<gray>über das Spielermenü einsperrst.")));
        }
        for (int i = 0; i < object.size() && 18 + i < this.rows() * 9; ++i) {
            Jail.Inmate inmate3 = (Jail.Inmate)object.get(i);
            this.set(18 + i, this.headOf(inmate3), inventoryClickEvent -> {
                this.plugin.jail().release(this.viewer.getName(), inmate3.uuid());
                this.plugin.send((CommandSender)this.viewer, "<green>" + inmate3.name() + " wurde freigelassen.");
                this.redraw();
            });
        }
        this.fillEmpty();
    }

    private ItemStack headOf(Jail.Inmate inmate) {
        Location location;
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer((UUID)inmate.uuid());
        Player player = Bukkit.getPlayer((UUID)inmate.uuid());
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add(player != null ? "<green>Online" : "<dark_gray>Offline");
        arrayList.add("");
        arrayList.add("<gray>Sitzt seit: <white>" + Ui.ago(System.currentTimeMillis() - inmate.jailedAt()));
        arrayList.add("<gray>Eingesperrt von: <white>" + inmate.jailedBy());
        if (player != null) {
            arrayList.add("<gray>Hunger: <white>" + player.getFoodLevel() + "<dark_gray>/<white>20");
        }
        if ((location = inmate.returnLocation()) != null && location.getWorld() != null) {
            arrayList.add("");
            arrayList.add("<gray>Kommt zurück nach:");
            arrayList.add("<white>" + Ui.pos(location) + " <dark_gray>(" + location.getWorld().getName() + ")");
        }
        arrayList.add("");
        arrayList.add("<green>➤ Klicken zum Freilassen");
        return Ui.head(offlinePlayer, "<red><bold>" + inmate.name() + "</bold>", arrayList);
    }
}

