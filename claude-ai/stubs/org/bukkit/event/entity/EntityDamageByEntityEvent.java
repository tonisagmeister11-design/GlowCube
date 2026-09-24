// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.event.entity;
public abstract class EntityDamageByEntityEvent extends EntityDamageEvent {
    public org.bukkit.entity.Entity getDamager() { return null; }
}
