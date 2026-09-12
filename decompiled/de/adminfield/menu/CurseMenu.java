/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Curse;
import de.adminfield.Curses;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CurseMenu
extends Menu {
    private static final int[] SLOTS = new int[]{19, 20, 21, 22, 23, 24};
    private final Player target;

    public CurseMenu(AdminFieldPlugin adminFieldPlugin, Menu menu, Player player) {
        super(adminFieldPlugin, menu);
        this.target = player;
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <dark_purple><bold>Fluch</bold></dark_purple> <dark_gray>· " + this.target.getName());
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
        if (!this.target.isOnline()) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Spieler ist offline"));
            this.backButton(45);
            this.closeButton(53);
            this.fillEmpty();
            return;
        }
        Curses.Hex hex = this.plugin.curses().hex(this.target.getUniqueId());
        long l = this.plugin.getConfig().getLong("curses.duration-minutes", 0L);
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("<gray>Ziel: <white>" + this.target.getName());
        arrayList.add("");
        if (hex == null) {
            arrayList.add("<gray>Zurzeit unverflucht.");
        } else {
            arrayList.add("<dark_purple>Aktiv: <white>" + hex.curse().label());
            arrayList.add("<gray>Seit <white>" + Ui.ago(System.currentTimeMillis() - hex.castAt()));
            arrayList.add("<gray>Von <white>" + hex.castBy());
        }
        arrayList.add("");
        arrayList.add((String)(l > 0L ? "<dark_gray>Läuft nach " + l + " Minuten von selbst aus." : "<dark_gray>Bleibt, bis du ihn aufhebst."));
        arrayList.add("<dark_gray>Er bekommt keine Meldung – er merkt es einfach.");
        this.set(4, Ui.glowing(Material.ENDER_EYE, "<dark_purple><bold>Fluch auflegen</bold>", arrayList));
        for (int i = 0; i < Curse.values().length && i < SLOTS.length; ++i) {
            Curse curse = Curse.values()[i];
            boolean bl = hex != null && hex.curse() == curse;
            ArrayList<String> arrayList2 = new ArrayList<String>();
            arrayList2.add("<gray>" + curse.description());
            arrayList2.add("");
            arrayList2.add(bl ? "<green>▪ Liegt gerade auf ihm" : "<yellow>➤ Klicken zum Auflegen");
            this.set(SLOTS[i], bl ? Ui.glowing(curse.icon(), "<dark_purple><bold>" + curse.label() + "</bold>", arrayList2) : Ui.icon(curse.icon(), "<light_purple>" + curse.label(), arrayList2), inventoryClickEvent -> this.apply(curse));
        }
        this.set(31, Ui.icon(Material.BONE_MEAL, "<light_purple><bold>Zufälliger Fluch</bold>", List.of("<gray>Das Plugin sucht einen aus.", "", "<yellow>➤ Klicken")), inventoryClickEvent -> this.apply(null));
        if (hex != null) {
            this.set(40, Ui.icon(Material.MILK_BUCKET, "<green><bold>Fluch aufheben</bold>", List.of("<gray>Nimmt <white>" + hex.curse().label() + "<gray> wieder ab.", "", "<yellow>➤ Klicken")), inventoryClickEvent -> {
                this.plugin.curses().lift(this.target.getUniqueId());
                this.plugin.send((CommandSender)this.viewer, "<gray>Fluch von <white>" + this.target.getName() + "<gray> aufgehoben.");
                this.redraw();
            });
        }
        this.divider(5);
        this.backButton(45);
        this.closeButton(53);
        this.fillEmpty();
    }

    private void apply(Curse curse) {
        if (!this.target.isOnline()) {
            this.plugin.send((CommandSender)this.viewer, "<red>Der Spieler ist nicht mehr online.");
            return;
        }
        Curse curse2 = this.plugin.curses().cast(this.viewer, this.target, curse);
        this.plugin.send((CommandSender)this.viewer, "<dark_purple>" + this.target.getName() + "<gray> trägt jetzt <white>" + curse2.label() + "<gray>.");
        this.redraw();
    }
}

