// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.event.entity;
public abstract class EntityDamageEvent extends org.bukkit.event.Event {
    public org.bukkit.entity.Entity getEntity() { return null; }
    public void setCancelled(boolean c) {}
}
