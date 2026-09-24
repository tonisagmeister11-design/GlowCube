// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.inventory;
public interface Inventory {
    java.util.HashMap<Integer, ItemStack> addItem(ItemStack... items);
    java.util.HashMap<Integer, ItemStack> removeItem(ItemStack... items);
    ItemStack[] getContents();
    void setContents(ItemStack[] items);
    ItemStack getItem(int slot);
    void setItem(int slot, ItemStack item);
    int getSize();
    void clear();
    int firstEmpty();
    InventoryHolder getHolder();
}
