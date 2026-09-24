// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.event.player;
public abstract class PlayerInteractEntityEvent extends org.bukkit.event.Event {
    public org.bukkit.entity.Player getPlayer() { return null; }
    public org.bukkit.entity.Entity getRightClicked() { return null; }
    public org.bukkit.inventory.EquipmentSlot getHand() { return null; }
    public void setCancelled(boolean c) {}
}
