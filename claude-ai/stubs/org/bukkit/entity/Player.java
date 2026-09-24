// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.entity;
public interface Player extends HumanEntity, org.bukkit.command.CommandSender {
    String getName();
    org.bukkit.inventory.PlayerInventory getInventory();
    org.bukkit.inventory.InventoryView openInventory(org.bukkit.inventory.Inventory inv);
    int getFoodLevel();
    boolean isOnline();
    void playSound(org.bukkit.Location l, org.bukkit.Sound s, float v, float p);
    void sendBlockDamage(org.bukkit.Location l, float progress, int sourceId);
}
