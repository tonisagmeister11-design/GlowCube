/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class OwnerStuffMenu
extends Menu {
    public OwnerStuffMenu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        super(adminFieldPlugin, menu);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gradient:#ffd166:#ff8a00><bold>Owner-Ausrüstung</bold></gradient>");
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
        this.set(4, Ui.glowing(Material.GOLDEN_HELMET, "<gradient:#ffd166:#ff8a00><bold>Owner-Ausrüstung</bold></gradient>", List.of("<gray>Werkzeuge, die es sonst nirgends gibt.", "<gray>Ein Klick packt das Stück ein.", "", "<dark_gray>Die Stäbe wirken per Rechtsklick", "<dark_gray>und funktionieren nur in deiner Hand.")));
        this.offer(10, "owner_sword");
        this.offer(11, "owner_axe");
        this.offer(12, "owner_pickaxe");
        this.offer(13, "owner_bow");
        this.offer(14, "owner_trident");
        this.set(16, Ui.icon(Material.ENCHANTED_GOLDEN_APPLE, "<gold><bold>Vorratspaket</bold>", List.of("<gray>64 verzauberte Goldäpfel", "<gray>16 Totems der Unsterblichkeit", "<gray>64 Feuerwerksraketen", "<gray>16 Enderperlen", "", "<yellow>➤ Klicken zum Holen")), inventoryClickEvent -> this.plugin.powers().supplies(this.viewer));
        this.offer(19, "owner_helmet");
        this.offer(20, "owner_chestplate");
        this.offer(21, "owner_leggings");
        this.offer(22, "owner_boots");
        this.offer(23, "owner_elytra");
        this.set(25, Ui.glowing(Material.NETHERITE_CHESTPLATE, "<gold><bold>Komplettes Set</bold>", List.of("<gray>Helm, Brust, Hose, Stiefel,", "<gray>Flügel und die Owner-Klinge", "<gray>auf einmal.", "", "<yellow>➤ Klicken zum Holen")), inventoryClickEvent -> {
            this.plugin.giveItems(this.viewer, this.plugin.ownerItems().get("owner_helmet"), this.plugin.ownerItems().get("owner_chestplate"), this.plugin.ownerItems().get("owner_leggings"), this.plugin.ownerItems().get("owner_boots"), this.plugin.ownerItems().get("owner_elytra"), this.plugin.ownerItems().get("owner_sword"));
            this.plugin.send((CommandSender)this.viewer, "<gray>Komplettes Set eingepackt.");
        });
        this.offer(27, "knockback_stick_255");
        this.offer(35, "knockback_stick_25");
        this.offer(28, "wand_tnt");
        this.offer(29, "wand_lightning");
        this.offer(30, "wand_heal");
        this.offer(31, "wand_freeze");
        this.offer(32, "wand_launch");
        this.offer(33, "wand_time");
        this.offer(34, "wand_clean");
        int n = this.plugin.getConfig().getInt("ownerstuff.tnt-amount", 150);
        this.set(38, Ui.icon(Material.TNT, "<red><bold>" + n + " TNT abwerfen</bold>", List.of("<gray>Sofort, ohne Umweg über einen Stab.", "<gray>Ziel ist der Block, den du gerade ansiehst.", "", "<dark_gray>Menü schließt sich, damit du zielen kannst.", "<red>➤ Klicken - passiert sofort")), inventoryClickEvent -> {
            this.viewer.closeInventory();
            this.plugin.powers().tntRain(this.viewer, this.plugin.powers().aim(this.viewer));
        });
        this.set(40, Ui.icon(Material.BLAZE_POWDER, "<gold><bold>Alle Stäbe holen</bold>", List.of("<gray>Packt dir alle sieben Stäbe ein.", "", "<yellow>➤ Klicken zum Holen")), inventoryClickEvent -> {
            this.plugin.giveItems(this.viewer, this.plugin.ownerItems().get("wand_tnt"), this.plugin.ownerItems().get("wand_lightning"), this.plugin.ownerItems().get("wand_heal"), this.plugin.ownerItems().get("wand_freeze"), this.plugin.ownerItems().get("wand_launch"), this.plugin.ownerItems().get("wand_time"), this.plugin.ownerItems().get("wand_clean"));
            this.plugin.send((CommandSender)this.viewer, "<gray>Alle Stäbe eingepackt.");
        });
        this.set(42, Ui.icon(Material.CLOCK, "<aqua><bold>Tag / Nacht</bold>", List.of("<gray>Schaltet die Zeit in deiner Welt um.")), inventoryClickEvent -> {
            this.plugin.powers().toggleTime(this.viewer);
            this.redraw();
        });
        this.offer(44, "ban_chest");
        this.divider(5);
        this.backButton(45);
        this.closeButton(53);
        this.fillEmpty();
    }

    private void offer(int n, String string) {
        ItemStack itemStack = this.plugin.ownerItems().get(string);
        if (itemStack == null) {
            return;
        }
        ItemStack itemStack2 = itemStack.clone();
        ItemMeta itemMeta = itemStack2.getItemMeta();
        ArrayList<Component> arrayList = itemMeta.lore() == null ? new ArrayList<Component>() : new ArrayList(itemMeta.lore());
        arrayList.add(Ui.item(""));
        arrayList.add(Ui.item("<yellow>➤ Klicken zum Holen"));
        itemMeta.lore(arrayList);
        itemStack2.setItemMeta(itemMeta);
        this.set(n, itemStack2, inventoryClickEvent -> {
            this.plugin.giveItems(this.viewer, this.plugin.ownerItems().get(string));
            this.plugin.send((CommandSender)this.viewer, "<gray>Eingepackt.");
        });
    }
}

