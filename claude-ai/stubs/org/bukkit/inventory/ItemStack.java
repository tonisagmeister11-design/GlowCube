// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.inventory;
public class ItemStack implements Cloneable {
    public ItemStack(org.bukkit.Material m, int amount) {}
    public org.bukkit.Material getType() { return null; }
    public int getAmount() { return 0; }
    public void setAmount(int a) {}
    public ItemStack clone() { return null; }
    public int getMaxStackSize() { return 0; }
    public boolean isSimilar(ItemStack o) { return false; }
}
