// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.block;
public interface Block {
    org.bukkit.Material getType();
    int getX();
    int getY();
    int getZ();
    org.bukkit.World getWorld();
    org.bukkit.Location getLocation();
    Block getRelative(int x, int y, int z);
    boolean isPassable();
    boolean isLiquid();
    boolean isEmpty();
    byte getLightLevel();
    void setType(org.bukkit.Material m);
    void setType(org.bukkit.Material m, boolean physics);
    org.bukkit.block.data.BlockData getBlockData();
    void setBlockData(org.bukkit.block.data.BlockData d);
    void setBlockData(org.bukkit.block.data.BlockData d, boolean physics);
    java.util.Collection<org.bukkit.inventory.ItemStack> getDrops(org.bukkit.inventory.ItemStack tool);
    BlockState getState();
}
