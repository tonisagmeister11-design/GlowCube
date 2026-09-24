// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.event.player;
public class PlayerCommandPreprocessEvent extends PlayerEvent {
    public String getMessage() { return null; }
    public void setCancelled(boolean cancel) {}
}
