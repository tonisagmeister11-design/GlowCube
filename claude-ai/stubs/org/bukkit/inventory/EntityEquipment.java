// Nur zum Uebersetzen - zur Laufzeit liefert Paper die echten Klassen.
package org.bukkit.inventory;
public interface EntityEquipment {
    void setItemInMainHand(ItemStack i);
    ItemStack getItemInMainHand();
    void setHelmet(ItemStack i);
    void setChestplate(ItemStack i);
    void setLeggings(ItemStack i);
    void setBoots(ItemStack i);
}
