// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.event.block;
public class BlockBreakEvent extends org.bukkit.event.Event implements org.bukkit.event.Cancellable {
    public BlockBreakEvent(org.bukkit.block.Block b, org.bukkit.entity.Player p) {}
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean c) {}
}
