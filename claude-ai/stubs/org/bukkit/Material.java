// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit;
public abstract class Material {
    public static Material AIR;
    public static Material matchMaterial(String name) { return null; }
    public static Material getMaterial(String name) { return null; }
    public String name() { return null; }
    public boolean isSolid() { return false; }
    public boolean isAir() { return false; }
    public boolean isBlock() { return false; }
    public boolean isItem() { return false; }
    public boolean isEdible() { return false; }
    public float getHardness() { return 0; }
    public int getMaxStackSize() { return 0; }
    public org.bukkit.block.data.BlockData createBlockData() { return null; }
}
