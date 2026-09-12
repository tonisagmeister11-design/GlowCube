/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import java.util.Iterator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

public final class InspectMenu
extends Menu {
    private static final int LAST_INVENTORY_SLOT = 40;
    private static final int ENDER_SLOTS = 27;
    private final Player target;
    private final boolean enderChest;
    private final boolean edit;

    public InspectMenu(AdminFieldPlugin adminFieldPlugin, Menu menu, Player player, boolean bl) {
        this(adminFieldPlugin, menu, player, bl, false);
    }

    public InspectMenu(AdminFieldPlugin adminFieldPlugin, Menu menu, Player player, boolean bl, boolean bl2) {
        super(adminFieldPlugin, menu);
        this.target = player;
        this.enderChest = bl;
        this.edit = bl2;
    }

    @Override
    protected Component title() {
        String string = this.enderChest ? "Enderkiste" : "Inventar";
        return this.edit ? Ui.mm("<dark_gray>▏ <red><bold>" + string + " bearbeiten</bold></red> <dark_gray>· " + this.target.getName()) : Ui.mm("<dark_gray>▏ <gold>" + string + "</gold> <dark_gray>· " + this.target.getName());
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    public boolean live() {
        return !this.edit;
    }

    @Override
    protected void draw() {
        if (!this.target.isOnline()) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Spieler ist offline", List.of("<gray>" + this.target.getName() + " hat den Server verlassen.")));
            this.backButton(45);
            this.closeButton(53);
            this.fillEmpty();
            return;
        }
        if (this.enderChest) {
            this.drawEnderChest();
        } else {
            this.drawInventory();
        }
        this.controls();
        this.fillLocked();
    }

    private void drawEnderChest() {
        ItemStack[] itemStackArray = this.target.getEnderChest().getContents();
        for (int i = 0; i < 27 && i < itemStackArray.length; ++i) {
            if (itemStackArray[i] == null) continue;
            this.set(i, itemStackArray[i].clone());
        }
    }

    private void drawInventory() {
        ItemStack itemStack;
        int n;
        PlayerInventory playerInventory = this.target.getInventory();
        for (n = 0; n < 27; ++n) {
            itemStack = playerInventory.getItem(9 + n);
            if (itemStack == null) continue;
            this.set(n, itemStack.clone());
        }
        for (n = 0; n < 9; ++n) {
            itemStack = playerInventory.getItem(n);
            if (itemStack == null) continue;
            this.set(27 + n, itemStack.clone());
        }
        this.armorSlot(36, playerInventory.getHelmet(), Material.LEATHER_HELMET, "Kopf");
        this.armorSlot(37, playerInventory.getChestplate(), Material.LEATHER_CHESTPLATE, "Brust");
        this.armorSlot(38, playerInventory.getLeggings(), Material.LEATHER_LEGGINGS, "Beine");
        this.armorSlot(39, playerInventory.getBoots(), Material.LEATHER_BOOTS, "Füße");
        this.armorSlot(40, playerInventory.getItemInOffHand(), Material.SHIELD, "Zweite Hand");
    }

    private void armorSlot(int n, ItemStack itemStack, Material material, String string) {
        if (itemStack != null && !itemStack.getType().isAir()) {
            this.set(n, itemStack.clone());
        } else if (!this.edit) {
            this.set(n, Ui.icon(material, "<dark_gray>" + string + ": leer"));
        }
    }

    private void controls() {
        PlayerInventory playerInventory = this.target.getInventory();
        int n = 0;
        for (ItemStack itemStack : playerInventory.getStorageContents()) {
            if (itemStack == null || itemStack.getType().isAir()) continue;
            ++n;
        }
        this.backButton(45);
        boolean bl = this.viewer.hasPermission("adminfield.inventory.edit");
        if (bl) {
            this.set(47, this.edit ? Ui.glowing(Material.WRITABLE_BOOK, "<red><bold>Bearbeiten: an</bold>", List.of("<gray>Du kannst Items herausnehmen,", "<gray>hineinlegen und tauschen.", "<gray>Änderungen greifen sofort.", "", "<dark_gray>Der Spieler bekommt keine Meldung.", "<yellow>➤ Klicken zum Ausschalten")) : Ui.icon(Material.BOOK, "<gray>Bearbeiten: aus", List.of("<gray>Zurzeit nur ansehen.", "", "<gray>Eingeschaltet kannst du Items", "<gray>herausnehmen und hineinlegen.", "<yellow>➤ Klicken zum Einschalten")), inventoryClickEvent -> new InspectMenu(this.plugin, this.parent, this.target, this.enderChest, !this.edit).open(this.viewer));
        }
        this.set(49, Ui.icon(Material.SPYGLASS, this.edit ? "<red>Bearbeitungsmodus" : "<aqua>Nur ansehen", this.edit ? List.of("<gray>Belegte Felder: <white>" + n + "<dark_gray>/<white>36", "", "<gray>Alles, was du hier änderst, landet", "<gray>direkt im Inventar von <white>" + this.target.getName() + "<gray>.", "<dark_gray>Entnahmen werden im Verlauf notiert.") : List.of("<gray>Belegte Felder: <white>" + n + "<dark_gray>/<white>36", "<gray>Level: <white>" + this.target.getLevel(), "", "<gray>Aktualisiert sich jede Sekunde.")));
        if (this.edit && bl) {
            this.set(51, Ui.icon(Material.HOPPER, "<red>Alles einsammeln", List.of("<gray>Nimmt " + (this.enderChest ? "die Enderkiste" : "das komplette Inventar"), "<gray>von <white>" + this.target.getName() + "<gray> an dich.", "<dark_gray>Was nicht passt, fällt vor deine Füße.", "", "<red>➤ Klicken - passiert sofort")), inventoryClickEvent -> this.takeEverything());
        }
        this.closeButton(53);
    }

    private void fillLocked() {
        Inventory inventory = this.getInventory();
        ItemStack itemStack = Ui.icon(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>");
        for (int i = this.firstLockedSlot(); i < inventory.getSize(); ++i) {
            if (inventory.getItem(i) != null) continue;
            inventory.setItem(i, itemStack);
        }
    }

    private int firstLockedSlot() {
        return this.enderChest ? 27 : 41;
    }

    private boolean isMapped(int n) {
        if (n < 0) {
            return false;
        }
        return this.enderChest ? n < 27 : n <= 40;
    }

    @Override
    public boolean allowClick(InventoryClickEvent inventoryClickEvent) {
        if (!(this.edit && this.target.isOnline() && this.viewer.hasPermission("adminfield.inventory.edit"))) {
            return false;
        }
        Inventory inventory = inventoryClickEvent.getClickedInventory();
        if (inventory == null) {
            return false;
        }
        if (!(inventory.getHolder() instanceof Menu)) {
            this.scheduleSync();
            return true;
        }
        if (this.isMapped(inventoryClickEvent.getRawSlot())) {
            this.scheduleSync();
            return true;
        }
        return false;
    }

    @Override
    public boolean allowDrag(InventoryDragEvent inventoryDragEvent) {
        if (!(this.edit && this.target.isOnline() && this.viewer.hasPermission("adminfield.inventory.edit"))) {
            return false;
        }
        int n = inventoryDragEvent.getView().getTopInventory().getSize();
        Iterator iterator = inventoryDragEvent.getRawSlots().iterator();
        while (iterator.hasNext()) {
            int n2 = (Integer)iterator.next();
            if (n2 >= n || this.isMapped(n2)) continue;
            return false;
        }
        this.scheduleSync();
        return true;
    }

    @Override
    public void closed() {
        if (this.edit) {
            this.syncToTarget();
        }
    }

    private void scheduleSync() {
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.syncToTarget());
    }

    private void syncToTarget() {
        int n;
        Inventory inventory = this.getInventory();
        if (inventory == null || !this.target.isOnline()) {
            return;
        }
        if (this.enderChest) {
            for (int i = 0; i < 27; ++i) {
                this.target.getEnderChest().setItem(i, inventory.getItem(i));
            }
            this.target.updateInventory();
            return;
        }
        PlayerInventory playerInventory = this.target.getInventory();
        for (n = 0; n < 27; ++n) {
            playerInventory.setItem(9 + n, inventory.getItem(n));
        }
        for (n = 0; n < 9; ++n) {
            playerInventory.setItem(n, inventory.getItem(27 + n));
        }
        playerInventory.setHelmet(inventory.getItem(36));
        playerInventory.setChestplate(inventory.getItem(37));
        playerInventory.setLeggings(inventory.getItem(38));
        playerInventory.setBoots(inventory.getItem(39));
        ItemStack itemStack = inventory.getItem(40);
        playerInventory.setItemInOffHand(itemStack == null ? new ItemStack(Material.AIR) : itemStack);
        this.target.updateInventory();
    }

    private void takeEverything() {
        int n = 0;
        if (this.enderChest) {
            Inventory inventory = this.target.getEnderChest();
            for (int i = 0; i < inventory.getSize(); ++i) {
                ItemStack itemStack = inventory.getItem(i);
                if (itemStack == null || itemStack.getType().isAir()) continue;
                this.giveToViewer(itemStack);
                inventory.setItem(i, null);
                ++n;
            }
        } else {
            PlayerInventory playerInventory = this.target.getInventory();
            for (int i = 0; i < 36; ++i) {
                ItemStack itemStack = playerInventory.getItem(i);
                if (itemStack == null || itemStack.getType().isAir()) continue;
                this.giveToViewer(itemStack);
                playerInventory.setItem(i, null);
                ++n;
            }
            for (ItemStack itemStack : playerInventory.getArmorContents()) {
                if (itemStack == null || itemStack.getType().isAir()) continue;
                this.giveToViewer(itemStack);
                ++n;
            }
            playerInventory.setArmorContents(null);
            ItemStack itemStack = playerInventory.getItemInOffHand();
            if (!itemStack.getType().isAir()) {
                this.giveToViewer(itemStack);
                playerInventory.setItemInOffHand(new ItemStack(Material.AIR));
                ++n;
            }
        }
        this.target.updateInventory();
        this.plugin.send((CommandSender)this.viewer, "<gray>" + n + " Gegenstände von <white>" + this.target.getName() + "<gray> übernommen.");
        this.plugin.log().add(ActivityLog.Level.WARN, this.viewer.getName() + " nahm " + n + " Gegenstände aus " + (this.enderChest ? "der Enderkiste" : "dem Inventar") + " von " + this.target.getName(), this.target.getLocation(), this.target.getUniqueId());
        this.redraw();
    }

    private void giveToViewer(ItemStack itemStack) {
        for (ItemStack itemStack2 : this.viewer.getInventory().addItem(new ItemStack[]{itemStack.clone()}).values()) {
            this.viewer.getWorld().dropItemNaturally(this.viewer.getLocation(), itemStack2);
        }
    }
}

