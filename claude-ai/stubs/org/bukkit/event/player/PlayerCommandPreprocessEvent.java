// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.event.player;
public abstract class PlayerCommandPreprocessEvent extends org.bukkit.event.Event {
    public org.bukkit.entity.Player getPlayer() { return null; }
    public String getMessage() { return null; }
    public void setCancelled(boolean c) {}
}
