// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.entity;
public interface Mob extends LivingEntity { LivingEntity getTarget(); void setTarget(LivingEntity t); }
