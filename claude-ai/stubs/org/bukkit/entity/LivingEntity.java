// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.entity;
public interface LivingEntity extends Entity {
    org.bukkit.inventory.EntityEquipment getEquipment();
    void swingMainHand();
    void damage(double amount, Entity source);
    double getHealth();
    org.bukkit.Location getEyeLocation();
    void setAI(boolean ai);
    void setCollidable(boolean c);
    void setRemoveWhenFarAway(boolean r);
    void setCanPickupItems(boolean p);
    boolean hasLineOfSight(Entity other);
    org.bukkit.block.Block getTargetBlockExact(int maxDistance);
}
