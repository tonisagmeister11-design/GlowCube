/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.LuckyBlocks;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

public final class LuckyBlockMenu
extends Menu {
    private static final int[] SLOTS = new int[]{20, 22, 24};

    public LuckyBlockMenu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        super(adminFieldPlugin, menu);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gradient:#ffe259:#4f8bff><bold>Lucky Blocks</bold></gradient>");
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void draw() {
        if (!this.plugin.access().isOwner(this.viewer.getUniqueId())) {
            this.viewer.closeInventory();
            return;
        }
        LuckyBlocks luckyBlocks = this.plugin.luckyBlocks();
        boolean bl = luckyBlocks.pluginPresent();
        boolean bl2 = luckyBlocks.enabled();
        ArrayList<String> arrayList = new ArrayList<String>();
        if (!bl2) {
            arrayList.add("<red>In der Konfiguration abgeschaltet.");
            arrayList.add("<dark_gray>luckyblocks.enabled: false");
        } else if (!bl) {
            arrayList.add("<red>GlowCubeLuckyBlocks läuft nicht.");
            arrayList.add("");
            arrayList.add("<gray>Lege das Lucky-Block-Plugin in den");
            arrayList.add("<gray>Ordner <white>plugins/<gray> und starte neu.");
        } else {
            arrayList.add("<gray>Hol dir Lucky Blocks direkt ins Inventar.");
            arrayList.add("");
            arrayList.add("<gray>Es sind exakt dieselben Blöcke wie aus");
            arrayList.add("<gray>dem Auswahlmenü – sie stapeln sich sogar");
            arrayList.add("<gray>mit denen, die Spieler dort bekommen.");
            arrayList.add("");
            arrayList.add("<dark_gray>Nur mit Adminfeld-Zugang erreichbar.");
        }
        this.set(4, bl && bl2 ? Ui.glowing(Material.NOTE_BLOCK, "<gradient:#ffe259:#4f8bff><bold>Lucky Blocks</bold></gradient>", arrayList) : Ui.icon(Material.NOTE_BLOCK, "<gray><bold>Lucky Blocks</bold>", arrayList));
        LuckyBlocks.Tier[] tierArray = LuckyBlocks.Tier.values();
        for (int i = 0; i < tierArray.length && i < SLOTS.length; ++i) {
            LuckyBlocks.Tier tier = tierArray[i];
            int n = SLOTS[i];
            if (!bl || !bl2) {
                this.set(n, Ui.icon(Material.GRAY_STAINED_GLASS_PANE, tier.color() + tier.id().toUpperCase(Locale.ROOT), List.of("<dark_gray>Gerade nicht verfügbar")));
                continue;
            }
            if (!luckyBlocks.allowed(tier)) {
                this.set(n, this.locked(tier));
                continue;
            }
            this.set(n, this.available(tier), inventoryClickEvent -> {
                int n = luckyBlocks.amount(inventoryClickEvent.isRightClick());
                ItemStack itemStack = luckyBlocks.create(tier, n);
                if (itemStack == null) {
                    this.plugin.send((CommandSender)this.viewer, "<red>Das Lucky-Block-Plugin läuft gerade nicht.");
                    return;
                }
                for (ItemStack itemStack2 : this.viewer.getInventory().addItem(new ItemStack[]{itemStack}).values()) {
                    this.viewer.getWorld().dropItemNaturally(this.viewer.getLocation(), itemStack2);
                }
                this.viewer.playSound(this.viewer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
                this.plugin.send((CommandSender)this.viewer, "<gray>" + n + "x " + tier.displayName() + " <gray>erhalten.");
                this.plugin.log().add(ActivityLog.Level.INFO, this.viewer.getName() + " holte sich " + n + "x Lucky Block (" + tier.id() + ")", this.viewer.getLocation(), this.viewer.getUniqueId());
            });
        }
        this.divider(3);
        this.backButton(36);
        this.closeButton(44);
        this.fillEmpty();
    }

    private ItemStack available(LuckyBlocks.Tier tier) {
        LuckyBlocks luckyBlocks = this.plugin.luckyBlocks();
        ItemStack itemStack = luckyBlocks.create(tier, 1);
        if (itemStack == null) {
            return Ui.icon(Material.BARRIER, "<red>Nicht verfügbar");
        }
        ArrayList<Object> arrayList = new ArrayList<Object>();
        arrayList.add("<gray>Enthält unter anderem:");
        for (String string : tier.contents()) {
            arrayList.add("<white>" + string);
        }
        arrayList.add("");
        arrayList.add("<yellow>➤ Linksklick: <white>" + luckyBlocks.amount(false) + " Stück");
        arrayList.add("<yellow>➤ Rechtsklick: <white>" + luckyBlocks.amount(true) + " Stück");
        ItemStack itemStack2 = itemStack.clone();
        itemStack2.editMeta(itemMeta -> itemMeta.lore(Ui.lore(arrayList)));
        return itemStack2;
    }

    private ItemStack locked(LuckyBlocks.Tier tier) {
        return Ui.icon(Material.BARRIER, "<dark_gray><bold>" + LuckyBlockMenu.tierName(tier) + " <red>gesperrt", List.of("<gray>Diese Stufe gibt es nicht über das Adminfeld.", "", "<gray>Der <light_purple>Subscribe Lucky Block<gray> bleibt", "<gray>dem normalen Weg vorbehalten –", "<gray>Auswahlmenü oder Fund in der Welt.", "", "<dark_gray>Fest gesperrt, auch per Konfiguration", "<dark_gray>nicht freischaltbar."));
    }

    private static String tierName(LuckyBlocks.Tier tier) {
        return switch (tier) {
            default -> throw new MatchException(null, null);
            case LuckyBlocks.Tier.NORMAL -> "Normal";
            case LuckyBlocks.Tier.SUPER -> "Super";
            case LuckyBlocks.Tier.SUBSCRIBE -> "Subscribe";
        };
    }
}

