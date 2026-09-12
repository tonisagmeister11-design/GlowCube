/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Assassin;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AssassinMenu
extends Menu {
    private static final int[] NAME_SLOTS = new int[]{19, 20, 21, 22, 23, 24, 25, 28};
    private final Player target;
    private boolean strong;
    private boolean ownerSkin;

    public AssassinMenu(AdminFieldPlugin adminFieldPlugin, Menu menu, Player player) {
        super(adminFieldPlugin, menu);
        this.target = player;
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <red><bold>Attentäter</bold></red> <dark_gray>· " + this.target.getName());
    }

    @Override
    protected int rows() {
        return 6;
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
        this.set(4, Ui.glowing(Material.NETHERITE_SWORD, "<red><bold>Attentäter ansetzen</bold>", List.of("<gray>Ziel: <white>" + this.target.getName(), "", "<gray>Erscheint etwa <white>" + this.plugin.getConfig().getInt("assassin.spawn-distance", 10) + " Blöcke<gray> entfernt hinter ihm,", "<gray>in voller Rüstung, und verfolgt ihn", "<gray>ohne Pause bis zum Ende.", "", "<gray>Im Chat steht danach ganz normal:", "<white>„" + this.target.getName() + " wurde von <Name> getötet\"", "", "<dark_gray>Danach löst er sich in Rauch auf.")));
        this.set(15, Ui.toggle(this.ownerSkin, this.ownerSkin ? "<gold>Aussehen: dein Skin" : "<white>Aussehen: Steve", List.of("<gray>Der Attentäter sieht aus wie ein", "<gray>gerüsteter Spieler – mit Steve-Skin", "<gray>oder mit deinem eigenen.")), inventoryClickEvent -> {
            this.ownerSkin = !this.ownerSkin;
            this.redraw();
        });
        this.set(13, Ui.toggle(this.strong, this.strong ? "<dark_purple>Stärke: Netherite" : "<aqua>Stärke: Diamant", List.of("<gray>Diamant: <white>" + (int)this.plugin.getConfig().getDouble("assassin.health", 60.0) + " Leben, " + (int)this.plugin.getConfig().getDouble("assassin.damage", 9.0) + " Schaden", "<gray>Netherite: <white>" + (int)this.plugin.getConfig().getDouble("assassin.health-strong", 120.0) + " Leben, " + (int)this.plugin.getConfig().getDouble("assassin.damage-strong", 14.0) + " Schaden")), inventoryClickEvent -> {
            this.strong = !this.strong;
            this.redraw();
        });
        for (int i = 0; i < Assassin.NAMES.size() && i < NAME_SLOTS.length; ++i) {
            String string = Assassin.NAMES.get(i);
            this.set(NAME_SLOTS[i], Ui.icon(Material.PLAYER_HEAD, "<white><bold>" + string + "</bold>", List.of("<gray>Tritt unter diesem Namen auf.", "", "<red>➤ Klicken zum Losschicken")), inventoryClickEvent -> this.launch(string));
        }
        this.set(31, Ui.glowing(Material.GOLDEN_HELMET, "<gold><bold>In deinem Namen</bold>", List.of("<gray>Der Attentäter trägt deinen Namen –", "<gray>die Todesmeldung nennt dich.", "", "<red>➤ Klicken zum Losschicken")), inventoryClickEvent -> this.launch(this.viewer.getName()));
        this.set(33, Ui.icon(Material.NAME_TAG, "<aqua><bold>Eigener Name</bold>", List.of("<gray>Du tippst den Kampfnamen in den Chat.", "<gray>2 bis 24 Zeichen, frei erfunden.", "", "<dark_gray>Namen echter Spieler dieses Servers", "<dark_gray>werden abgelehnt.", "", "<red>➤ Klicken, dann Name in den Chat")), inventoryClickEvent -> {
            boolean bl = this.strong;
            boolean bl2 = this.ownerSkin;
            this.plugin.state().prompt(this.viewer, "Wie soll der Attentäter heißen?", string -> {
                if (!this.target.isOnline()) {
                    this.plugin.send((CommandSender)this.viewer, "<red>Der Spieler ist nicht mehr online.");
                    return;
                }
                String string2 = this.plugin.assassin().validateName(this.viewer, (String)string);
                if (string2 != null) {
                    this.plugin.send((CommandSender)this.viewer, "<red>" + string2);
                    return;
                }
                this.plugin.assassin().send(this.viewer, this.target, string.trim(), bl, bl2);
            });
        });
        if (this.plugin.assassin().isHunted(this.target.getUniqueId())) {
            this.set(40, Ui.icon(Material.BARRIER, "<yellow>Attentäter zurückrufen", List.of("<gray>Unterwegs: <white>" + this.plugin.assassin().hunterName(this.target.getUniqueId()), "", "<yellow>➤ Klicken")), inventoryClickEvent -> {
                this.plugin.assassin().dismiss(this.target.getUniqueId(), true);
                this.plugin.send((CommandSender)this.viewer, "<gray>Zurückgerufen.");
                this.redraw();
            });
        }
        this.divider(5);
        this.backButton(45);
        this.closeButton(53);
        this.fillEmpty();
    }

    private void launch(String string) {
        this.viewer.closeInventory();
        this.plugin.assassin().send(this.viewer, this.target, string, this.strong, this.ownerSkin);
    }
}

