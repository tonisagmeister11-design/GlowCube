// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.entity;
public interface Entity {
    org.bukkit.Location getLocation();
    boolean teleport(org.bukkit.Location l);
    org.bukkit.World getWorld();
    void remove();
    boolean isValid();
    boolean isDead();
    java.util.UUID getUniqueId();
    EntityType getType();
    void setGravity(boolean g);
    void setVelocity(org.bukkit.util.Vector v);
    org.bukkit.util.Vector getVelocity();
    void setRotation(float yaw, float pitch);
    void setCustomName(String name);
    void setCustomNameVisible(boolean v);
    void setSilent(boolean s);
    void setInvulnerable(boolean i);
    void setPersistent(boolean p);
    java.util.List<Entity> getNearbyEntities(double x, double y, double z);
    String getName();
    boolean isOnGround();
    double getHeight();
    void setFireTicks(int t);
    boolean addScoreboardTag(String tag);
    java.util.Set<String> getScoreboardTags();
    boolean isInWater();
}
