package org.bukkit.inventory;
public interface PlayerInventory { ItemStack[] getContents(); void setContents(ItemStack[] c); int getHeldItemSlot(); void setHeldItemSlot(int s); }
