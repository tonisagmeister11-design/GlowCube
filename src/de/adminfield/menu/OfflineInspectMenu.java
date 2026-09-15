package de.adminfield.menu;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.offline.OfflineStore;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Inventar oder Enderkiste eines Spielers bearbeiten, der gerade nicht da ist.
 *
 * <p>Bedient sich wie das Fenster fuer Online-Spieler. Gearbeitet wird aber auf dem Abbild,
 * das beim Verlassen gesichert wurde - beim naechsten Einloggen landet alles im echten
 * Inventar.
 */
public final class OfflineInspectMenu extends Menu {

    private final UUID target;
    private final String name;
    private final boolean enderChest;

    public OfflineInspectMenu(AdminFieldPlugin plugin, Menu parent, UUID target, String name,
            boolean enderChest) {
        super(plugin, parent);
        this.target = target;
        this.name = name;
        this.enderChest = enderChest;
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gold><bold>" + (this.enderChest ? "Enderkiste" : "Inventar")
                + "</bold></gold> <dark_gray>· " + this.name + " <dark_gray>(offline)");
    }

    @Override
    protected int rows() {
        return 6;
    }

    private int slots() {
        return this.enderChest ? OfflineStore.ENDER_SLOTS : OfflineStore.INVENTORY_SLOTS;
    }

    @Override
    protected void draw() {
        OfflineStore store = OfflineStore.instance();
        if (store == null || !this.mayEdit()) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Nicht verfügbar",
                    List.of(store == null ? "<gray>Die Offline-Verwaltung läuft nicht."
                                          : "<gray>Dir fehlt das Recht zum Bearbeiten.")));
            this.backButton(45);
            this.closeButton(53);
            this.fillEmpty();
            return;
        }
        if (Bukkit.getPlayer(this.target) != null) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>" + this.name + " ist wieder online",
                    List.of("<gray>Bearbeite ihn über die normale Spielerliste –",
                            "<gray>dort greifen Änderungen sofort.")));
            this.backButton(45);
            this.closeButton(53);
            this.fillEmpty();
            return;
        }

        ItemStack[] items = this.enderChest ? store.ender(this.target) : store.inventory(this.target);
        for (int slot = 0; slot < items.length && slot < this.slots(); slot++) {
            if (items[slot] != null) {
                this.set(slot, items[slot].clone());
            }
        }
        this.controls();
        this.fillLocked();
    }

    private void controls() {
        this.backButton(45);
        this.set(47, Ui.icon(this.enderChest ? Material.CHEST : Material.ENDER_CHEST,
                this.enderChest ? "<gold>Zum Inventar" : "<dark_purple>Zur Enderkiste",
                List.of("<gray>Zwischen beidem umschalten.")),
                event -> new OfflineInspectMenu(this.plugin, this.parent, this.target, this.name,
                        !this.enderChest).open(this.viewer));
        this.set(49, Ui.glowing(Material.WRITABLE_BOOK, "<red><bold>Bearbeiten: an</bold>",
                List.of("<gray>Items herausnehmen, hineinlegen",
                        "<gray>und tauschen wie gewohnt.",
                        "",
                        "<gray>Wirksam wird es, sobald <white>" + this.name,
                        "<gray>das nächste Mal einloggt.",
                        "<dark_gray>Er bekommt keine Meldung.")));
        this.set(51, Ui.icon(Material.HOPPER, "<red>Alles einsammeln",
                List.of("<gray>Nimmt " + (this.enderChest ? "die Enderkiste" : "das Inventar"),
                        "<gray>von <white>" + this.name + "<gray> an dich.",
                        "<dark_gray>Was nicht passt, fällt vor deine Füße.",
                        "",
                        "<red>➤ Klicken")),
                event -> this.takeEverything());
        this.closeButton(53);
    }

    private void fillLocked() {
        Inventory inventory = this.getInventory();
        ItemStack filler = Ui.icon(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>");
        for (int slot = this.slots(); slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private boolean isMapped(int slot) {
        return slot >= 0 && slot < this.slots();
    }

    private boolean mayEdit() {
        return this.viewer != null && this.viewer.hasPermission("adminfield.inventory.edit");
    }

    @Override
    public boolean allowClick(InventoryClickEvent event) {
        if (!this.mayEdit() || Bukkit.getPlayer(this.target) != null) {
            return false;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return false;
        }
        if (!(clicked.getHolder() instanceof Menu)) {
            this.scheduleSave();
            return true;
        }
        if (this.isMapped(event.getRawSlot())) {
            this.scheduleSave();
            return true;
        }
        return false;
    }

    @Override
    public boolean allowDrag(InventoryDragEvent event) {
        if (!this.mayEdit() || Bukkit.getPlayer(this.target) != null) {
            return false;
        }
        int top = event.getView().getTopInventory().getSize();
        for (Object raw : event.getRawSlots()) {
            int slot = (Integer) raw;
            if (slot < top && !this.isMapped(slot)) {
                return false;
            }
        }
        this.scheduleSave();
        return true;
    }

    @Override
    public void closed() {
        this.store();
    }

    private void scheduleSave() {
        Bukkit.getScheduler().runTask((Plugin) this.plugin, this::store);
    }

    /** Schreibt den Fensterinhalt ins Abbild zurueck. */
    private void store() {
        OfflineStore store = OfflineStore.instance();
        Inventory inventory = this.getInventory();
        if (store == null || inventory == null || !this.mayEdit()) {
            return;
        }
        if (Bukkit.getPlayer(this.target) != null) {
            return;
        }
        ItemStack[] items = new ItemStack[this.slots()];
        for (int slot = 0; slot < items.length; slot++) {
            items[slot] = inventory.getItem(slot);
        }
        store.write(this.target, this.enderChest ? null : items, this.enderChest ? items : null);
    }

    private void takeEverything() {
        OfflineStore store = OfflineStore.instance();
        Inventory inventory = this.getInventory();
        if (store == null || inventory == null || !this.mayEdit()) {
            return;
        }
        int taken = 0;
        for (int slot = 0; slot < this.slots(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            this.giveToViewer(item);
            inventory.setItem(slot, null);
            taken++;
        }
        this.store();
        this.plugin.send((CommandSender) this.viewer, "<gray>" + taken
                + " Gegenstände von <white>" + this.name + "<gray> übernommen.");
        this.plugin.log().add(ActivityLog.Level.WARN, this.viewer.getName() + " nahm " + taken
                + " Gegenstände aus " + (this.enderChest ? "der Enderkiste" : "dem Inventar")
                + " von " + this.name + " (offline)", this.viewer.getLocation(), this.target);
        this.redraw();
    }

    private void giveToViewer(ItemStack item) {
        for (ItemStack rest : this.viewer.getInventory().addItem(new ItemStack[]{item.clone()}).values()) {
            this.viewer.getWorld().dropItemNaturally(this.viewer.getLocation(), rest);
        }
    }
}
