/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public abstract class Menu
implements InventoryHolder {
    protected final AdminFieldPlugin plugin;
    protected final Menu parent;
    protected Player viewer;
    protected int page;
    private Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<Integer, Consumer<InventoryClickEvent>>();

    protected Menu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        this.plugin = adminFieldPlugin;
        this.parent = menu;
    }

    protected abstract Component title();

    protected abstract int rows();

    protected abstract void draw();

    public boolean live() {
        return false;
    }

    public boolean allowClick(InventoryClickEvent inventoryClickEvent) {
        return false;
    }

    public boolean allowDrag(InventoryDragEvent inventoryDragEvent) {
        return false;
    }

    public void closed() {
    }

    public void open(Player player) {
        this.viewer = player;
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)(this.rows() * 9), (Component)this.title());
        this.redraw();
        player.openInventory(this.inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.5f, 1.6f);
    }

    public void redraw() {
        if (this.inventory == null || this.viewer == null) {
            return;
        }
        this.actions.clear();
        this.inventory.clear();
        this.draw();
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    protected void set(int n, ItemStack itemStack) {
        this.set(n, itemStack, null);
    }

    protected void set(int n, ItemStack itemStack, Consumer<InventoryClickEvent> consumer) {
        if (n < 0 || n >= this.inventory.getSize()) {
            return;
        }
        this.inventory.setItem(n, itemStack);
        if (consumer != null) {
            this.actions.put(n, consumer);
        }
    }

    protected void fillEmpty() {
        ItemStack itemStack = Ui.icon(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>");
        for (int i = 0; i < this.inventory.getSize(); ++i) {
            if (this.inventory.getItem(i) != null) continue;
            this.inventory.setItem(i, itemStack);
        }
    }

    protected void divider(int n) {
        ItemStack itemStack = Ui.icon(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>");
        for (int i = 0; i < 9; ++i) {
            this.set(n * 9 + i, itemStack);
        }
    }

    protected void backButton(int n) {
        if (this.parent == null) {
            this.closeButton(n);
            return;
        }
        this.set(n, Ui.icon(Material.ARROW, "<yellow>Zurück", List.of("<gray>Eine Ebene nach oben")), inventoryClickEvent -> this.parent.open(this.viewer));
    }

    protected void closeButton(int n) {
        this.set(n, Ui.icon(Material.BARRIER, "<red>Schließen"), inventoryClickEvent -> this.viewer.closeInventory());
    }

    protected int pager(int n, int n2, int n3, int n4) {
        int n5 = Math.max(1, (int)Math.ceil((double)n / (double)n2));
        this.page = Math.max(0, Math.min(this.page, n5 - 1));
        if (this.page > 0) {
            this.set(n3, Ui.icon(Material.SPECTRAL_ARROW, "<yellow>Vorherige Seite", List.of("<gray>Seite <white>" + this.page + "<gray> von <white>" + n5)), inventoryClickEvent -> {
                --this.page;
                this.redraw();
            });
        }
        if (this.page < n5 - 1) {
            this.set(n4, Ui.icon(Material.SPECTRAL_ARROW, "<yellow>Nächste Seite", List.of("<gray>Seite <white>" + (this.page + 2) + "<gray> von <white>" + n5)), inventoryClickEvent -> {
                ++this.page;
                this.redraw();
            });
        }
        return n5;
    }

    public void click(InventoryClickEvent inventoryClickEvent) {
        Consumer<InventoryClickEvent> consumer = this.actions.get(inventoryClickEvent.getRawSlot());
        if (consumer == null) {
            return;
        }
        if (this.viewer != null) {
            this.viewer.playSound(this.viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.7f);
        }
        consumer.accept(inventoryClickEvent);
    }

    protected void refreshLater() {
        Bukkit.getScheduler().runTask((Plugin)this.plugin, this::redraw);
    }
}

