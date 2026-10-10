package org.bukkit.inventory;
public interface PlayerInventory extends Inventory { ItemStack[] getContents(); void setContents(ItemStack[] c); int getHeldItemSlot(); void setHeldItemSlot(int s); }
