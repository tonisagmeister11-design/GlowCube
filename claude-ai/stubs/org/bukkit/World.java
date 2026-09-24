// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit;
public interface World {
    org.bukkit.block.Block getBlockAt(int x, int y, int z);
    org.bukkit.block.Block getBlockAt(Location l);
    java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(Location l, double x, double y, double z);
    org.bukkit.entity.Entity spawnEntity(Location l, org.bukkit.entity.EntityType t);
    org.bukkit.entity.Item dropItemNaturally(Location l, org.bukkit.inventory.ItemStack i);
    void playSound(Location l, Sound s, float v, float p);
    void spawnParticle(Particle p, Location l, int count, double dx, double dy, double dz, double extra);
    <T> void playEffect(Location l, Effect e, T data);
    long getTime();
    String getName();
    java.util.UUID getUID();
    boolean isChunkLoaded(int x, int z);
    int getMinHeight();
    int getMaxHeight();
    java.util.List<org.bukkit.entity.Player> getPlayers();
    boolean hasStorm();
    Location getSpawnLocation();
}
