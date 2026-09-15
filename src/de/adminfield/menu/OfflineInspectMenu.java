package de.adminfield.menu;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.ban.BanChest;
import de.adminfield.offline.KnownPlayers;
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
        OfflineStore store = OfflineStore.instance();
        if (store != null && !store.hasSnapshot(this.target)) {
            return Ui.mm("<dark_gray>▏ <gold><bold>Aufträge</bold></gold> <dark_gray>· " + this.name);
        }
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

        if (!store.hasSnapshot(this.target)) {
            // Von ihm ist noch nichts gesichert - dann eben Auftraege statt Inventar.
            this.drawOrders(store);
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

    /**
     * Was man ohne Abbild trotzdem tun kann.
     *
     * <p>Ansehen geht nicht - was jemand bei sich hat, weiss nur der Server, und der rueckt es
     * erst heraus, wenn der Spieler da ist. Leeren geht aber sehr wohl: dafuer muss man nicht
     * wissen, was drin ist. Beim naechsten Einloggen wird es ausgefuehrt, danach steht er mit
     * Abbild in der Liste und alles Weitere geht wie gewohnt.
     */
    private void drawOrders(OfflineStore store) {
        boolean clearInventory = store.queued(this.target, false);
        boolean clearEnder = store.queued(this.target, true);
        BanChest ban = BanChest.instance();
        boolean freeing = ban != null && ban.released(this.target);

        long seen = KnownPlayers.lastSeen(this.target);
        this.set(13, Ui.icon(Material.PAPER, "<gold><bold>" + this.name + "</bold>",
                List.of("<gray>Zuletzt gesehen: <white>"
                                + (seen > 0L ? Ui.ago(System.currentTimeMillis() - seen) : "unbekannt"),
                        "<gray>Ein Abbild gibt es von ihm noch nicht.",
                        "",
                        "<gray>Sein Inventar ansehen geht deshalb nicht:",
                        "<gray>was jemand bei sich hat, rückt der Server",
                        "<gray>nur für Anwesende heraus.",
                        "",
                        "<gray>Sobald er wieder einloggt, wird es gesichert",
                        "<gray>und steht hier – bis dahin helfen die",
                        "<gray>Aufträge hier unten.")));

        this.set(29, Ui.toggle(clearInventory, "<red>Inventar beim nächsten Einloggen leeren",
                List.of("<gray>Nimmt ihm <white>alles<gray> ab, sobald er kommt.")),
                event -> this.toggleOrder(store, !clearInventory, clearEnder));

        this.set(31, Ui.toggle(freeing, "<green>Nur die Bannkiste abnehmen",
                List.of("<gray>Sucht beim nächsten Einloggen nur die",
                        "<gray>Bannkiste heraus – aus Inventar <white>und",
                        "<gray>Enderkiste. Alles andere bleibt ihm.",
                        "",
                        "<gray>Der schonende Weg, um jemanden",
                        "<gray>wieder hereinzulassen.")),
                event -> this.toggleBan(ban, freeing));

        this.set(33, Ui.toggle(clearEnder, "<dark_purple>Enderkiste beim nächsten Einloggen leeren",
                List.of("<gray>Leert seine ganze Enderkiste.")),
                event -> this.toggleOrder(store, clearInventory, !clearEnder));

        this.backButton(45);
        this.closeButton(53);
        this.fillEmpty();
    }

    /** Nur die Bannkiste - der schonende Weg, im Gegensatz zum Leeren daneben. */
    private void toggleBan(BanChest ban, boolean freeing) {
        if (!this.mayEdit()) {
            return;
        }
        if (ban == null) {
            this.plugin.send((CommandSender) this.viewer, "<red>Die Bannkiste läuft gerade nicht.");
            return;
        }
        if (freeing) {
            ban.cancelRelease(this.target, this.name);
            this.plugin.send((CommandSender) this.viewer,
                    "<gray>Freigabe für <white>" + this.name + "<gray> zurückgenommen.");
        } else {
            this.plugin.send((CommandSender) this.viewer, ban.release(this.target, this.name));
            this.plugin.log().add(ActivityLog.Level.WARN, this.viewer.getName()
                    + " gab " + this.name + " von der Bannkiste frei",
                    this.viewer.getLocation(), this.target);
        }
        this.redraw();
    }

    private void toggleOrder(OfflineStore store, boolean inventory, boolean ender) {
        if (!this.mayEdit()) {
            return;
        }
        store.queueClear(this.target, this.name, inventory, ender);
        this.plugin.log().add(ActivityLog.Level.WARN, this.viewer.getName()
                + (inventory || ender ? " merkte vor: " : " nahm zurück: ")
                + "Inventar leeren bei " + this.name + " (ohne Abbild)",
                this.viewer.getLocation(), this.target);
        this.redraw();
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

    /**
     * Nur wo ein Abbild vorliegt, ist das Fenster ein Inventar.
     *
     * <p>Ohne Abbild zeigt es Auftragsknoepfe - die duerfen weder herausgenommen noch als sein
     * Inventar gespeichert werden.
     */
    private boolean editable() {
        OfflineStore store = OfflineStore.instance();
        return store != null && store.hasSnapshot(this.target);
    }

    private boolean mayEdit() {
        return this.viewer != null && this.viewer.hasPermission("adminfield.inventory.edit");
    }

    @Override
    public boolean allowClick(InventoryClickEvent event) {
        if (!this.mayEdit() || !this.editable() || Bukkit.getPlayer(this.target) != null) {
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
        if (!this.mayEdit() || !this.editable() || Bukkit.getPlayer(this.target) != null) {
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
        if (store == null || inventory == null || !this.mayEdit() || !this.editable()) {
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
        if (store == null || inventory == null || !this.mayEdit() || !this.editable()) {
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
